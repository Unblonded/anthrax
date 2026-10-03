package unblonded.anthrax.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All drawing happens in real window pixels (the screen undoes Minecraft's GUI scale),
 * so shapes are pixel-exact and text is never magnified from a low-res atlas.
 * {@link #S} is our own integer UI scale; use {@link #u(float)} to convert design units to pixels.
 */
final class Draw {
    /** Set false to disable particles/glows if you ever need more FPS. */
    static boolean FANCY = true;

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

    private static final Map<Integer, int[]> RUNS = new HashMap<>();

    /** Corner profile for a radius as merged runs: {startRow, rowCount, solidInset, aaLevel} per run. */
    private static int[] runs(int r) {
        return RUNS.computeIfAbsent(r, rr -> {
            List<int[]> out = new ArrayList<>();
            int[] cur = null;
            for (int i = 0; i < rr; i++) {
                float dy = rr - i - 0.5f;
                float inset = rr - (float) Math.sqrt(rr * rr - dy * dy);
                int full = (int) Math.ceil(inset);
                float cov = full - inset;
                int q = (full > 0 && cov > 0.12f) ? (cov < 0.4f ? 1 : cov < 0.75f ? 2 : 3) : 0;
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
        });
    }

    /** Anti-aliased rounded rectangle (few fills: corner rows are merged). */
    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c, boolean top, boolean bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { fill(g, x, y, w, h, c); return; }
        fill(g, x, y + r, w, h - 2 * r, c);
        if (!top) fill(g, x, y, w, r, c);
        if (!bottom) fill(g, x, y + h - r, w, r, c);
        if (!top && !bottom) return;
        int a = c >>> 24;
        final float[] lv = {0f, 0.25f, 0.55f, 0.85f};
        int[] rs = runs(r);
        for (int k = 0; k < rs.length; k += 4) {
            int start = rs[k], n = rs[k + 1], full = rs[k + 2], q = rs[k + 3];
            int edge = q > 0 ? alpha(c, (int) (a * lv[q])) : 0;
            if (top) run(g, x, y + start, w, n, full, edge, c);
            if (bottom) run(g, x, y + h - start - n, w, n, full, edge, c);
        }
    }

    private static void run(GuiGraphicsExtractor g, int x, int yy, int w, int n, int full, int edge, int c) {
        fill(g, x + full, yy, w - 2 * full, n, c);
        if (edge != 0) {
            fill(g, x + full - 1, yy, 1, n, edge);
            fill(g, x + w - full, yy, 1, n, edge);
        }
    }

    /** Cheap rounded rect for glows/shadows: no anti-aliasing, corner drawn in a few bands. */
    static void roundFast(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { fill(g, x, y, w, h, c); return; }
        fill(g, x, y + r, w, h - 2 * r, c);
        int bands = Math.min(r, 5);
        for (int b = 0; b < bands; b++) {
            int r0 = r * b / bands, r1 = r * (b + 1) / bands;
            if (r1 <= r0) continue;
            float dy = r - (r0 + r1) / 2f;
            int in = Math.round(r - (float) Math.sqrt(Math.max(0, r * r - dy * dy)));
            fill(g, x + in, y + r0, w - 2 * in, r1 - r0, c);
            fill(g, x + in, y + h - r1, w - 2 * in, r1 - r0, c);
        }
    }

    /** Rounded rect with a t-pixel border. Fill must be opaque. */
    static void roundBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int t, int border, int fill) {
        round(g, x, y, w, h, r, border);
        round(g, x + t, y + t, w - 2 * t, h - 2 * t, r - t, fill);
    }

    static void circle(GuiGraphicsExtractor g, int cx, int cy, int radius, int c) {
        round(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, c);
    }

    static void hgrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        int step = Math.max(2, w / 36);
        for (int i = 0; i < w; i += step) {
            int cw = Math.min(step, w - i);
            fill(g, x + i, y, cw, h, lerp(c1, c2, (i + cw / 2f) / w));
        }
    }

    /** Horizontal-gradient pill with rounded end caps. */
    static void pillGrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        int r = h / 2;
        int i = 0;
        while (i < w) {
            boolean cap = i < r || i >= w - r;
            int cw = cap ? 2 : Math.max(2, w / 28);
            if (!cap) cw = Math.min(cw, w - r - i);
            cw = Math.max(1, Math.min(cw, w - i));
            float cx = i + cw / 2f;
            float d = cx < r ? r - cx : (cx > w - r ? cx - (w - r) : 0);
            int ins = d > 0 ? Math.round(r - (float) Math.sqrt(Math.max(0, r * r - d * d))) : 0;
            fill(g, x + i, y + ins, cw, h - 2 * ins, lerp(c1, c2, cx / w));
            i += cw;
        }
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
        for (int i = 0; i <= size; i++) {
            int yy = up ? cy + size / 2 - i : cy - size / 2 + i;
            fill(g, cx - size + i, yy, t, t, color);
            fill(g, cx + size - i - t + 1, yy, t, t, color);
        }
    }

    // ---------------------------------------------------------------- effects
    /** Soft glow made of stacked translucent circles. */
    static void glow(GuiGraphicsExtractor g, int cx, int cy, int radius, int color, int layers, int alphaPerLayer) {
        if (!FANCY) return;
        layers = Math.min(layers, 3);
        int a = alphaPerLayer * 2;   // fewer layers, so each one is a bit stronger
        for (int i = 0; i < layers; i++) {
            int rr = radius - radius * i / layers;
            roundFast(g, cx - rr, cy - rr, rr * 2, rr * 2, rr, alpha(color, a));
        }
    }

    /** Moving highlight: a horizontal streak centered at cx, clipped to [rx, rx+rw]. */
    static void shine(GuiGraphicsExtractor g, int rx, int rw, int y, int h, int cx, int half, int a) {
        int from = Math.max(rx, cx - half), to = Math.min(rx + rw, cx + half);
        int step = Math.max(2, half / 6);
        for (int px = from; px < to; px += step) {
            float t = 1f - Math.abs(px + step / 2f - cx) / half;
            if (t <= 0) continue;
            fill(g, px, y, Math.min(step, to - px), h, alpha(0xFFFFFF, (int) (a * t)));
        }
    }

    /** Dotted line made of small squares. */
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
        FontDescription f = FONTS[Math.max(0, Math.min(S, FONTS.length) - 1)];
        return Component.literal(s).withStyle(st -> st.withColor(color & 0xFFFFFF).withFont(f).withBold(bold));
    }

    /** Set by the screen every frame; used to measure text. */
    static Font measureFont;

    static int textWidth(String s, boolean bold) {
        return measureFont == null ? 0 : measureFont.width(styled(s, 0xFFFFFF, bold));
    }

    /** Per-character hue gradient text (hue offsets by `spread` degrees per letter). */
    static void textRainbow(GuiGraphicsExtractor g, int x, int cy, String s, float hue, float spread, boolean bold) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            text(g, cx, cy, ch, hsl(hue + i * spread, 0.95f, 0.68f), bold);
            cx += textWidth(ch, bold);
        }
    }
}