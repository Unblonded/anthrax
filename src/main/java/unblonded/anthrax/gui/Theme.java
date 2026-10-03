package unblonded.anthrax.gui;

/** Colors + accent hue. All base colors are opaque so rounded borders composite cleanly. */
final class Theme {
    static final String[] NAMES = {"Rainbow", "Cyan", "Green", "Violet", "Red", "Amber", "Pink"};
    static final float[] HUES = {-1, 185, 145, 268, 352, 42, 322};

    static final int BG = 0xFF0B0B12;
    static final int HEADER = 0xFF10101A;
    static final int SIDE = 0xFF0D0D15;
    static final int CARD = 0xFF131320;
    static final int CARD_HOVER = 0xFF1A1A2B;
    static final int LINE = 0xFF24243A;
    static final int TEXT = 0xFFE8E8F2;
    static final int SUBTEXT = 0xFFB9BDD0;
    static final int MUTED = 0xFF8A8FA5;
    static final int DIM = 0xFF5C6075;
    static final int OK = 0xFF3DFFA0;
    static final int DANGER = 0xFFFF4D6D;

    static int idx = 3; // Violet by default
    static float hue = 268;

    private Theme() {}

    static void tick(long ms) {
        float h = HUES[idx];
        hue = h < 0 ? (ms / 60f) % 360f : h;
    }

    static void next() { idx = (idx + 1) % NAMES.length; }

    static int accent() { return Draw.hsl(hue, 0.95f, 0.64f); }
    static int accent2() { return Draw.hsl(hue + 60, 0.95f, 0.66f); }
}
