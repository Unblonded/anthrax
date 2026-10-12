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
    /** Window sizes in design units (pixels / Draw.S), so they survive GUI-scale changes. */
    private static final Map<String, int[]> windowSizes = new LinkedHashMap<>();
    private static final java.util.Set<String> collapsed = new java.util.LinkedHashSet<>();
    /** Category windows back to front. */
    private static final java.util.List<String> windowOrder = new java.util.ArrayList<>();
    private static final java.util.Set<String> openCards = new java.util.LinkedHashSet<>();

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
        windowSizes.clear();
        collapsed.clear();
        for (String key : properties.stringPropertyNames()) {
            try {
                if (key.startsWith("gui.window.")) {
                    String[] parts = properties.getProperty(key, "").split(",", 2);
                    if (parts.length != 2) continue;
                    double x = Double.parseDouble(parts[0]), y = Double.parseDouble(parts[1]);
                    if (Double.isFinite(x) && Double.isFinite(y)) windowPositions.put(decode(key, "gui.window."), new double[]{clamp(x), clamp(y)});
                } else if (key.startsWith("gui.size.")) {
                    String[] parts = properties.getProperty(key, "").split(",", 2);
                    if (parts.length != 2) continue;
                    windowSizes.put(decode(key, "gui.size."), new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
                } else if (key.startsWith("gui.collapsed.") && Boolean.parseBoolean(properties.getProperty(key))) {
                    collapsed.add(decode(key, "gui.collapsed."));
                }
            } catch (RuntimeException ignored) {}
        }
        windowOrder.clear();
        for (String name : properties.getProperty("gui.windowOrder", "").split("\\|")) if (!name.isEmpty()) windowOrder.add(name);
        openCards.clear();
        for (String name : properties.getProperty("gui.openCards", "").split("\\|")) if (!name.isEmpty()) openCards.add(name);
    }

    private static String decode(String key, String prefix) {
        return new String(Base64.getUrlDecoder().decode(key.substring(prefix.length())), StandardCharsets.UTF_8);
    }

    public static void save(Properties properties) {
        Map<String, String> values = new LinkedHashMap<>();
        save(values);
        values.forEach(properties::setProperty);
    }

    public static void save(java.util.Map<String, String> values) {
        values.put("gui.theme", Integer.toString(Theme.idx));
        values.put("gui.fx", Integer.toString(Draw.FX));
        values.put("gui.fancy", Boolean.toString(Draw.FANCY));
        values.put("gui.antialias", Boolean.toString(Draw.AA));
        values.put("gui.lastCategory", lastCategory);
        values.put("gui.separateCategoryWindows", Boolean.toString(separateCategoryWindows));
        windowPositions.forEach((name, position) -> values.put(windowKey(name), position[0] + "," + position[1]));
        windowSizes.forEach((name, size) -> values.put(key("gui.size.", name), size[0] + "," + size[1]));
        for (String name : collapsed) values.put(key("gui.collapsed.", name), "true");
        values.put("gui.windowOrder", String.join("|", windowOrder));
        values.put("gui.openCards", String.join("|", openCards));
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

    public static void removeWindowPosition(String name) { windowPositions.remove(name); }

    /** Saved size in design units, or null. */
    public static int[] windowSize(String name) {
        int[] size = windowSizes.get(name);
        return size == null ? null : size.clone();
    }
    public static void setWindowSize(String name, int w, int h) { windowSizes.put(name, new int[]{w, h}); }
    public static void removeWindowSize(String name) { windowSizes.remove(name); }

    public static boolean collapsed(String name) { return collapsed.contains(name); }
    public static void setCollapsed(String name, boolean value) { if (value) collapsed.add(name); else collapsed.remove(name); }

    /** Category windows back to front; unknown names go to the back in the given order. */
    public static java.util.List<String> windowOrder(java.util.List<String> names) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String n : names) if (!windowOrder.contains(n)) out.add(n);
        for (String n : windowOrder) if (names.contains(n)) out.add(n);
        return out;
    }
    public static void bringToFront(String name) { windowOrder.remove(name); windowOrder.add(name); }

    public static boolean cardOpen(String module) { return openCards.contains(module); }
    public static void setCardOpen(String module, boolean open) { if (open) openCards.add(module); else openCards.remove(module); }

    private static String windowKey(String name) { return key("gui.window.", name); }

    private static String key(String prefix, String name) {
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }
    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
