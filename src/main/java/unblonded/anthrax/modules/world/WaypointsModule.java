package unblonded.anthrax.modules.world;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Waypoints per server and dimension, with automatic death waypoints. Shown as beams in the world
 * and as glass labels pinned on screen (drawn by the HUD). Also driven by the {@code .wp} command.
 */
public final class WaypointsModule extends FeatureModule {
    public static final WaypointsModule INSTANCE = new WaypointsModule();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("anthrax-waypoints.csv");
    private static final int[] PALETTE = {0xFF4AF5FF, 0xFFFF7AD9, 0xFFFFC94A, 0xFF7CFF7C, 0xFFB76BFF, 0xFFFF8A4C};

    public static final class Waypoint {
        public final String server, dimension, name;
        public final int x, y, z, color;
        public final boolean death;
        Waypoint(String server, String dimension, String name, int x, int y, int z, int color, boolean death) {
            this.server = server; this.dimension = dimension; this.name = name;
            this.x = x; this.y = y; this.z = z; this.color = color; this.death = death;
        }
    }

    private final List<Waypoint> all = new ArrayList<>();
    private final Setting.Text newName = setting(Setting.Text.standalone("New waypoint name", "Home", 32));
    private final Setting.Bool beams = setting(Setting.Bool.standalone("Beams", true));
    private final Setting.Bool labels = setting(Setting.Bool.standalone("On-screen labels", true));
    private final Setting.Bool deaths = setting(Setting.Bool.standalone("Death waypoints", true));
    private final Setting.Num maxDeaths = setting(Setting.Num.standalone("Death waypoints kept", 3, 1, 10, 1));
    private final Setting.Action add = setting(new Setting.Action("Add waypoint here", () -> addHere(newNameValue())));
    private final Setting.Action removeNearest = setting(new Setting.Action("Remove nearest", this::removeNearest));
    private final Setting.Action clearDeaths = setting(new Setting.Action("Clear death waypoints", () -> {
        all.removeIf(w -> w.death);
        persist();
        Notifications.info("Waypoints", "Death waypoints cleared");
    }));

    private boolean loaded, wasDead;

    private WaypointsModule() {
        super("Waypoints", "Named waypoints and death points with beams and on-screen labels (.wp)", "World", true);
    }

    @Override protected boolean hiddenFromList() { return true; }

    private String newNameValue() { return newName.get().isBlank() ? "Waypoint" : newName.get().trim(); }

    @Override public void tick(Minecraft mc) {
        if (!loaded) load();
        if (mc.player == null) return;
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead && deaths.get()) {
            String name = "Death " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            List<Waypoint> mine = new ArrayList<>();
            for (Waypoint w : all) if (w.death && matches(w, mc)) mine.add(w);
            while (mine.size() >= (int) maxDeaths.get()) all.remove(mine.removeFirst());
            all.add(make(mc, name, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), 0xFFFF4D6D, true));
            persist();
            Notifications.warn("Waypoints", name + " saved at " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ());
        }
        wasDead = dead;
    }

    // ------------------------------------------------------------------ API used by commands
    public List<Waypoint> here() {
        Minecraft mc = Minecraft.getInstance();
        List<Waypoint> out = new ArrayList<>();
        if (mc.level == null) return out;
        if (!loaded) load();
        for (Waypoint w : all) if (matches(w, mc)) out.add(w);
        return out;
    }

    public void addHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        add(name, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
    }

    public void add(String name, int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!loaded) load();
        all.removeIf(w -> matches(w, mc) && w.name.equalsIgnoreCase(name));
        all.add(make(mc, name, x, y, z, PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)], false));
        persist();
        Notifications.success("Waypoints", "Added " + name + " at " + x + " " + y + " " + z);
    }

    public boolean remove(String name) {
        Minecraft mc = Minecraft.getInstance();
        boolean removed = all.removeIf(w -> matches(w, mc) && w.name.equalsIgnoreCase(name));
        if (removed) persist();
        return removed;
    }

    public void clearHere() {
        Minecraft mc = Minecraft.getInstance();
        all.removeIf(w -> matches(w, mc));
        persist();
    }

    private void removeNearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Waypoint best = null;
        double bestD = Double.MAX_VALUE;
        for (Waypoint w : here()) {
            double d = mc.player.distanceToSqr(w.x, w.y, w.z);
            if (d < bestD) { bestD = d; best = w; }
        }
        if (best == null) { Notifications.warn("Waypoints", "No waypoints here"); return; }
        all.remove(best);
        persist();
        Notifications.info("Waypoints", "Removed " + best.name);
    }

    public boolean showLabels() { return isEnabled() && labels.get(); }

    private static Waypoint make(Minecraft mc, String name, int x, int y, int z, int color, boolean death) {
        return new Waypoint(BaseLog.server(), mc.level.dimension().identifier().toString(), name, x, y, z, color, death);
    }

    private static boolean matches(Waypoint w, Minecraft mc) {
        ClientLevel level = mc.level;
        return level != null && w.server.equals(BaseLog.server()) && w.dimension.equals(level.dimension().identifier().toString());
    }

    // ------------------------------------------------------------------ persistence
    private void load() {
        loaded = true;
        all.clear();
        if (!Files.isRegularFile(FILE)) return;
        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t");
                if (p.length < 8) continue;
                try {
                    all.add(new Waypoint(p[0], p[1], p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                            (int) Long.parseLong(p[6], 16), Boolean.parseBoolean(p[7])));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            System.err.println("Anthrax: could not read waypoints: " + e.getMessage());
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (Waypoint w : all)
            lines.add(String.join("\t", w.server, w.dimension, w.name.replace('\t', ' '), Integer.toString(w.x),
                    Integer.toString(w.y), Integer.toString(w.z), Integer.toHexString(w.color), Boolean.toString(w.death)));
        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Notifications.error("Waypoints", "Could not save waypoints");
        }
    }

    // ------------------------------------------------------------------ world rendering
    public static void renderGizmos(LevelRenderer renderer) {
        WaypointsModule m = INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (!m.isEnabled() || !m.beams.get() || mc.level == null || mc.player == null) return;
        List<Waypoint> list = m.here();
        if (list.isEmpty()) return;
        int minY = mc.level.getMinY(), maxY = mc.level.getMaxY();
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            for (Waypoint w : list) {
                Vec3 bottom = new Vec3(w.x + .5, minY, w.z + .5), top = new Vec3(w.x + .5, maxY, w.z + .5);
                Gizmos.line(bottom, top, 0x60000000 | (w.color & 0xFFFFFF), 7f).setAlwaysOnTop();
                Gizmos.line(bottom, top, 0xF0000000 | (w.color & 0xFFFFFF), 2f).setAlwaysOnTop();
            }
        }
        renderer.addMainThreadGizmos(collector.drainGizmos());
    }
}
