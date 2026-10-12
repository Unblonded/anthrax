package unblonded.anthrax.modules;

import net.fabricmc.loader.api.FabricLoader;
import unblonded.anthrax.gui.GuiPreferences;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Loads and saves module state, module settings, and GUI preferences. */
public final class ModuleConfig {
    private static final int CONFIG_VERSION = 3;
    /** Reserved pseudo-setting name under which a module's keybind is stored. */
    private static final String BIND = "#bind";
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("anthrax.properties");
    private static final Path PROFILES = FabricLoader.getInstance().getConfigDir().resolve("anthrax-profiles");
    private static Map<String, String> observed = Map.of();
    private static long saveAt;
    private static boolean loaded;

    private ModuleConfig() {}

    public static void load() {
        Properties properties = read(FILE);
        for (GuiModule module : ModuleRegistry.all())
            for (Setting setting : module.settings()) setting.captureDefault();
        applyAll(properties);
        loaded = true;
        int configVersion = 0;
        try { configVersion = Integer.parseInt(properties.getProperty("anthrax.configVersion", "0")); }
        catch (NumberFormatException ignored) {}
        if (configVersion < CONFIG_VERSION) saveNow();
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (Exception exception) {
                System.err.println("Anthrax: could not read " + file.getFileName() + ": " + exception.getMessage());
            }
        }
        return properties;
    }

    // ------------------------------------------------------------------ profiles
    private static Path profile(String name) {
        String safe = name.trim().replaceAll("[^A-Za-z0-9_ -]", "_");
        return PROFILES.resolve((safe.isEmpty() ? "default" : safe) + ".properties");
    }

    /** Saves every module, setting, keybind and GUI preference as a named profile. */
    public static boolean saveProfile(String name) {
        saveNow();
        try {
            Files.createDirectories(PROFILES);
            Files.copy(FILE, profile(name), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            System.err.println("Anthrax: could not save profile: " + e.getMessage());
            return false;
        }
    }

    /** Applies a saved profile on top of the current state (missing entries keep their current values). */
    public static boolean loadProfile(String name) {
        Path file = profile(name);
        if (!Files.isRegularFile(file)) return false;
        applyAll(read(file));
        saveNow();
        return true;
    }

    public static boolean deleteProfile(String name) {
        try { return Files.deleteIfExists(profile(name)); }
        catch (Exception e) { return false; }
    }

    public static java.util.List<String> profiles() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!Files.isDirectory(PROFILES)) return out;
        try (var stream = Files.list(PROFILES)) {
            stream.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".properties"))
                    .map(n -> n.substring(0, n.length() - ".properties".length())).sorted().forEach(out::add);
        } catch (Exception ignored) {}
        return out;
    }

    private static void applyAll(Properties properties) {

        int configVersion = 0;
        try { configVersion = Integer.parseInt(properties.getProperty("anthrax.configVersion", "0")); }
        catch (NumberFormatException ignored) {}

        for (GuiModule module : ModuleRegistry.all()) {
            String bind = properties.getProperty(key(module.name, BIND));
            if (bind != null) {
                try { module.setKey(Integer.parseInt(bind)); } catch (NumberFormatException ignored) {}
            }
            for (Setting setting : module.settings()) {
                String value = properties.getProperty(key(module.name, setting.name));
                if (value != null) apply(setting, value);
            }
            // Enable last, so a module's onToggle already sees its configured settings.
            String enabled = properties.getProperty(key(module.name, "enabled"));
            if (enabled != null) module.setEnabled(Boolean.parseBoolean(enabled));
        }
        // The chart previously defaulted to menu-only, but the HUD version should
        // be visible in normal gameplay for existing installations as well.
        if (configVersion < 2) {
            for (GuiModule module : ModuleRegistry.all()) {
                if (!module.name.equals("FPS Chart")) continue;
                for (Setting setting : module.settings()) {
                    if (setting instanceof Setting.Bool bool && setting.name.equals("Show in game")
                            && properties.getProperty(key(module.name, setting.name)) == null) bool.set(true);
                }
            }
        }
        GuiPreferences.load(properties);
        observed = snapshot();
    }

    private static int tickCounter;

    /** Debounced change detection also catches runtime changes such as auto-disarming Primed. */
    public static void tick() {
        if (!loaded) return;
        if (saveAt == 0 && ++tickCounter % 5 != 0) return;
        Map<String, String> current = snapshot();
        if (!current.equals(observed)) {
            observed = current;
            saveAt = System.currentTimeMillis() + 400;
        }
        if (saveAt != 0 && System.currentTimeMillis() >= saveAt) saveNow();
    }

    public static void saveNow() {
        if (!loaded) return;
        Properties properties = new Properties();
        properties.setProperty("anthrax.configVersion", Integer.toString(CONFIG_VERSION));
        for (GuiModule module : ModuleRegistry.all()) {
            properties.setProperty(key(module.name, "enabled"), Boolean.toString(module.isEnabled()));
            properties.setProperty(key(module.name, BIND), Integer.toString(module.key()));
            for (Setting setting : module.settings()) {
                String value = serialize(setting);
                if (value != null) properties.setProperty(key(module.name, setting.name), value);
            }
        }
        GuiPreferences.save(properties);
        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Anthrax module settings and GUI preferences");
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicMoveUnsupported) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            observed = snapshot();
            saveAt = 0;
        } catch (Exception exception) {
            System.err.println("Anthrax: could not save settings: " + exception.getMessage());
        }
    }

    private static Map<String, String> snapshot() {
        Map<String, String> values = new LinkedHashMap<>();
        for (GuiModule module : ModuleRegistry.all()) {
            values.put(key(module.name, "enabled"), Boolean.toString(module.isEnabled()));
            values.put(key(module.name, BIND), Integer.toString(module.key()));
            for (Setting setting : module.settings()) {
                String value = serialize(setting);
                if (value != null) values.put(key(module.name, setting.name), value);
            }
        }
        GuiPreferences.save(values);
        return values;
    }

    /** Restores every setting of a module to the value it declared. */
    public static void resetModule(GuiModule module) {
        for (Setting setting : module.settings()) setting.reset();
    }

    private static String serialize(Setting setting) {
        if (setting instanceof Setting.Bool bool) return Boolean.toString(bool.get());
        if (setting instanceof Setting.Num number) return Double.toString(number.get());
        if (setting instanceof Setting.Mode mode) return mode.get();
        if (setting instanceof Setting.Text text) return text.get();
        return null; // Actions are buttons, not values.
    }

    private static void apply(Setting setting, String value) {
        try {
            if (setting instanceof Setting.Bool bool) bool.set(Boolean.parseBoolean(value));
            else if (setting instanceof Setting.Num number) {
                double parsed = Double.parseDouble(value);
                if (Double.isFinite(parsed)) number.set(parsed);
            } else if (setting instanceof Setting.Mode mode) {
                mode.set(value);
            } else if (setting instanceof Setting.Text text) text.set(value);
        } catch (RuntimeException exception) {
            System.err.println("Anthrax: ignored invalid setting '" + setting.name + "': " + exception.getMessage());
        }
    }

    private static String key(String module, String setting) {
        return "value." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString((module + "\u0000" + setting).getBytes(StandardCharsets.UTF_8));
    }
}
