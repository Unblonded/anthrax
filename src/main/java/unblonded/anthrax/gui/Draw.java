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
    /** Anti-aliased corners (MAX only). */
    static boolean AA = false;
    static final String[] FX_NAMES = {"LITE", "FX", "MAX"};
    static final String[] FX_LONG = {"Lite (no effects)", "Balanced", "Max quality"};

    static void setFx(int level) {
        FX = Math.floorMod(level, 3);
        FANCY = FX > 0;
        AA = FX >= 2;
    }

    /** Draw the item icons on module cards (item rendering is not free). */
    static boolean ICONS = true;

    static int S = 2;
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

    /** Lite mode caps corner radii so each rounded rect needs only a handful of fills. */
    private static int capR(int r) { return FX >= 2 ? r : Math.min(r, S * (FX == 1 ? 5 : 3)); }

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

    private static final int[][][] RUN_TABLE = new int[2][128][];
    private static final Map<Integer, int[]> FAST_RUNS = new HashMap<>();

    /** Corner profile for a radius as merged runs: {startRow, rowCount, solidInset, aaLevel} per run. */
    private static int[] runs(int r, boolean aa) {
        int[][] table = RUN_TABLE[aa ? 1 : 0];
        if (r < table.length) {
            int[] cached = table[r];
            if (cached == null) {
                cached = buildRuns(r, aa);
                table[r] = cached;
            }
            return cached;
        }
        return buildRuns(r, aa);
    }

    private static int[] buildRuns(int rr, boolean aa) {
        List<int[]> out = new ArrayList<>();
        int[] cur = null;
        for (int i = 0; i < rr; i++) {
            float dy = rr - i - 0.5f;
            float inset = rr - (float) Math.sqrt(rr * rr - dy * dy);
            int full = (int) Math.ceil(inset);
            float cov = full - inset;
            int q = (aa && full > 0 && cov > 0.12f) ? (cov < 0.4f ? 1 : cov < 0.75f ? 2 : 3) : 0;
            if (cur != null && cur[2] == full && cur[3] == q) {
                cur[1]++;
            } else {
                cur = new int[]{i, 1, full, q};
                out.add(cur);
            }
        }
        int[] flat = new int[out.size() * 4];
        for (int k = 0; k < out.size(); k++) System.arraycopy(out.get(k), 0, flat, k * 4, 4);
        return flat;
    }

    private static final float[] AA_LEVELS = {0f, 0.25f, 0.55f, 0.85f};

    /**
     * Rounded rectangle. Fancy: true radius with anti-aliased edges. Lite: capped radius, and opaque
     * full-corner shapes are drawn as a union of a few overlapping rects (one fill per corner band).
     */
    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c, boolean top, boolean bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(capR(r), Math.min(w, h) / 2));
        if (r == 0) { fill(g, x, y, w, h, c); return; }
        int a = c >>> 24;
        if (FX < 2 && a == 255 && top && bottom) {
            int[] lite = runs(r, false);
            for (int k = 0; k < lite.length; k += 4) {
                int start = lite[k], full = lite[k + 2];
                fill(g, x + full, y + start, w - 2 * full, h - 2 * start, c);
            }
            fill(g, x, y + r, w, h - 2 * r, c);
            return;
        }
        boolean aa = AA;
        fill(g, x, y + r, w, h - 2 * r, c);
        if (!top) fill(g, x, y, w, r, c);
        if (!bottom) fill(g, x, y + h - r, w, r, c);
        if (!top && !bottom) return;
        boolean opaque = a == 255;
        int[] rs = runs(r, aa);
        for (int k = 0; k < rs.length; k += 4) {
            int start = rs[k], n = rs[k + 1], full = rs[k + 2], q = rs[k + 3];
            int edge = (aa && q > 0) ? alpha(c, (int) (a * AA_LEVELS[q])) : 0;
            if (top) run(g, x, y + start, w, n, full, edge, c, opaque);
            if (bottom) run(g, x, y + h - start - n, w, n, full, edge, c, opaque);
        }
    }

    private static void run(GuiGraphicsExtractor g, int x, int yy, int w, int n, int full, int edge, int c, boolean opaque) {
        if (edge != 0) {
            if (opaque) {
                // one wide edge-colored fill; the solid fill below covers its middle
                fill(g, x + full - 1, yy, w - 2 * full + 2, n, edge);
            } else {
                fill(g, x + full - 1, yy, 1, n, edge);
                fill(g, x + w - full, yy, 1, n, edge);
            }
        }
        fill(g, x + full, yy, w - 2 * full, n, c);
    }

    /** Cheap rounded rect for glows/shadows: no anti-aliasing, corner drawn in a few bands. */
    static void roundFast(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        if (!FANCY || w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { fill(g, x, y, w, h, c); return; }
        if (FX < 2) {
            // cheap soft shape: one middle band + two inset caps (3 fills, no overlap so alpha stays even)
            int k = Math.max(1, r * 2 / 3), ins = Math.max(1, r / 3);
            fill(g, x, y + k, w, h - 2 * k, c);
            fill(g, x + ins, y, w - 2 * ins, k, c);
            fill(g, x + ins, y + h - k, w - 2 * ins, k, c);
            return;
        }
        fill(g, x, y + r, w, h - 2 * r, c);
        int[] bands = FAST_RUNS.computeIfAbsent(r, radius -> {
            int count = Math.min(radius, 5);
            int[] runs = new int[count * 3];
            for (int b = 0; b < count; b++) {
                int r0 = radius * b / count, r1 = radius * (b + 1) / count;
                float dy = radius - (r0 + r1) / 2f;
                int inset = Math.round(radius - (float) Math.sqrt(Math.max(0, radius * radius - dy * dy)));
                runs[b * 3] = r0;
                runs[b * 3 + 1] = r1;
                runs[b * 3 + 2] = inset;
            }
            return runs;
        });
        for (int i = 0; i < bands.length; i += 3) {
            int r0 = bands[i], r1 = bands[i + 1], inset = bands[i + 2];
            fill(g, x + inset, y + r0, w - 2 * inset, r1 - r0, c);
            fill(g, x + inset, y + h - r1, w - 2 * inset, r1 - r0, c);
        }
    }

    /** Rounded rect with a t-pixel border. Fill must be opaque. */
    static void roundBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int t, int border, int fill) {
        int ro = capR(r);
        round(g, x, y, w, h, ro, border);
        round(g, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, ro - t), fill);
    }

    static void circle(GuiGraphicsExtractor g, int cx, int cy, int radius, int c) {
        if (FX < 2 && (c >>> 24) == 255 && radius >= 2) {
            // octagon from two overlapping rects: 2 fills instead of a stack of corner runs
            int k = Math.max(1, Math.round(radius * 0.29f)), d = radius * 2;
            fill(g, cx - radius + k, cy - radius, d - 2 * k, d, c);
            fill(g, cx - radius, cy - radius + k, d, d - 2 * k, c);
            return;
        }
        round(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, c);
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

    /** Horizontal-gradient pill with rounded end caps: 3 draws (two caps + one gradient body). */
    static void pillGrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        if (FX < 2) { hgrad(g, x, y, w, h, c1, c2); return; }
        int r = h / 2;
        if (w <= h) { round(g, x, y, w, h, r, c1); return; }
        round(g, x, y, h, h, r, c1);
        round(g, x + w - h, y, h, h, r, c2);
        hgrad(g, x + r, y, w - h, h, lerp(c1, c2, r / (float) w), lerp(c1, c2, 1f - r / (float) w));
    }

    static void toggle(GuiGraphicsExtractor g, int x, int y, int w, int h, float on, int accent) {
        round(g, x, y, w, h, h / 2, lerp(0xFF2B2B3F, accent, on));
        int pad = Math.max(2, h / 6);
        int d = h - pad * 2;
        int kx = x + pad + Math.round((w - h) * on);
        round(g, kx, y + pad, d, d, d / 2, lerp(0xFFB5B8C8, 0xFFFFFFFF, on));
    }

    static void chevron(GuiGraphicsExtractor g, int cx, int cy, int size, boolean up, int color) {
        int t = Math.max(1, S);
        int len = Math.round(size * 1.4142f) + t / 2;
        int vy = up ? cy - size / 2 : cy + size / 2;
        float a1 = (float) (up ? 3 * Math.PI / 4 : -3 * Math.PI / 4);
        float a2 = (float) (up ? Math.PI / 4 : -Math.PI / 4);
        arm(g, cx, vy, a1, len, t, color);
        arm(g, cx, vy, a2, len, t, color);
    }

    private static void arm(GuiGraphicsExtractor g, int x, int y, float angle, int len, int t, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate(angle);
        g.fill(-t / 2, -t / 2, len, t - t / 2, color);
        g.pose().popMatrix();
    }

    // ---------------------------------------------------------------- effects
    /** Soft, low-cost glow with a gradual alpha falloff toward its edge. */
    static void glow(GuiGraphicsExtractor g, int cx, int cy, int radius, int color, int layers, int alphaPerLayer) {
        if (!FANCY) return;
        layers = Math.max(1, Math.min(layers, FX >= 2 ? 4 : 2));
        int maxAlpha = Math.min(36, alphaPerLayer * 2);
        for (int i = layers; i > 0; i--) {
            int rr = Math.max(1, radius * i / layers);
            int a = Math.max(1, maxAlpha * (layers - i + 1) / (layers * 2));
            roundFast(g, cx - rr, cy - rr, rr * 2, rr * 2, rr, alpha(color, a));
        }
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
        if (al == null) g.textRenderer().accept(x, y, c);
        else g.textRenderer().accept(al, x, y, c);
    }

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