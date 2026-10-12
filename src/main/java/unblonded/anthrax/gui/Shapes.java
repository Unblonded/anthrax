package unblonded.anthrax.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Anti-aliased shape masks, generated on demand and cached. Each mask is white with alpha equal to the
 * exact pixel coverage of the shape (supersampled), so drawing it tinted at 1:1 gives the same smooth
 * edges a browser gives {@code border-radius}. Soft shapes (glows, shadows) use linear filtering.
 * Render thread only.
 */
final class Shapes {
    private static final int SUPERSAMPLE = 6;
    private static final Map<Long, Identifier> CACHE = new HashMap<>();
    private static int created;

    private Shapes() {}

    /** A texture whose sampler can be switched to linear filtering for smooth upscaling. */
    private static final class MaskTexture extends DynamicTexture {
        MaskTexture(String name, NativeImage image, boolean linear) {
            super(() -> name, image);
            if (linear) sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    private interface Coverage { float at(float x, float y); }

    private static Identifier make(long key, String name, int w, int h, boolean linear, Coverage coverage, boolean supersample) {
        Identifier cached = CACHE.get(key);
        if (cached != null) return cached;
        NativeImage image = new NativeImage(Math.max(1, w), Math.max(1, h), false);
        int n = supersample ? SUPERSAMPLE : 1;
        float inv = 1f / n;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float sum = 0;
                for (int sy = 0; sy < n; sy++)
                    for (int sx = 0; sx < n; sx++)
                        sum += coverage.at(x + (sx + 0.5f) * inv, y + (sy + 0.5f) * inv);
                int a = Math.round(Math.max(0, Math.min(1, sum / (n * n))) * 255);
                image.setPixel(x, y, (a << 24) | 0xFFFFFF);
            }
        Identifier id = Identifier.fromNamespaceAndPath("anthrax", "shape/" + name + "_" + (created++));
        Minecraft.getInstance().getTextureManager().register(id, new MaskTexture(name, image, linear));
        CACHE.put(key, id);
        return id;
    }

    private static long key(int kind, int a, int b, int c) {
        return ((long) kind << 56) | ((long) (a & 0xFFFF) << 32) | ((long) (b & 0xFFFF) << 16) | (c & 0xFFFF);
    }

    /** Filled disk of radius r, as a 2r x 2r mask. */
    static Identifier disk(int r) {
        float rf = r;
        return make(key(1, r, 0, 0), "disk" + r, 2 * r, 2 * r, false, (x, y) -> {
            float dx = x - rf, dy = y - rf;
            return dx * dx + dy * dy <= rf * rf ? 1 : 0;
        }, true);
    }

    /** Ring of outer radius r and thickness t, as a 2r x 2r mask. */
    static Identifier ring(int r, int t) {
        float ro = r, ri = Math.max(0, r - t);
        return make(key(2, r, t, 0), "ring" + r + "_" + t, 2 * r, 2 * r, false, (x, y) -> {
            float dx = x - ro, dy = y - ro, d2 = dx * dx + dy * dy;
            return d2 <= ro * ro && d2 >= ri * ri ? 1 : 0;
        }, true);
    }

    /**
     * Arc of a ring from 12 o'clock clockwise covering {@code percent}% with round caps; 2r x 2r mask.
     */
    static Identifier arc(int r, int t, int percent) {
        float ro = r, ri = Math.max(0, r - t), mid = (ro + ri) / 2f, cap = (ro - ri) / 2f;
        double sweep = Math.PI * 2 * percent / 100.0;
        // cap centres at the start and end of the arc
        float sx = ro, sy = ro - mid;
        float ex = ro + (float) Math.sin(sweep) * mid, ey = ro - (float) Math.cos(sweep) * mid;
        return make(key(3, r, t, percent), "arc" + r + "_" + t + "_" + percent, 2 * r, 2 * r, false, (x, y) -> {
            float dx = x - ro, dy = y - ro, d2 = dx * dx + dy * dy;
            if ((x - sx) * (x - sx) + (y - sy) * (y - sy) <= cap * cap) return 1;
            if ((x - ex) * (x - ex) + (y - ey) * (y - ey) <= cap * cap) return 1;
            if (d2 > ro * ro || d2 < ri * ri) return 0;
            double angle = Math.atan2(dx, -dy);          // 0 at 12 o'clock, clockwise positive
            if (angle < 0) angle += Math.PI * 2;
            return angle <= sweep ? 1 : 0;
        }, true);
    }

    /** Smooth radial falloff (1 at centre, 0 at the edge), 128 px, linearly filtered for any size. */
    static Identifier radial() {
        return make(key(4, 0, 0, 0), "radial", 128, 128, true, (x, y) -> {
            float dx = (x - 64) / 64f, dy = (y - 64) / 64f;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float v = Math.max(0, 1 - d);
            return v * v * (3 - 2 * v);   // smoothstep
        }, false);
    }

    /**
     * Quarter of a blurred rounded corner, like CSS box-shadow: solid inside radius r, fading out over
     * {@code s} pixels. Size (r + s) square; the corner circle centre is the bottom-right pixel corner.
     */
    static Identifier softCorner(int r, int s) {
        int size = r + s;
        return make(key(5, r, s, 0), "soft" + r + "_" + s, size, size, true, (x, y) -> {
            float dx = size - x, dy = size - y;
            float e = (float) Math.sqrt(dx * dx + dy * dy) - r;   // distance outside the solid corner
            float v = 1 - Math.max(0, Math.min(1, e / Math.max(1, s)));
            return v * v * (3 - 2 * v);
        }, false);
    }

    // ------------------------------------------------------------------ logo
    /** Apple-style squircle (superellipse, n = 5) filling a d x d square. */
    private static float squircle(float x, float y, float d) {
        float nx = Math.abs(x / d * 2 - 1), ny = Math.abs(y / d * 2 - 1);
        return Math.pow(nx, 5) + Math.pow(ny, 5) <= 1 ? 1 : 0;
    }

    /** Distance from a point to a segment, in the same units. */
    private static float segment(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay, wx = px - ax, wy = py - ay;
        float t = Math.max(0, Math.min(1, (wx * vx + wy * vy) / (vx * vx + vy * vy)));
        float dx = wx - vx * t, dy = wy - vy * t;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Logo tile shape. */
    static Identifier logoTile(int d) {
        return make(key(6, d, 0, 0), "logo_tile" + d, d, d, false, (x, y) -> squircle(x, y, d), true);
    }

    /** Logo tile with alpha fading along the diagonal, tinted with the second accent for a gradient. */
    static Identifier logoSheen(int d) {
        return make(key(6, d, 1, 0), "logo_sheen" + d, d, d, false, (x, y) -> {
            float t = Math.max(0, Math.min(1, (x + y) / (2f * d)));
            return squircle(x, y, d) * t * t;
        }, true);
    }

    /** Glossy highlight across the top of the tile. */
    static Identifier logoGloss(int d) {
        return make(key(6, d, 2, 0), "logo_gloss" + d, d, d, false, (x, y) -> {
            float v = 1 - y / (d * 0.55f);
            return v <= 0 ? 0 : squircle(x, y, d) * v * v * 0.55f;
        }, true);
    }

    /**
     * The Anthrax mark: a sharp "A" chevron with round-capped strokes and a spore dot in place of the
     * crossbar, ringed by a thin orbit broken where it would cross the legs.
     */
    static Identifier logoMark(int d) {
        return make(key(6, d, 3, 0), "logo_mark" + d, d, d, false, (x, y) -> {
            float u = x / d, v = y / d;
            float stroke = 0.065f;
            float ax = 0.5f, ay = 0.19f, lx = 0.25f, rx = 0.75f, by = 0.80f;
            if (segment(u, v, ax, ay, lx, by) <= stroke || segment(u, v, ax, ay, rx, by) <= stroke) return 1;
            float dx = u - 0.5f, dy = v - 0.585f, r = (float) Math.sqrt(dx * dx + dy * dy);
            if (r <= 0.075f) return 1;                                       // spore
            if (Math.abs(r - 0.135f) <= 0.018f && dy > 0.02f) return 0.85f;  // orbit (lower half, between the legs)
            return 0;
        }, true);
    }

    // ------------------------------------------------------------------ drawing helpers
    /** Draws a w x h region of a mask (u, v in mask pixels) tinted with {@code color}. */
    static void blit(GuiGraphicsExtractor g, Identifier mask, int x, int y, int w, int h,
                     float u, float v, int uw, int vh, int texW, int texH, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, mask, x, y, u, v, w, h, uw, vh, texW, texH, color);
    }
}
