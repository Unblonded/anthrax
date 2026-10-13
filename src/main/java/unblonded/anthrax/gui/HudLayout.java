package unblonded.anthrax.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each movable overlay element sits. A position is stored as a fraction of the free space
 * (screen size minus element size), so 0 hugs the left/top edge and 1 the right/bottom edge whatever
 * the element's size or the window resolution. Elements without a saved spot use their default.
 *
 * Elements draw at {@link #place} using the size they had last frame, then report their real bounds
 * with {@link #record}; the HUD editor drags them around through the same data.
 */
public final class HudLayout {
    private HudLayout() {}

    /** A movable element: id (config key), label shown in the editor, and a size used before it first draws. */
    public record Element(String id, String label, int defaultW, int defaultH) {}

    public static final List<Element> ELEMENTS = List.of(
            new Element("watermark", "Watermark", 230, 22),
            new Element("dig", "Dig Safety", 160, 20),
            new Element("players", "Player List", 248, 60),
            new Element("basefinder", "Base Finder", 236, 52),
            new Element("fps", "FPS Chart", 196, 70),
            new Element("modules", "Module List", 110, 120),
            new Element("effects", "Potion Effects", 120, 32),
            new Element("armor", "Armor", 80, 26),
            new Element("keystrokes", "Keystrokes", 72, 100),
            new Element("coords", "Coordinates", 150, 44),
            new Element("toasts", "Notifications", 220, 86),
            new Element("target", "Target HUD", 150, 58),
            new Element("scanbox", "Storage Scan box", 190, 28),
            new Element("music", "Now Playing", 220, 62),
            new Element("minimap", "Minimap", 120, 120));

    private static final String PREFIX = "hud:";
    /** Last drawn bounds in HUD pixels: x, y, w, h, frame. */
    private static final Map<String, int[]> BOUNDS = new LinkedHashMap<>();
    private static int frame;

    /** Called once per HUD frame so the editor can tell which elements actually drew. */
    static void beginFrame() { frame++; }

    static Element element(String id) {
        for (Element e : ELEMENTS) if (e.id.equals(id)) return e;
        throw new IllegalArgumentException(id);
    }

    /** Size from last frame, or the element's default (in design units) before it has drawn. */
    static int[] size(String id) {
        int[] b = BOUNDS.get(id);
        if (b != null && b[2] > 0 && b[3] > 0) return new int[]{b[2], b[3]};
        Element e = element(id);
        return new int[]{Draw.u(e.defaultW), Draw.u(e.defaultH)};
    }

    /** Top-left for an element of this size: its saved spot, else (defX, defY). Always kept on screen. */
    static int[] place(String id, int w, int h, int pw, int ph, int defX, int defY) {
        double[] saved = GuiPreferences.windowPosition(PREFIX + id);
        int maxX = Math.max(0, pw - w), maxY = Math.max(0, ph - h);
        if (saved == null) return new int[]{Math.max(0, Math.min(maxX, defX)), Math.max(0, Math.min(maxY, defY))};
        return new int[]{(int) Math.round(saved[0] * maxX), (int) Math.round(saved[1] * maxY)};
    }

    static boolean hasSaved(String id) { return GuiPreferences.windowPosition(PREFIX + id) != null; }

    /** Store a top-left position for an element of this size. */
    static void move(String id, int x, int y, int w, int h, int pw, int ph) {
        int maxX = Math.max(0, pw - w), maxY = Math.max(0, ph - h);
        GuiPreferences.setWindowPosition(PREFIX + id, maxX == 0 ? 0 : x / (double) maxX, maxY == 0 ? 0 : y / (double) maxY);
    }

    static void reset(String id) { GuiPreferences.removeWindowPosition(PREFIX + id); }

    static void record(String id, int x, int y, int w, int h) { BOUNDS.put(id, new int[]{x, y, w, h, frame}); }

    /** Bounds drawn in the current or previous frame, or null. */
    static int[] drawn(String id) {
        int[] b = BOUNDS.get(id);
        return b != null && frame - b[4] <= 1 ? b : null;
    }

    /** True when the element sits on the right half, so rows inside it should align right. */
    static boolean rightSide(int x, int w, int pw) { return x + w / 2 > pw / 2; }

    /** True when the element sits on the bottom half, so stacks inside it should grow upward. */
    static boolean bottomSide(int y, int h, int ph) { return y + h / 2 > ph / 2; }
}
