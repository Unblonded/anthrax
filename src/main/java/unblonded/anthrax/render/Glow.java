package unblonded.anthrax.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import unblonded.anthrax.gui.ThemeColors;
import unblonded.anthrax.modules.visual.HandRenderModule;

/**
 * Neon outline for the first-person hand and held item. Every vertex gets its own color from its
 * on-screen position, so palettes flow along the outline ("spread"), a bright glint band can sweep
 * across it, and the whole thing can breathe (pulse). Styles: Neon (halo + glow + core), Solid,
 * Soft (glow only) and Double (two-tone stripe).
 */
public final class Glow {
    private Glow() {}

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Vector3f TMP = new Vector3f();

    /** One glowing segment from a to b in the given pose's space. */
    public static void segment(PoseStack.Pose pose, VertexConsumer out,
                               float ax, float ay, float az, float bx, float by, float bz) {
        HandRenderModule m = HandRenderModule.INSTANCE;
        float t = (System.currentTimeMillis() % 3_600_000L) * .001f;
        // First-person poses are world-oriented; the model-view stack turns them into stable screen space.
        VIEW.set(RenderSystem.getModelViewStack()).mul(pose.pose());
        VIEW.transformPosition(ax, ay, az, TMP);
        float vax = TMP.x, vay = TMP.y, vaz = TMP.z;
        VIEW.transformPosition(bx, by, bz, TMP);
        int ca = colorAt(m, vax, vay, vaz, t), cb = colorAt(m, TMP.x, TMP.y, TMP.z, t);
        float ga = glint(m, vax, vay, t), gb = glint(m, TMP.x, TMP.y, t);

        float pulse = 1f - m.pulse() * (.5f + .5f * (float) Math.sin(t * m.pulseSpeed() * Math.PI));
        float strength = m.glowStrength() * pulse, size = m.glowSize(), w = m.width() * (.75f + .25f * pulse);
        switch (m.style()) {
            case "Solid" -> line(pose, out, ax, ay, az, bx, by, bz, mix(ca, 0xFFFFFF, ga), mix(cb, 0xFFFFFF, gb), 255, 255, w);
            case "Soft" -> {
                halo(pose, out, ax, ay, az, bx, by, bz, ca, cb, ga, gb, Math.max(.35f, strength), size, w);
                line(pose, out, ax, ay, az, bx, by, bz, mix(ca, 0xFFFFFF, ga), mix(cb, 0xFFFFFF, gb), 150, 150, w * 1.4f);
            }
            case "Double" -> {
                if (strength > 0) halo(pose, out, ax, ay, az, bx, by, bz, ca, cb, ga, gb, strength * .6f, size, w);
                line(pose, out, ax, ay, az, bx, by, bz, ca, cb, 255, 255, w * 2.6f);
                line(pose, out, ax, ay, az, bx, by, bz, mix(shift(ca), 0xFFFFFF, ga), mix(shift(cb), 0xFFFFFF, gb), 255, 255, w);
            }
            default -> {
                if (strength > 0) halo(pose, out, ax, ay, az, bx, by, bz, ca, cb, ga, gb, strength, size, w);
                float core = m.core();
                line(pose, out, ax, ay, az, bx, by, bz, mix(mix(ca, 0xFFFFFF, core), 0xFFFFFF, ga),
                        mix(mix(cb, 0xFFFFFF, core), 0xFFFFFF, gb), 255, 255, w);
            }
        }
    }

    private static void halo(PoseStack.Pose pose, VertexConsumer out, float ax, float ay, float az, float bx, float by, float bz,
                             int ca, int cb, float ga, float gb, float strength, float size, float w) {
        // the glint brightens the halo too, so the sweep reads as light passing over the edge
        line(pose, out, ax, ay, az, bx, by, bz, ca, cb, Math.round(34 * strength * (1 + ga)), Math.round(34 * strength * (1 + gb)),
                w + 7f * strength * size);
        line(pose, out, ax, ay, az, bx, by, bz, ca, cb, Math.round(95 * strength * (1 + ga * .6f)), Math.round(95 * strength * (1 + gb * .6f)),
                w * .9f + 3f * strength * size);
    }

    /** Palette color at a view-space point. */
    private static int colorAt(HandRenderModule m, float x, float y, float z, float t) {
        float s = m.saturation(), l = m.brightness(), speed = m.speed();
        // ~3 units per hand width, so spread 1 shows roughly one color cycle across the item
        float spatial = (x * 1f + y * .8f + z * .5f) * 3f * m.spread();
        float wave = (float) Math.sin(spatial * Math.PI + t * speed * 2);
        return switch (m.outlineColor()) {
            case "Gradient" -> hueMix(m.hue(), m.hue2(), .5f + .5f * wave, s, l);
            case "Custom" -> ThemeColors.hsl(m.hue() + 10 * wave, s, l) & 0xFFFFFF;
            case "Theme" -> ThemeColors.accent() & 0xFFFFFF;
            case "Fire" -> ThemeColors.hsl(24 + 20 * (float) Math.sin(spatial * 3 + t * speed * 4), s, l) & 0xFFFFFF;
            case "Toxic" -> ThemeColors.hsl(105 + 28 * wave, s, l) & 0xFFFFFF;
            case "Vaporwave" -> hueMix(300, 190, .5f + .5f * wave, s, l);
            case "Ocean" -> ThemeColors.hsl(205 + 22 * wave, s, l) & 0xFFFFFF;
            case "Ice" -> ThemeColors.hsl(195, s * .7f, Math.max(l, .78f)) & 0xFFFFFF;
            case "White" -> 0xFFFFFF;
            default -> ThemeColors.hsl(t * 55 * speed + spatial * 120, s, l) & 0xFFFFFF;
        };
    }

    /** 0..1 brightness of the glint band passing over this point. */
    private static float glint(HandRenderModule m, float x, float y, float t) {
        if (!m.glint()) return 0;
        float phase = (t * m.glintSpeed() * .6f) % 2.4f - 1.2f;   // sweeps across, then pauses off-screen
        float d = (x * .7f + y * .7f) - phase;
        float w = m.glintWidth();
        return .9f * (float) Math.exp(-(d * d) / (w * w));
    }

    private static void line(PoseStack.Pose pose, VertexConsumer out, float ax, float ay, float az,
                             float bx, float by, float bz, int rgbA, int rgbB, int alphaA, int alphaB, float width) {
        // The line shader reads the normal as the segment's direction and widens the line perpendicular to
        // it on screen. A fixed normal collapses lines to nothing whenever it lines up with the view.
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        dx /= len; dy /= len; dz /= len;
        out.addVertex(pose, ax, ay, az).setColor(argb(rgbA, alphaA)).setNormal(pose, dx, dy, dz).setLineWidth(width);
        out.addVertex(pose, bx, by, bz).setColor(argb(rgbB, alphaB)).setNormal(pose, dx, dy, dz).setLineWidth(width);
    }

    private static int argb(int rgb, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF); }

    /** Complementary-ish partner color for the Double style. */
    private static int shift(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return (g << 16) | (b << 8) | r;
    }

    /** Blend two hues the short way round the color wheel. */
    private static int hueMix(float h1, float h2, float k, float s, float l) {
        float d = ((h2 - h1 + 540) % 360) - 180;
        return ThemeColors.hsl(h1 + d * k, s, l) & 0xFFFFFF;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
