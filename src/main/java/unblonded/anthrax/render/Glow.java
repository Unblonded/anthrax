package unblonded.anthrax.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import unblonded.anthrax.gui.ThemeColors;
import unblonded.anthrax.modules.visual.HandRenderModule;

/** Soft neon outline used for the first-person hand and held item: wide faint halo, mid glow, bright core. */
public final class Glow {
    private Glow() {}

    /** Current outline color (opaque RGB, no alpha) from the Hand Render settings. */
    public static int color() {
        double t = System.currentTimeMillis() * .001;
        return switch (HandRenderModule.INSTANCE.outlineColor()) {
            case "Theme" -> ThemeColors.accent() & 0xFFFFFF;
            case "White" -> 0xFFFFFF;
            case "Ice" -> 0x9FE8FF;
            default -> ThemeColors.hsl((float) (t * 55), 0.95f, 0.62f) & 0xFFFFFF;
        };
    }

    /** One glowing segment from a to b in the given pose's space. */
    public static void segment(PoseStack.Pose pose, VertexConsumer out,
                               float ax, float ay, float az, float bx, float by, float bz, int rgb) {
        float strength = HandRenderModule.INSTANCE.glowStrength();
        int core = mix(rgb, 0xFFFFFF, 0.55f);
        if (strength > 0) {
            line(pose, out, ax, ay, az, bx, by, bz, rgb, Math.round(34 * strength), 2f + 7f * strength);
            line(pose, out, ax, ay, az, bx, by, bz, rgb, Math.round(95 * strength), 1.5f + 3f * strength);
        }
        line(pose, out, ax, ay, az, bx, by, bz, core, 255, 1.6f);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer out, float ax, float ay, float az,
                             float bx, float by, float bz, int rgb, int alpha, float width) {
        int color = (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
        out.addVertex(pose, ax, ay, az).setColor(color).setNormal(pose, 0, 0, 1).setLineWidth(width);
        out.addVertex(pose, bx, by, bz).setColor(color).setNormal(pose, 0, 0, 1).setLineWidth(width);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
