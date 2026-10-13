package unblonded.anthrax.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All drawing happens in real window pixels (the screen undoes Minecraft's GUI scale),
 * so shapes are pixel-exact and text is never magnified from a low-res atlas.
 * {@link #S} is our own integer UI scale; use {@link #u(float)} to convert design units to pixels.
 */
final class Draw {
    /**
     * Effect quality, cycled by the FX button in the header.
     *   0 = LITE : no effects at all, capped corners, fewest possible fills
     *   1 = FX   : all effects, but each one is built from a handful of cheap fills (default)
     *   2 = MAX  : the full-quality look (anti-aliased corners, layered glows, more particles)
     */
    static int FX = 1;
    /** True when any effects are drawn (FX >= 1). Kept for existing call sites. */
    static boolean FANCY = true;
    /** Legacy flag kept for saved configs; corners are now always anti-aliased. */
    static boolean AA = false;
    static final String[] FX_NAMES = {"LITE", "FX", "MAX"};
    static final String[] FX_LONG = {"Lite: no blur or effects, fastest", "Balanced: blur and glass, light effects", "Max: every effect (heaviest)"};

    static void setFx(int level) {
        FX = Math.floorMod(level, 3);
        FANCY = FX > 0;
        AA = FX >= 2;
    }

    /** Draw the item icons on module cards (item rendering is not free). */
    static boolean ICONS = true;

    static int S = 2;

    // ---------------------------------------------------------------- design tokens
    // All in design units (pass through u() like any other size). Spacing sits on a 4-unit grid.
    static final int SP_XS = 4, SP_S = 8, SP_M = 12, SP_L = 16, SP_XL = 24;
    /** Corner radii: floating windows, and cards or panels inside them. Pills use h / 2. */
    static final int R_WINDOW = 24, R_CARD = 16;
    /** Translucent tinted gray behind every window pane: dark enough for white text, the blurred world still shows through. */
    static final int GLASS_TINT = 0xB4181A20;
    /** Extra smoke pooled in a window's middle, fading out toward the rim, so the edge reads as thick bright glass. */
    static final int GLASS_CORE = 0x5C000000;
    /** Faint white sheet over the tint, so the pane still reads as glass rather than a flat slab. */
    static final int GLASS_SHEET = 0x0CFFFFFF;
    /** Rim glare on the top and left edges of a fully lit pane. */
    static final int GLASS_SPECULAR = 0x90FFFFFF;
    /** Rim shade on the bottom and right edges (the far side of the glass). */
    static final int GLASS_DEPTH = 0x20000000;
    /** Text drop shadow on/off (a 1 px dark copy under light text keeps it readable on glass). */
    static boolean TEXT_SHADOW = true;
    /** Approx. glyph box height in design units (matches a TTF "size" of ~9). */
    static final float TH = 9f;

    /**
     * One font per UI scale (anthrax:segoeui_1 .. segoeui_4), each with its TTF "size" = 9 * scale
     * and oversample 1. Text is drawn at exactly 1:1 with no matrix scaling and integer positions,
     * so every glyph pixel lands on a screen pixel (this is what removes the blur).
     */
    private static final FontDescription[] FONTS = new FontDescription[4];
    static {
        for (int i = 0; i < FONTS.length; i++) {
            FONTS[i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath("anthrax", "segoeui_" + (i + 1)));
        }
    }

    // Text is emitted every frame, but most strings/colors repeat. Reusing immutable styled
    // components avoids allocating a Component and style tree for every label on every frame.
    private record TextKey(String text, int color, boolean bold, int scale) {}
    private record WidthKey(String text, boolean bold, int scale) {}
    // The screen renders on Minecraft's render thread, so these reusable lookup probes are
    // safe and avoid allocating a record on every cache hit. Only real cache entries are stored.
    private static final class TextProbe {
        String text;
        int color, scale;
        boolean bold;

        @Override public int hashCode() {
            int result = text.hashCode();
            result = 31 * result + color;
            result = 31 * result + Boolean.hashCode(bold);
            return 31 * result + scale;
        }

        @Override public boolean equals(Object other) {
            if (other instanceof TextKey key) {
                return color == key.color() && bold == key.bold() && scale == key.scale() && text.equals(key.text());
            }
            return false;
        }
    }
    private static final class WidthProbe {
        String text;
        int scale;
        boolean bold;

        @Override public int hashCode() {
            return 31 * (31 * text.hashCode() + Boolean.hashCode(bold)) + scale;
        }

        @Override public boolean equals(Object other) {
            if (other instanceof WidthKey key) {
                return bold == key.bold() && scale == key.scale() && text.equals(key.text());
            }
            return false;
        }
    }
    private static final TextProbe TEXT_PROBE = new TextProbe();
    private static final WidthProbe WIDTH_PROBE = new WidthProbe();
    private static final String[] ASCII_CHARACTERS = new String[128];
    static {
        for (int i = 0; i < ASCII_CHARACTERS.length; i++) ASCII_CHARACTERS[i] = String.valueOf((char) i);
    }
    private static final int TEXT_CACHE_LIMIT = 4096;
    private static final Map<TextKey, Component> TEXT_CACHE = new LinkedHashMap<>(512, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<TextKey, Component> eldest) {
            return size() > TEXT_CACHE_LIMIT;
        }
    };
    private static final Map<WidthKey, Integer> WIDTH_CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<WidthKey, Integer> eldest) {
            return size() > TEXT_CACHE_LIMIT;
        }
    };

    private Draw() {}

    static int u(float n) { return Math.round(n * S); }

    // Text vertical tuning. If text still looks high/low, change TEXT_NUDGE (design units, + = down).
    static final float BASELINE = 7f;
    static final float CAP_CENTER = 0.35f;
    static final float TEXT_NUDGE = 0f;

    // ---------------------------------------------------------------- colors
    static int alpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int hsl(float h, float s, float l) {
        h = ((h % 360f) + 360f) % 360f;
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float hp = h / 60f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        switch ((int) hp) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        float m = l - c / 2;
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    static boolean inside(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    // ---------------------------------------------------------------- shapes
    static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) g.fill(x, y, x + w, y + h, c);
    }

    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        round(g, x, y, w, h, r, c, true, true);
    }

    /**
     * Rounded rectangle with anti-aliased corners (like CSS border-radius), optionally rounding only
     * the top or bottom pair of corners. Works with translucent colors.
     */
    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c, boolean top, boolean bottom) {
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r < 2) { fill(g, x, y, w, h, c); return; }
        // Anti-aliased corners from a coverage mask; the straight parts are plain fills. Nothing overlaps,
        // so translucent colors stay perfectly even.
        var disk = Shapes.disk(r);
        int d = 2 * r;
        if (top) {
            Shapes.blit(g, disk, x, y, r, r, 0, 0, r, r, d, d, c);
            Shapes.blit(g, disk, x + w - r, y, r, r, r, 0, r, r, d, d, c);
            fill(g, x + r, y, w - 2 * r, r, c);
        } else fill(g, x, y, w, r, c);
        if (bottom) {
            Shapes.blit(g, disk, x, y + h - r, r, r, 0, r, r, r, d, d, c);
            Shapes.blit(g, disk, x + w - r, y + h - r, r, r, r, r, r, r, d, d, c);
            fill(g, x + r, y + h - r, w - 2 * r, r, c);
        } else fill(g, x, y + h - r, w, r, c);
        fill(g, x, y + r, w, h - 2 * r, c);
    }

    /** Soft-edged rounded rect for glows and halos. */
    static void roundFast(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        if (!FANCY || w <= 0 || h <= 0) return;
        // Used for glows and halos: a soft-edged rounded rect reads like a CSS blur instead of a hard slab.
        int s = Math.max(2, Math.min(r, Math.min(w, h) / 2));
        soft(g, x, y, w, h, Math.max(0, r - s), s, c);
    }

    /**
     * Soft rounded rectangle (like CSS box-shadow): solid inside a rounded rect inset by {@code s},
     * fading to transparent at the outer bounds over {@code s} pixels.
     */
    static void soft(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int s, int c) {
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        s = Math.max(1, Math.min(s, Math.min(w, h) / 2));
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2 - s));
        int k = r + s;                                  // corner tile size
        var corner = Shapes.softCorner(r, s);
        Shapes.blit(g, corner, x, y, k, k, 0, 0, k, k, k, k, c);
        Shapes.blit(g, corner, x + w - k, y, k, k, k, 0, -k, k, k, k, c);
        Shapes.blit(g, corner, x, y + h - k, k, k, 0, k, k, -k, k, k, c);
        Shapes.blit(g, corner, x + w - k, y + h - k, k, k, k, k, -k, -k, k, k, c);
        int clear = alpha(c, 0);
        // edges: fade over s, solid over r (the corner tiles cover the rest)
        g.fillGradient(x + k, y, x + w - k, y + s, clear, c);
        g.fillGradient(x + k, y + h - s, x + w - k, y + h, c, clear);
        horizontal(g, x, y + k, s, h - 2 * k, clear, c);
        horizontal(g, x + w - s, y + k, s, h - 2 * k, c, clear);
        fill(g, x + k, y + s, w - 2 * k, r, c);
        fill(g, x + k, y + h - k, w - 2 * k, r, c);
        fill(g, x + s, y + k, w - 2 * s, h - 2 * k, c);
    }

    private static void horizontal(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        if (w > 0 && h > 0) horizontalGradient(g, x, y, w, h, c1, c2);
    }

    /** Rounded rect with a t-pixel border. */
    static void roundBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int t, int border, int fill) {
        round(g, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, r - t), fill);
        outline(g, x, y, w, h, r, t, border, border);
    }

    static void circle(GuiGraphicsExtractor g, int cx, int cy, int radius, int c) {
        if (radius <= 0 || (c >>> 24) == 0) return;
        if (radius < 2) { fill(g, cx - radius, cy - radius, radius * 2, radius * 2, c); return; }
        int d = radius * 2;
        Shapes.blit(g, Shapes.disk(radius), cx - radius, cy - radius, d, d, 0, 0, d, d, d, d, c);
    }

    static void hgrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0) return;
        horizontalGradient(g, x, y, w, h,
                lerp(c1, c2, 0.5f / w),
                lerp(c1, c2, (w - 0.5f) / w));
    }

    private static void horizontalGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y + h);
        g.pose().rotate(-(float) (Math.PI / 2));
        g.fillGradient(0, 0, h, w, c1, c2);
        g.pose().popMatrix();
    }

    /** Horizontal-gradient pill: anti-aliased half-disk caps plus a gradient body, no overlap. */
    static void pillGrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        int r = h / 2;
        if (r < 2) { hgrad(g, x, y, w, h, c1, c2); return; }
        if (w <= h) { round(g, x, y, w, h, r, c1); return; }
        var disk = Shapes.disk(r);
        int d = 2 * r;
        Shapes.blit(g, disk, x, y, r, h, 0, 0, r, d, d, d, c1);
        Shapes.blit(g, disk, x + w - r, y, r, h, r, 0, r, d, d, d, c2);
        hgrad(g, x + r, y, w - 2 * r, h, lerp(c1, c2, r / (float) w), lerp(c1, c2, 1f - r / (float) w));
    }

    /**
     * iOS-style switch. {@code on} is the animated position (0..1); the knob stretches while it is
     * travelling, which reads as a liquid drop rather than a sliding block.
     */
    static void toggle(GuiGraphicsExtractor g, int x, int y, int w, int h, float on, int accent) {
        float p = Math.max(0f, Math.min(1f, on));
        round(g, x, y, w, h, h / 2, lerp(0x2EFFFFFF, accent, p));
        if (p > 0.01f && FANCY) round(g, x + S, y + S, w - 2 * S, h / 2 - S, h / 2 - S, alpha(0xFFFFFF, (int) (0x22 * p)), true, false);
        int pad = Math.max(2, h / 9);
        int d = h - pad * 2;
        int stretch = Math.round((float) Math.sin(Math.PI * p) * d * 0.45f);
        int kw = d + stretch;
        int kx = x + pad + Math.round((w - 2 * pad - kw) * p);
        if (FANCY) roundFast(g, kx - S, y + pad + S, kw + 2 * S, d + S, d / 2, 0x38000000);
        round(g, kx, y + pad, kw, d, d / 2, 0xFFFFFFFF);
        if (FANCY) round(g, kx + S, y + pad + d / 2, kw - 2 * S, d / 2 - S, d / 2, 0x14000000, false, true);
    }

    /**
     * Circular gauge: a faint full track plus an arc from 12 o'clock covering {@code frac}, shaded
     * from c1 to c2, with a round cap. Built from short rotated segments.
     */
    static void ring(GuiGraphicsExtractor g, int cx, int cy, int radius, int thick, float frac, int track, int c1, int c2) {
        if (radius < 2) return;
        int d = radius * 2;
        thick = Math.max(1, Math.min(thick, radius));
        Shapes.blit(g, Shapes.ring(radius, thick), cx - radius, cy - radius, d, d, 0, 0, d, d, d, d, track);
        int percent = Math.round(Math.max(0f, Math.min(1f, frac)) * 100);
        if (percent <= 0) return;
        int color = lerp(c1, c2, 0.5f + 0.5f * Math.min(1f, frac));
        Shapes.blit(g, Shapes.arc(radius, thick, percent), cx - radius, cy - radius, d, d, 0, 0, d, d, d, d, color);
    }

    // ---------------------------------------------------------------- liquid glass

    /**
     * Alpha-safe rounded ring (no fill), brighter at the top than at the bottom like light catching
     * the rim of a glass pane. Nothing overlaps, so translucent colors stay even.
     */
    static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int t, int top, int bottom) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        r = Math.max(t, Math.min(r, Math.min(w, h) / 2));
        if (r < 2) {
            fill(g, x, y, w, t, top);
            fill(g, x, y + h - t, w, t, bottom);
            vgrad(g, x, y + t, t, h - 2 * t, top, bottom);
            vgrad(g, x + w - t, y + t, t, h - 2 * t, top, bottom);
            return;
        }
        var ring = Shapes.ring(r, t);
        int d = 2 * r;
        Shapes.blit(g, ring, x, y, r, r, 0, 0, r, r, d, d, top);
        Shapes.blit(g, ring, x + w - r, y, r, r, r, 0, r, r, d, d, top);
        Shapes.blit(g, ring, x, y + h - r, r, r, 0, r, r, r, d, d, bottom);
        Shapes.blit(g, ring, x + w - r, y + h - r, r, r, r, r, r, r, d, d, bottom);
        fill(g, x + r, y, w - 2 * r, t, top);
        fill(g, x + r, y + h - t, w - 2 * r, t, bottom);
        vgrad(g, x, y + r, t, h - 2 * r, top, bottom);
        vgrad(g, x + w - t, y + r, t, h - 2 * r, top, bottom);
    }

    /** Soft drop shadow under a rounded panel. */
    static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int spread, int alphaMax) {
        if (!FANCY || spread <= 0) return;
        // CSS-style drop shadow: one blurred rounded rect, offset slightly down.
        soft(g, x - spread, y - spread + spread / 3, w + 2 * spread, h + 2 * spread, r, spread * 2, alpha(0, alphaMax));
    }

    /**
     * Glass rim lit from the top-left: {@code tl} on the top and left edges, {@code br} on the bottom and
     * right, passing through {@code mid} at the other two corners. Nothing overlaps, so translucent colors
     * stay even. Swap tl and br for a recessed (inset) edge.
     */
    static void rim(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int t, int tl, int mid, int br) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        r = Math.max(t, Math.min(r, Math.min(w, h) / 2));
        if (r < 2) {
            hgrad(g, x, y, w, t, tl, mid);
            hgrad(g, x, y + h - t, w, t, mid, br);
            vgrad(g, x, y + t, t, h - 2 * t, tl, mid);
            vgrad(g, x + w - t, y + t, t, h - 2 * t, mid, br);
            return;
        }
        var ring = Shapes.ring(r, t);
        int d = 2 * r;
        Shapes.blit(g, ring, x, y, r, r, 0, 0, r, r, d, d, tl);
        Shapes.blit(g, ring, x + w - r, y, r, r, r, 0, r, r, d, d, mid);
        Shapes.blit(g, ring, x, y + h - r, r, r, 0, r, r, r, d, d, mid);
        Shapes.blit(g, ring, x + w - r, y + h - r, r, r, r, r, r, r, d, d, br);
        hgrad(g, x + r, y, w - 2 * r, t, tl, mid);
        hgrad(g, x + r, y + h - t, w - 2 * r, t, mid, br);
        vgrad(g, x, y + r, t, h - 2 * r, tl, mid);
        vgrad(g, x + w - t, y + r, t, h - 2 * r, mid, br);
    }

    /** Rim width: one real pixel up to UI scale 3, so the edge stays crisp instead of turning into a band. */
    static int rimWidth() { return Math.max(1, S / 2); }

    /**
     * A liquid-glass pane: translucent body, specular sheen on the upper half, and a 1 px rim lit from
     * the top-left (glare on top and left, depth shade on bottom and right). {@code lift} (0..1) is how
     * strongly it catches the light: idle controls sit around 0.35, hovered controls and windows at 1.
     */
    static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int body, float lift) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        round(g, x, y, w, h, r, body);
        int t = rimWidth();
        if (FANCY) {
            // specular sheen: strongest at the top edge, fading out by ~45% of the height
            int sheenA = Math.round(0x12 + 0x14 * lift);
            round(g, x + t, y + t, w - 2 * t, Math.max(1, r - t), Math.max(0, r - t), alpha(0xFFFFFF, sheenA), true, false);
            int fadeTop = y + r, fadeH = Math.max(0, Math.round(h * 0.45f) - r);
            vgrad(g, x + t, fadeTop, w - 2 * t, fadeH, alpha(0xFFFFFF, sheenA), 0x00FFFFFF);
        }
        int glare = Math.round((GLASS_SPECULAR >>> 24) * (0.55f + 0.45f * Math.max(0f, Math.min(1f, lift))));
        rim(g, x, y, w, h, r, t, alpha(0xFFFFFF, glare), alpha(0xFFFFFF, glare / 3), GLASS_DEPTH);
        if (!FANCY || w < 6 * t || h < 6 * t) return;
        // thick glass shows two edges: a faint second rim one pixel in, lit the same way
        rim(g, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, r - t), t,
                alpha(0xFFFFFF, glare / 4), alpha(0xFFFFFF, glare / 10), alpha(0xFFFFFF, glare / 8));
        // caustic: light that came in through the top leaves along the bottom edge, brightest right of centre
        int span = w - 2 * Math.max(r, t);
        if (span > 0) {
            int half = Math.max(1, span / 3);
            shineC(g, x + Math.max(r, t), span, y + h - 3 * t, t, x + Math.max(r, t) + span - half, half,
                    Math.round(0x50 * lift), 0xFFFFFF);
        }
    }

    /**
     * A floating window: a translucent tinted gray ({@link #GLASS_TINT}) over the blurred world with a faint
     * glass sheet on top, so white text keeps its contrast over snow, sky or sand. Lite mode has no blur
     * behind it, so the tint is denser there.
     */
    static void canvas(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        round(g, x, y, w, h, r, FX > 0 ? GLASS_TINT : alpha(GLASS_TINT, 0xE0));
        if (FANCY) {
            // lens edge: smoke pools in the middle and thins toward the rim, so the border band reads as
            // thick glass bending more light (one smooth soft fill, concentric with the pane, no seams)
            int e = u(2), fade = Math.max(2, Math.min(u(14), Math.min(w, h) / 4));
            soft(g, x + e, y + e, w - 2 * e, h - 2 * e, Math.max(0, r - e - fade), fade, GLASS_CORE);
            // light pool: a broad, slowly drifting glow under the top edge, left of centre, like a window
            // light caught in the glass
            int span = w - 2 * r;
            if (span > 0 && r > 0) {
                int half = Math.max(1, span / 4);
                float drift = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 2600.0);
                int cx = x + r + half + Math.round((span - 2 * half) * (0.15f + 0.3f * drift));
                int t = rimWidth();
                shineC(g, x + r, span, y + t, Math.max(1, r - t), cx, half, 0x1C, 0xFFFFFF);
            }
        }
        glass(g, x, y, w, h, r, GLASS_SHEET, 1f);
    }

    /**
     * Liquid-glass pill control. Idle it is a clear sheet and a lit rim, with no box behind it. As
     * {@code hover} rises (0..1) the sheet fills in, tinted with {@code tint}, the sheen brightens, and a
     * shimmer of light follows the cursor across the pill. {@code pressed} fills it a step further; the
     * caller sinks the label with it.
     */
    static void liquidButton(GuiGraphicsExtractor g, int x, int y, int w, int h, float hover, boolean pressed, int tint, int mouseX) {
        if (w <= 0 || h <= 0) return;
        float k = Math.max(0f, Math.min(1f, hover));
        int r = Math.min(w, h) / 2;
        int body = pressed ? alpha(tint, 0x58) : lerp(0x0CFFFFFF, alpha(tint, 0x3A), k);
        glass(g, x, y, w, h, r, body, 0.35f + 0.65f * k);
        if (!FANCY || k < 0.02f) return;
        // shimmer: a soft band of light under the cursor, kept inside the straight part so it never
        // spills past the rounded caps
        int t = rimWidth(), span = w - 2 * r;
        if (span <= 0) return;
        int half = Math.max(1, Math.min(span / 2, Math.max(h, w / 3)));
        int cx = Math.max(x + r + half, Math.min(x + w - r - half, mouseX));
        shineC(g, x + r, span, y + t, h - 2 * t, cx, half, Math.round(0x26 * k), 0xFFFFFF);
    }

    /** Slider or progress channel cut into the glass: dark fill with a rim lit from below (an inset edge). */
    static void track(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        round(g, x, y, w, h, h / 2, 0x38000000);
        rim(g, x, y, w, h, h / 2, rimWidth(), 0x40000000, 0x14FFFFFF, 0x30FFFFFF);
    }

    /** Eased hover state per control: current amount and when it was last updated (System.nanoTime). */
    private static final class Ease { float v; long t = System.nanoTime(); }
    private static final Map<String, Ease> HOVER = new HashMap<>();

    /**
     * Eased hover amount (0..1) for a control, keyed by a stable id, so fills and shimmer ease in and out
     * (~70 ms) instead of snapping. Time is tracked per control, so it needs no frame delta. The clock is
     * kept as a long: nanoTime counts from boot, and as a float it would only tick every ~0.1 s.
     */
    static float hover(String id, boolean hovered) {
        long now = System.nanoTime();
        Ease st = HOVER.computeIfAbsent(id, k -> new Ease());
        float dt = Math.min(0.05f, Math.max(0f, (now - st.t) / 1e9f));
        st.t = now;
        st.v += ((hovered ? 1f : 0f) - st.v) * Math.min(1f, dt * 30f);
        return st.v;
    }

    /**
     * Glossy round badge (app-icon style): an accent disk with a soft capsule highlight across its top,
     * all anti-aliased. {@code d} is the diameter.
     */
    static void bead(GuiGraphicsExtractor g, int x, int y, int d, int color) {
        circle(g, x + d / 2, y + d / 2, d / 2, color);
        int hw = Math.round(d * 0.62f), hh = Math.max(2, Math.round(d * 0.36f));
        round(g, x + (d - hw) / 2, y + Math.max(1, Math.round(d * 0.08f)), hw, hh, hh / 2, 0x4CFFFFFF);
    }

    /** The Anthrax logo: a glossy gradient squircle tile with the spore "A" mark, at any size, anti-aliased. */
    static void logo(GuiGraphicsExtractor g, int x, int y, int d, int c1, int c2) {
        if (d <= 0) return;
        Shapes.blit(g, Shapes.logoTile(d), x, y, d, d, 0, 0, d, d, d, d, c1);
        Shapes.blit(g, Shapes.logoSheen(d), x, y, d, d, 0, 0, d, d, d, d, c2);
        Shapes.blit(g, Shapes.logoGloss(d), x, y, d, d, 0, 0, d, d, d, d, 0xFFFFFFFF);
        // a soft drop under the mark keeps it legible on light accents
        Shapes.blit(g, Shapes.logoMark(d), x, y + Math.max(1, d / 28), d, d, 0, 0, d, d, d, d, 0x40000000);
        Shapes.blit(g, Shapes.logoMark(d), x, y, d, d, 0, 0, d, d, d, d, 0xFFFFFFFF);
    }

    /** Glass pane tinted with a color (selected items, active states). */
    static void glassTint(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color, float strength) {
        glass(g, x, y, w, h, r, alpha(color, Math.round(0x30 + 0x40 * strength)), 0.6f + 0.4f * strength);
    }

    /** Up/down chevron as an anti-aliased icon glyph. */
    static void chevron(GuiGraphicsExtractor g, int cx, int cy, int size, boolean up, int color) {
        icon(g, cx, cy, up ? Icons.CHEVRON_UP : Icons.CHEVRON_DOWN, color);
    }

    // ---------------------------------------------------------------- effects
    /** Soft radial glow (a single smooth gradient, no banding). */
    static void glow(GuiGraphicsExtractor g, int cx, int cy, int radius, int color, int layers, int alphaPerLayer) {
        if (!FANCY || radius <= 0) return;
        // One smooth radial gradient instead of stacked layers (no banding, one draw).
        int a = Math.min(90, Math.max(1, alphaPerLayer * Math.max(1, Math.min(layers, 4)) * 3 / 2));
        int d = radius * 2;
        Shapes.blit(g, Shapes.radial(), cx - radius, cy - radius, d, d, 0, 0, 128, 128, 128, 128, alpha(color, a));
    }

    /** Moving highlight: a horizontal streak centered at cx, clipped to [rx, rx+rw]. */
    static void shine(GuiGraphicsExtractor g, int rx, int rw, int y, int h, int cx, int half, int a) {
        shineC(g, rx, rw, y, h, cx, half, a, 0xFFFFFF);
    }

    /** Same as {@link #shine} but with a custom color. */
    static void shineC(GuiGraphicsExtractor g, int rx, int rw, int y, int h, int cx, int half, int a, int color) {
        if (!FANCY) return;
        int from = Math.max(rx, cx - half), to = Math.min(rx + rw, cx + half);
        if (to <= from || half <= 0) return;
        int leftAlpha = alpha(color, (int) (a * (1f - Math.abs(from - cx) / (float) half)));
        int rightAlpha = alpha(color, (int) (a * (1f - Math.abs(to - cx) / (float) half)));
        if (cx <= from || cx >= to) {
            horizontalGradient(g, from, y, to - from, h, leftAlpha, rightAlpha);
            return;
        }
        horizontalGradient(g, from, y, cx - from, h, leftAlpha, alpha(color, a));
        horizontalGradient(g, cx, y, to - cx, h, alpha(color, a), rightAlpha);
    }

    /** Vertical gradient (top to bottom). */
    static void vgrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        g.fillGradient(x, y, x + w, y + h, c1, c2);
    }

    /** Gradient streak rotated by angle (radians), starting at (x, y). Used for shooting stars. */
    static void streak(GuiGraphicsExtractor g, int x, int y, float angle, int len, int thick, int c1, int c2) {
        if (!FANCY || len < 1 || thick < 1) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate(angle);
        horizontalGradient(g, 0, 0, len, thick, c1, c2);
        g.pose().popMatrix();
    }

    /** Rainbow strip built from 8 gradient segments; scroll it by changing hue. */
    static void rainbow(GuiGraphicsExtractor g, int x, int y, int w, int h, float hue, float sat) {
        if (w <= 0 || h <= 0) return;
        if (!FANCY) { hgrad(g, x, y, w, h, hsl(hue, sat, 0.62f), hsl(hue + 120f, sat, 0.62f)); return; }
        final int n = FX >= 2 ? 8 : 4;
        final float step = 224f / n;
        for (int k = 0; k < n; k++) {
            int x0 = x + w * k / n, x1 = x + w * (k + 1) / n;
            hgrad(g, x0, y, x1 - x0, h, hsl(hue + k * step, sat, 0.62f), hsl(hue + (k + 1) * step, sat, 0.62f));
        }
    }

    /** HUD-style corner brackets around a rectangle. */
    static void brackets(GuiGraphicsExtractor g, int x, int y, int w, int h, int len, int t, int c) {
        if (!FANCY) return;
        fill(g, x, y, len, t, c);
        fill(g, x, y, t, len, c);
        fill(g, x + w - len, y, len, t, c);
        fill(g, x + w - t, y, t, len, c);
        fill(g, x, y + h - t, len, t, c);
        fill(g, x, y + h - len, t, len, c);
        fill(g, x + w - len, y + h - t, len, t, c);
        fill(g, x + w - t, y + h - len, t, len, c);
    }

    /** Single solid line drawn as one rotated rect (one fill instead of a dotted trail). */
    static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float len, int thick, int color) {
        if (len < 1f || (color >>> 24) == 0) return;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().rotate((float) Math.atan2(y1 - y0, x1 - x0));
        g.fill(0, 0, Math.round(len), thick, color);
        g.pose().popMatrix();
    }

    /** Dotted line made of small squares (kept for other callers; particles now use {@link #line}). */
    static void dotLine(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int step, int size, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        int n = Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy) / step));
        for (int i = 0; i <= n; i++) {
            fill(g, x0 + Math.round(dx * i / n), y0 + Math.round(dy * i / n), size, size, color);
        }
    }

    // ---------------------------------------------------------------- text
    // y arguments are the vertical CENTER of the text line.
    static void text(GuiGraphicsExtractor g, int x, int cy, String s, int color, boolean bold) {
        draw(g, x, cy, s, color, bold, null);
    }

    static void textR(GuiGraphicsExtractor g, int x, int cy, String s, int color, boolean bold) {
        draw(g, x, cy, s, color, bold, TextAlignment.RIGHT);
    }

    static void textC(GuiGraphicsExtractor g, int cx, int cy, String s, int color, boolean bold) {
        draw(g, cx, cy, s, color, bold, TextAlignment.CENTER);
    }

    private static void draw(GuiGraphicsExtractor g, int x, int cy, String s, int color, boolean bold, TextAlignment al) {
        Component c = styled(s, color, bold);
        // Vanilla draws the font baseline a fixed 7px below the y we pass, regardless of TTF size,
        // so large fonts end up too high. Compensate: put the visual middle of the caps on cy.
        int y = cy - Math.round(BASELINE) + Math.round(CAP_CENTER * 9f * S) + Math.round(TEXT_NUDGE * S);
        if (shadowed(color)) {
            int o = rimWidth();
            Component sh = styled(s, shadowOf(color), bold);
            if (al == null) g.textRenderer().accept(x + o, y + o, sh);
            else g.textRenderer().accept(al, x + o, y + o, sh);
        }
        if (al == null) g.textRenderer().accept(x, y, c);
        else g.textRenderer().accept(al, x, y, c);
    }

    /** Light text gets a shadow; dark text (on white knobs or bright chips) would only smear. */
    private static boolean shadowed(int color) {
        if (!TEXT_SHADOW) return false;
        int r = (color >> 16) & 255, gr = (color >> 8) & 255, b = color & 255;
        return r * 299 + gr * 587 + b * 114 > 128_000;
    }

    /** Shadow color: the text color at a quarter brightness, like vanilla's, so tinted text keeps its hue. */
    private static int shadowOf(int color) { return (color & 0xFCFCFC) >> 2; }

    private static Component styled(String s, int color, boolean bold) {
        int scale = Math.max(0, Math.min(S, FONTS.length) - 1);
        int rgb = color & 0xFFFFFF;
        TEXT_PROBE.text = s;
        TEXT_PROBE.color = rgb;
        TEXT_PROBE.bold = bold;
        TEXT_PROBE.scale = scale;
        Component cached = TEXT_CACHE.get(TEXT_PROBE);
        if (cached != null) return cached;
        Component created = Component.literal(s).withStyle(st -> st.withColor(rgb).withFont(FONTS[scale]).withBold(bold));
        TEXT_CACHE.put(new TextKey(s, rgb, bold, scale), created);
        return created;
    }

    // ---------------------------------------------------------------- icons
    /** Font Awesome glyph fonts, one per UI scale, rendered 1:1 like the text fonts. */
    private static final FontDescription[] ICON_FONTS = new FontDescription[4];
    static {
        for (int i = 0; i < ICON_FONTS.length; i++) {
            ICON_FONTS[i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath("anthrax", "fa_" + (i + 1)));
        }
    }
    private static final Map<TextKey, Component> ICON_CACHE = new HashMap<>();

    /** Draws a Font Awesome glyph (see {@link Icons}) centered on (cx, cy). */
    static void icon(GuiGraphicsExtractor g, int cx, int cy, String glyph, int color) {
        int scale = Math.max(0, Math.min(S, ICON_FONTS.length) - 1);
        int rgb = color & 0xFFFFFF;
        Component c = ICON_CACHE.computeIfAbsent(new TextKey(glyph, rgb, false, scale),
                k -> Component.literal(glyph).withStyle(st -> st.withColor(rgb).withFont(ICON_FONTS[scale])));
        // Glyph boxes are ~8*S tall with the baseline 7 px below y (see draw); center them like text.
        int y = cy - Math.round(BASELINE) + Math.round(0.42f * 8f * S);
        if (shadowed(color)) {
            int sh = shadowOf(color), o = rimWidth();
            Component shadow = ICON_CACHE.computeIfAbsent(new TextKey(glyph, sh, false, scale),
                    k -> Component.literal(glyph).withStyle(st -> st.withColor(sh).withFont(ICON_FONTS[scale])));
            g.textRenderer().accept(TextAlignment.CENTER, cx + o, y + o, shadow);
        }
        g.textRenderer().accept(TextAlignment.CENTER, cx, y, c);
    }

    /** Set by the screen every frame; used to measure text. */
    static Font measureFont;

    static int textWidth(String s, boolean bold) {
        if (measureFont == null) return 0;
        int scale = Math.max(0, Math.min(S, FONTS.length) - 1);
        WIDTH_PROBE.text = s;
        WIDTH_PROBE.bold = bold;
        WIDTH_PROBE.scale = scale;
        Integer cached = WIDTH_CACHE.get(WIDTH_PROBE);
        if (cached != null) return cached;
        int width = measureFont.width(styled(s, 0xFFFFFF, bold));
        WIDTH_CACHE.put(new WidthKey(s, bold, scale), width);
        return width;
    }

    /** Per-character hue gradient text (hue offsets by `spread` degrees per letter). */
    static void textRainbow(GuiGraphicsExtractor g, int x, int cy, String s, float hue, float spread, boolean bold) {
        if (FX < 2) { text(g, x, cy, s, hsl(hue, 0.95f, 0.68f), bold); return; }
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char character = s.charAt(i);
            String ch = character < ASCII_CHARACTERS.length ? ASCII_CHARACTERS[character] : String.valueOf(character);
            text(g, cx, cy, ch, hsl(hue + i * spread, 0.95f, 0.68f), bold);
            cx += textWidth(ch, bold);
        }
    }
}