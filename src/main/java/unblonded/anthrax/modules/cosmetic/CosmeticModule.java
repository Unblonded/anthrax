package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import unblonded.anthrax.gui.ThemeColors;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Base for the cosmetic modules: client-side looks only. Nothing here sends anything to the server, so other
 * players never see these (except where a module says it shows you what others are doing).
 */
public abstract class CosmeticModule extends FeatureModule {
    public static final String CATEGORY = "Cosmetics";

    protected CosmeticModule(String name, String description) { super(name, description, CATEGORY, false); }

    /** The colour choice most cosmetics share: menu theme, rainbow, a preset or your own hue. */
    public static final class Palette {
        final Setting.Mode mode;
        final Setting.Num hue;
        final Setting.Num speed;
        Palette(Setting.Mode mode, Setting.Num hue, Setting.Num speed) { this.mode = mode; this.hue = hue; this.speed = speed; }

        /** RGB (no alpha) for a point along the effect; {@code phase} 0..1 spreads gradients and rainbows. */
        public int rgb(double phase) {
            double t = System.currentTimeMillis() / 1000.0 * speed.get();
            return switch (mode.get()) {
                case "Rainbow" -> hsl((float) ((t * 60 + phase * 360) % 360), 0.9f, 0.62f);
                case "Custom" -> hsl((float) hue.get(), 0.9f, 0.62f);
                case "White" -> 0xFFFFFF;
                case "Fire" -> hsl((float) (8 + 40 * wave(t + phase)), 1f, 0.56f);
                case "Ice" -> hsl((float) (180 + 30 * wave(t + phase)), 0.85f, 0.7f);
                case "Toxic" -> hsl((float) (85 + 45 * wave(t + phase)), 0.95f, 0.55f);
                case "Vaporwave" -> hsl((float) (290 - 110 * wave(t * 0.7 + phase)), 0.9f, 0.66f);
                case "Blood" -> hsl((float) (350 + 12 * wave(t + phase)) % 360, 0.9f, 0.42f);
                default -> {
                    int a = ThemeColors.accent(), b = ThemeColors.accent2();
                    yield mix(a, b, (float) wave(t * 0.5 + phase)) & 0xFFFFFF;
                }
            };
        }

        public int rgb() { return rgb(0); }
    }

    protected final Palette palette(String def) {
        Setting.Mode mode = setting(Setting.Mode.standalone("Colour", def, "Theme", "Rainbow", "Custom", "White", "Fire", "Ice", "Toxic", "Vaporwave", "Blood"),
                "Theme follows the menu accent; Rainbow cycles; Custom uses the hue below.");
        Setting.Num hue = setting(Setting.Num.standalone("Hue", 285, 0, 360, 1), "Used by the Custom colour.");
        Setting.Num speed = setting(Setting.Num.standalone("Colour speed", 1, 0, 5, 0.1), "How fast animated colours move.");
        return new Palette(mode, hue, speed);
    }

    /** 0..1 triangle-smooth wave. */
    static double wave(double t) { return 0.5 + 0.5 * Math.sin(t * Math.PI * 2); }

    static int hsl(float h, float s, float l) { return ThemeColors.hsl(((h % 360) + 360) % 360, s, l) & 0xFFFFFF; }

    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static boolean firstPerson(Minecraft mc) { return mc.options.getCameraType().isFirstPerson(); }
}
