package unblonded.anthrax.gui;

import java.util.Properties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistence bridge for the GUI's package-private visual preferences. */
public final class GuiPreferences {
    private static String lastCategory = "";
    private static boolean separateCategoryWindows;
    private static final Map<String, double[]> windowPositions = new LinkedHashMap<>();

    private GuiPreferences() {}

    public static void load(Properties properties) {
        try {
            int theme = Integer.parseInt(properties.getProperty("gui.theme", Integer.toString(Theme.idx)));
            if (theme >= 0 && theme < Theme.NAMES.length) Theme.idx = theme;
        } catch (NumberFormatException ignored) {}
        // The effects level is the source of truth; older configs only stored the flags it implies.
        boolean fancy = Boolean.parseBoolean(properties.getProperty("gui.fancy", "true"));
        boolean aa = Boolean.parseBoolean(properties.getProperty("gui.antialias", "false"));
        int fx = !fancy ? 0 : aa ? 2 : 1;
        try { fx = Integer.parseInt(properties.getProperty("gui.fx", Integer.toString(fx))); } catch (NumberFormatException ignored) {}
        Draw.setFx(fx);
        lastCategory = properties.getProperty("gui.lastCategory", "");
        separateCategoryWindows = Boolean.parseBoolean(properties.getProperty("gui.separateCategoryWindows", "false"));
        windowPositions.clear();
        String prefix = "gui.window.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            String encoded = key.substring(prefix.length());
            try {
                String name = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
                String[] parts = properties.getProperty(key, "").split(",", 2);
                if (parts.length != 2) continue;
                double x = Double.parseDouble(parts[0]), y = Double.parseDouble(parts[1]);
                if (Double.isFinite(x) && Double.isFinite(y)) windowPositions.put(name, new double[]{clamp(x), clamp(y)});
            } catch (RuntimeException ignored) {}
        }
    }

    public static void save(Properties properties) {
        properties.setProperty("gui.theme", Integer.toString(Theme.idx));
        properties.setProperty("gui.fx", Integer.toString(Draw.FX));
        properties.setProperty("gui.fancy", Boolean.toString(Draw.FANCY));
        properties.setProperty("gui.antialias", Boolean.toString(Draw.AA));
        properties.setProperty("gui.lastCategory", lastCategory);
        properties.setProperty("gui.separateCategoryWindows", Boolean.toString(separateCategoryWindows));
        windowPositions.forEach((name, position) -> properties.setProperty(windowKey(name), position[0] + "," + position[1]));
    }

    public static void save(java.util.Map<String, String> values) {
        values.put("gui.theme", Integer.toString(Theme.idx));
        values.put("gui.fx", Integer.toString(Draw.FX));
        values.put("gui.fancy", Boolean.toString(Draw.FANCY));
        values.put("gui.antialias", Boolean.toString(Draw.AA));
        values.put("gui.lastCategory", lastCategory);
        values.put("gui.separateCategoryWindows", Boolean.toString(separateCategoryWindows));
        windowPositions.forEach((name, position) -> values.put(windowKey(name), position[0] + "," + position[1]));
    }

    public static String lastCategory() { return lastCategory; }
    public static void setLastCategory(String category) { lastCategory = category == null ? "" : category; }
    public static boolean separateCategoryWindows() { return separateCategoryWindows; }
    public static void setSeparateCategoryWindows(boolean separate) { separateCategoryWindows = separate; }
    public static double[] windowPosition(String name) {
        double[] position = windowPositions.get(name);
        return position == null ? null : position.clone();
    }
    public static void setWindowPosition(String name, double x, double y) {
        windowPositions.put(name, new double[]{clamp(x), clamp(y)});
    }

    private static String windowKey(String name) {
        return "gui.window." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }
    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
