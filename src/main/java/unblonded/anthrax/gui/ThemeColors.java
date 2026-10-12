package unblonded.anthrax.gui;

/** Read-only access to the menu's live accent colors for world and model renderers. */
public final class ThemeColors {
    private ThemeColors() {}

    public static int accent() { Theme.tick(System.currentTimeMillis()); return Theme.accent(); }
    public static int accent2() { Theme.tick(System.currentTimeMillis()); return Theme.accent2(); }
    public static float hue() { Theme.tick(System.currentTimeMillis()); return Theme.hue; }
    public static int hsl(float h, float s, float l) { return Draw.hsl(h, s, l); }
}
