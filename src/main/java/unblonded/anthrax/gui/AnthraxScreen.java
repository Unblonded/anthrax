package unblonded.anthrax.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import unblonded.anthrax.modules.GuiModule;
import unblonded.anthrax.modules.ModuleConfig;
import unblonded.anthrax.modules.ModuleRegistry;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.FontSizeModule;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntConsumer;
import org.lwjgl.glfw.GLFW;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Modular client GUI. Everything inside the content area is generated from {@link ModuleRegistry};
 * to add a module or setting you never touch this file.
 */
public class AnthraxScreen extends Screen {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private interface Drag { void to(int mouseX, int mouseY); }

    private static final class Hit {
        int x, y, w, h;
        int clipX, clipY, clipW, clipH;
        boolean clipped;
        IntConsumer press;
        Drag drag;

        void set(int x, int y, int w, int h, boolean clipped, int clipX, int clipY, int clipW, int clipH,
                 IntConsumer press, Drag drag) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.clipped = clipped;
            this.clipX = clipX;
            this.clipY = clipY;
            this.clipW = clipW;
            this.clipH = clipH;
            this.press = press;
            this.drag = drag;
        }

        boolean contains(int px, int py) { return inside(px, py, x, y, w, h); }
    }

    private record Toast(String title, String msg, boolean err, long born) {}
    /** Mutable key so cache hits can be looked up with a reusable probe (no allocation per call). */
    private static final class FitKey {
        String value;
        int width, scale;
        boolean bold;

        FitKey() {}

        FitKey(String value, int width, boolean bold, int scale) {
            this.value = value;
            this.width = width;
            this.bold = bold;
            this.scale = scale;
        }

        @Override public int hashCode() {
            return 31 * (31 * (31 * value.hashCode() + width) + Boolean.hashCode(bold)) + scale;
        }

        @Override public boolean equals(Object o) {
            return o instanceof FitKey k && width == k.width && scale == k.scale && bold == k.bold
                    && value.equals(k.value);
        }
    }
    private static final FitKey FIT_PROBE = new FitKey();

    private static final class CardState {
        boolean open;
        float expand, hover, enable;
        String initials;
        ItemStack icon;
        boolean iconInit;
    }

    private static final class Particle { float x, y, vx, vy, ph, sz; }
    private static final class WindowRect {
        int x, y, w, h;
        void set(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean contains(int px, int py) { return inside(px, py, x, y, w, h); }
    }

    private final List<Hit> hits = new ArrayList<>();
    private int hitCount;
    private final Map<GuiModule, CardState> states = new IdentityHashMap<>();
    private static final Map<String, ItemStack> MODULE_ICONS = new HashMap<>();
    private static final Map<FitKey, String> FIT_TEXT_CACHE = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<FitKey, String> eldest) { return size() > 2048; }
    };
    private final List<Toast> toasts = new ArrayList<>();
    private int previousModuleCount = -1;
    private String moduleCountText;

    // cached module/category data (rebuilt at most every 500 ms instead of several times per frame)
    private List<String> cats = List.of();
    private final List<List<GuiModule>> catMods = new ArrayList<>();
    private String[] catCountText = new String[0];
    private String[] catKeys = new String[0];
    private long catRefresh;
    private int lastActive = -1;
    private long activeStamp = -1000L;
    private int activeCount, totalCount, lastTotal = -1;
    private float activeBar;
    private String activeText = "0 ACTIVE";

    private int category = -1;
    private float scroll, scrollTarget, contentHeight;
    private float tabY = -1;
    private Drag activeDrag;
    private Setting.Text focusedText;
    private boolean selectAllText;
    private final Map<String, Float> detachedScroll = new java.util.HashMap<>();
    private final Map<String, Float> detachedScrollTarget = new java.util.HashMap<>();
    private final Map<String, WindowRect> detachedRects = new java.util.HashMap<>();
    private final Map<String, int[]> detachedCardMetrics = new java.util.HashMap<>();

    private float gs = 1f;
    private float uiOverride = 1f;
    private int mx, my;                    // mouse in real pixels
    private int vx, vy, vw, vh;            // scrollable viewport (real pixels)
    private long lastNs = System.nanoTime();
    private final long openNs = System.nanoTime();
    private long catNs = openNs;
    private final long startMs = System.currentTimeMillis();

    private int frames, fps;
    private String fpsText = "FPS 0";
    private long costEmaNs;
    private String costText = "GUI 0.00ms";
    private long fpsStamp = System.currentTimeMillis();
    private String clockText = LocalTime.now().format(CLOCK);
    private long clockStamp = fpsStamp;

    private Particle[] particles;
    private int partW, partH;

    // shooting star
    private final Random fxRnd = new Random(11);
    private long starStart = -1, nextStar = 1200;
    private float starX, starY, starAng;

    // mouse trail (ring buffer, one entry per frame)
    private final int[] trX = new int[14], trY = new int[14];
    private int trHead;
    private boolean trInit;

    public AnthraxScreen() {
        super(Component.literal("Anthrax"));
    }

    // =====================================================================
    //  frame
    // =====================================================================
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Intentionally NOT calling super.extractRenderState(...): it runs vanilla's blur/dim
        // background pass, and this screen paints its own backdrop and has no vanilla widgets.

        Draw.measureFont = this.font;
        // Keep the virtual canvas tied to the window scale. Apply the module's
        // font/UI override only in the final transform so it doesn't cancel out
        // against Draw.S, which is calculated from the virtual canvas size.
        gs = (float) minecraft.getWindow().getGuiScale();
        uiOverride = FontSizeModule.scale();
        int pw = Math.round(width * gs), ph = Math.round(height * gs);
        mx = Math.round(mouseX * gs / uiOverride);
        my = Math.round(mouseY * gs / uiOverride);

        long nowNs = System.nanoTime();
        float dt = Math.min(0.05f, (nowNs - lastNs) / 1_000_000_000f);
        lastNs = nowNs;

        frames++;
        long wallMs = System.currentTimeMillis();
        if (wallMs - fpsStamp >= 1000) {
            fps = Math.round(frames * 1000f / (wallMs - fpsStamp));
            fpsText = "FPS " + fps;
            int c = (int) (costEmaNs / 10_000L);
            costText = "GUI " + (c / 100) + "." + (c % 100 < 10 ? "0" : "") + (c % 100) + "ms";
            frames = 0;
            fpsStamp = wallMs;
        }
        if (wallMs - clockStamp >= 1000) {
            clockText = LocalTime.now().format(CLOCK);
            clockStamp = wallMs;
        }

        refreshModules(wallMs);

        g.pose().pushMatrix();
        float uiScale = uiOverride / gs;
        g.pose().scale(uiScale, uiScale);
        long costStart = System.nanoTime();
        try {
            drawAll(g, pw, ph, dt, wallMs, nowNs);
        } finally {
            g.pose().popMatrix();
            costEmaNs += ((System.nanoTime() - costStart) - costEmaNs) / 16L;
        }
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float i = 1f - t;
        return 1f - i * i * i;
    }

    /** Rebuilds the cached category/module lists (at most every 500 ms). */
    private void refreshModules(long wallMs) {
        if (!cats.isEmpty() && wallMs - catRefresh < 500) return;
        catRefresh = wallMs;
        cats = new ArrayList<>(ModuleRegistry.categories());
        if (category < 0) {
            category = cats.indexOf(GuiPreferences.lastCategory());
            if (category < 0) category = 0;
        }
        catMods.clear();
        catCountText = new String[cats.size()];
        catKeys = new String[cats.size()];
        for (int i = 0; i < cats.size(); i++) {
            List<GuiModule> l = new ArrayList<>(ModuleRegistry.inCategory(cats.get(i)));
            catMods.add(l);
            catCountText[i] = String.valueOf(l.size());
            catKeys[i] = "category:" + cats.get(i);
        }
    }

    private void drawAll(GuiGraphicsExtractor g, int pw, int ph, float dt, long wallMs, long nowNs) {
        hitCount = 0;
        long ms = wallMs - startMs;
        Theme.tick(wallMs);
        Draw.S = Math.max(1, Math.min(4, Math.min(Math.round(ph / 720f), Math.min(pw / 600, ph / 400))));
        int accent = Theme.accent(), accent2 = Theme.accent2();

        float introT = (nowNs - openNs) / 1_000_000_000f;
        float open = easeOut(introT / 0.4f);

        // ---- backdrop: dim, vignette, drifting color blobs, particle network, shooting star
        g.fill(0, 0, pw, ph, alpha(0x050509, (int) (0xC8 * open)));
        if (FANCY) {
            int vignette = u(24);
            int vignetteOuter = alpha(0x000000, (int) (0x48 * open));
            int vignetteInner = alpha(0x000000, (int) (0x09 * open));
            g.fillGradient(0, 0, pw, vignette, vignetteOuter, vignetteInner);
            g.fillGradient(0, ph - vignette, pw, ph, vignetteInner, vignetteOuter);
            hgrad(g, 0, 0, vignette, ph, vignetteOuter, vignetteInner);
            hgrad(g, pw - vignette, 0, vignette, ph, vignetteInner, vignetteOuter);
        }
        if (FANCY) {
            drawBlobs(g, pw, ph, ms, open, accent, accent2);
            drawParticles(g, pw, ph, dt, open, accent, accent2, ms);
            drawStar(g, pw, ph, ms, open, accent2);
        }

        // ---- window geometry
        if (GuiPreferences.separateCategoryWindows()) {
            drawDetachedWindows(g, pw, ph, dt, ms, nowNs, open, accent, accent2);
            drawToasts(g, pw, ph, accent);
            drawTrail(g, accent, accent2);
            return;
        }

        int ww = u(580), wh = u(380);
        int maxWX = Math.max(0, pw - ww), maxWY = Math.max(0, ph - wh);
        double[] storedMain = GuiPreferences.windowPosition("__main");
        if (storedMain == null) storedMain = new double[]{.5, .5};
        int wx = Math.round((float) (storedMain[0] * maxWX));
        int wy = Math.round((float) (storedMain[1] * maxWY));
        int introOffset = Math.round((1f - open) * u(30));
        wy += introOffset;
        int r = u(14), t = S;
        int headerH = u(44), sideW = u(128), footH = u(26);
        int ix = wx + t, iw = ww - 2 * t;
        int footTop = wy + wh - t - footH;
        int bodyTop = wy + headerH;

        // shadow + pulsing accent glow
        for (int i = 2; i >= 1; i--) {
            int e = u(i * 5);
            roundFast(g, wx - e, wy - e + u(5), ww + 2 * e, wh + 2 * e, r + e, alpha(0x000000, (int) (0x14 * open)));
        }
        float pulse = 0.5f + 0.5f * (float) Math.sin(ms / 700.0);
        if (FANCY) {
            for (int i = 2; i >= 1; i--) {
                int e = u(i * 4);
                roundFast(g, wx - e, wy - e, ww + 2 * e, wh + 2 * e, r + e, alpha(accent, (int) ((6 + 6 * pulse) * open)));
            }
        }

        // HUD corner brackets floating just outside the window
        int bk = u(7);
        brackets(g, wx - bk, wy - bk, ww + 2 * bk, wh + 2 * bk, u(18), S,
                alpha(accent, (int) ((0x60 + 0x70 * pulse) * open)));

        roundBorder(g, wx, wy, ww, wh, r, t, lerp(Theme.LINE, accent, 0.35f + 0.2f * pulse), Theme.BG);

        round(g, ix, wy + t, iw, headerH - t, r - t, Theme.HEADER, true, false);
        round(g, ix, footTop, iw, footH, r - t, Theme.HEADER, false, true);
        fill(g, ix, footTop, iw, t, Theme.LINE);
        fill(g, ix, bodyTop, sideW - t, footTop - bodyTop, Theme.SIDE);
        fill(g, ix + sideW - t, bodyTop, t, footTop - bodyTop, Theme.LINE);

        // gradient line under header + travelling shine
        int lineH = Math.max(1, S);
        hgrad(g, ix, bodyTop - lineH, iw / 2, lineH, accent, accent2);
        hgrad(g, ix + iw / 2, bodyTop - lineH, iw - iw / 2, lineH, accent2, Theme.HEADER);
        int shineX = ix + Math.round((((ms / 1900f) % 1.4f) - 0.2f) * iw);
        shine(g, ix, iw, bodyTop - lineH, lineH, shineX, u(90), 0xD0);

        // neon edges: scrolling rainbow on top, accent gradient + opposing shine on bottom
        int edgeX = wx + r, edgeW = ww - 2 * r;
        rainbow(g, edgeX, wy, edgeW, t, Theme.hue, 0.9f);
        hgrad(g, edgeX, wy + wh - t, edgeW / 2, t, accent2, accent);
        hgrad(g, edgeX + edgeW / 2, wy + wh - t, edgeW - edgeW / 2, t, accent, accent2);
        if (FANCY) {
            int bx = edgeX + edgeW - Math.round((((ms / 2300f) % 1.4f) - 0.2f) * edgeW);
            shine(g, edgeX, edgeW, wy + wh - t, t, bx, u(110), 0xB0);
        }

        // boot flash racing across the top edge when the screen opens
        if (introT < 0.9f) {
            float p = easeOut(introT / 0.7f);
            int fa = (int) (0xFF * (1f - introT / 0.9f));
            shineC(g, edgeX, edgeW, wy, t * 2, edgeX + Math.round(p * edgeW), u(150), fa, 0xFFFFFF);
        }

        final int dragOffsetX = mx - wx, dragOffsetY = my - wy;
        hit(wx + u(8), wy + u(4), u(150), headerH - u(8), false, null, (px, py) -> {
            int nx = Math.max(0, Math.min(maxWX, px - dragOffsetX));
            int ny = Math.max(0, Math.min(maxWY, py - dragOffsetY - introOffset));
            GuiPreferences.setWindowPosition("__main", maxWX == 0 ? 0 : nx / (double) maxWX,
                    maxWY == 0 ? 0 : ny / (double) maxWY);
        });
        drawHeader(g, ix, iw, wy, headerH, accent, accent2, ms);
        drawSidebar(g, ix, bodyTop, footTop, sideW, accent, accent2, dt, ms);
        drawContent(g, ix, iw, bodyTop, footTop, sideW, accent, accent2, dt, ms, nowNs);
        drawFooter(g, ix, iw, footTop, footH);

        // slow holographic scan sweep: fades out near the window edges instead of using a scissor
        if (FANCY) {
            float sp = (ms % 5200L) / 5200f;
            int bandH = u(70);
            int sTop = wy + u(6), sBottom = wy + wh - u(6);
            int by = sTop + Math.round(sp * (sBottom - sTop + bandH)) - bandH;
            int mid = by + bandH / 2;
            float edge = Math.min(mid - sTop, sBottom - mid) / (float) (bandH / 2);
            int pa = (int) (0x16 * Math.min(1f, Math.max(0f, edge)));
            if (pa > 0) {
                g.fillGradient(wx + u(6), by, wx + ww - u(6), mid, alpha(accent, 0), alpha(accent, pa));
                g.fillGradient(wx + u(6), mid, wx + ww - u(6), by + bandH, alpha(accent, pa), alpha(accent, 0));
            }
        }

        drawToasts(g, pw, ph, accent);
        drawTrail(g, accent, accent2);
    }

    // =====================================================================
    //  backdrop effects
    // =====================================================================
    /** Two big soft color blobs drifting behind the window. */
    private void drawBlobs(GuiGraphicsExtractor g, int pw, int ph, long ms, float open, int accent, int accent2) {
        if (open < 0.05f) return;
        int a = (int) (16 * open);
        glow(g, Math.round(pw * (0.22f + 0.06f * (float) Math.sin(ms / 4700.0))),
                Math.round(ph * (0.30f + 0.08f * (float) Math.cos(ms / 3900.0))), u(230), accent, 3, a);
        glow(g, Math.round(pw * (0.80f + 0.05f * (float) Math.cos(ms / 5300.0))),
                Math.round(ph * (0.72f + 0.08f * (float) Math.sin(ms / 4100.0))), u(230), accent2, 3, a);
    }

    private void drawParticles(GuiGraphicsExtractor g, int pw, int ph, float dt, float open, int accent, int accent2, long ms) {
        int pc = FX >= 2 ? 16 : 10;
        if (particles == null || particles.length != pc || partW != pw || partH != ph) {
            Random rnd = new Random(7);
            particles = new Particle[pc];
            for (int i = 0; i < particles.length; i++) {
                Particle p = new Particle();
                p.x = rnd.nextFloat() * pw;
                p.y = rnd.nextFloat() * ph;
                p.vx = (rnd.nextFloat() - 0.5f) * u(22);
                p.vy = (rnd.nextFloat() - 0.5f) * u(22);
                p.ph = rnd.nextFloat() * 6.2832f;
                p.sz = rnd.nextFloat();
                particles[i] = p;
            }
            partW = pw;
            partH = ph;
        }
        int link = u(90), mouseLink = u(110);
        float linkSq = (float) link * link, mouseSq = (float) mouseLink * mouseLink;
        for (Particle p : particles) {
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.x < 0 || p.x > pw) p.vx = -p.vx;
            if (p.y < 0 || p.y > ph) p.vy = -p.vy;
            float tw = 0.65f + 0.35f * (float) Math.sin(ms / 420.0 + p.ph);
            int dotA = alpha(accent2, (int) (0xB0 * open * tw));
            boolean big = p.sz > 0.8f;
            int ds = S + 1 + (big ? S : 0);
            if (big && FX >= 2) glow(g, (int) p.x, (int) p.y, u(7), accent2, 2, (int) (10 * open));
            fill(g, (int) p.x - ds / 2, (int) p.y - ds / 2, ds, ds, dotA);
        }
        for (int i = 0; i < particles.length; i++) {
            Particle a = particles[i];
            for (int j = i + 1; j < particles.length; j++) {
                Particle b = particles[j];
                float dx = a.x - b.x, dy = a.y - b.y;
                float d2 = dx * dx + dy * dy;
                if (d2 < linkSq) {
                    float d = (float) Math.sqrt(d2);
                    line(g, (int) a.x, (int) a.y, (int) b.x, (int) b.y, d, S,
                            alpha(accent, (int) ((1 - d / link) * 0x48 * open)));
                }
            }
            float dx = a.x - mx, dy = a.y - my;
            float d2 = dx * dx + dy * dy;
            if (d2 < mouseSq) {
                float d = (float) Math.sqrt(d2);
                line(g, (int) a.x, (int) a.y, mx, my, d, S,
                        alpha(accent2, (int) ((1 - d / mouseLink) * 0x90 * open)));
            }
        }
    }

    /** An occasional shooting star streaking across the backdrop. */
    private void drawStar(GuiGraphicsExtractor g, int pw, int ph, long ms, float open, int accent2) {
        if (starStart < 0) {
            if (ms < nextStar) return;
            starStart = ms;
            starX = pw * (0.05f + 0.7f * fxRnd.nextFloat());
            starY = ph * (0.02f + 0.35f * fxRnd.nextFloat());
            starAng = 0.45f + 0.35f * fxRnd.nextFloat();
        }
        float p = (ms - starStart) / 750f;
        if (p >= 1f) {
            starStart = -1;
            nextStar = ms + 2200 + fxRnd.nextInt(3500);
            return;
        }
        float cos = (float) Math.cos(starAng), sin = (float) Math.sin(starAng);
        float dist = u(340) * p;
        int hx = Math.round(starX + cos * dist), hy = Math.round(starY + sin * dist);
        float fade = (float) Math.sin(Math.PI * p) * open;
        int tail = u(80);
        streak(g, Math.round(hx - cos * tail), Math.round(hy - sin * tail), starAng, tail, S,
                alpha(0xFFFFFF, 0), alpha(0xFFFFFF, (int) (0xE0 * fade)));
        glow(g, hx, hy, u(9), accent2, 2, (int) (12 * fade));
        fill(g, hx - S, hy - S, S * 2, S * 2, alpha(0xFFFFFF, (int) (0xFF * fade)));
    }

    /** Glowing sparkle trail following the mouse. */
    private void drawTrail(GuiGraphicsExtractor g, int accent, int accent2) {
        if (!FANCY) return;
        int n = FX >= 2 ? trX.length : 8;
        if (!trInit) {
            Arrays.fill(trX, mx);
            Arrays.fill(trY, my);
            trInit = true;
        }
        trHead = (trHead + 1) % n;
        trX[trHead] = mx;
        trY[trHead] = my;
        for (int k = 0; k < n; k++) {
            int idx = (trHead - k + n) % n;
            float f = 1f - k / (float) n;
            int sz = Math.max(S, Math.round(S * 2 * f));
            fill(g, trX[idx] - sz / 2, trY[idx] - sz / 2, sz, sz,
                    alpha(lerp(accent2, accent, k / (float) n), (int) (0xB0 * f * f)));
        }
    }

    // =====================================================================
    //  header
    // =====================================================================
    private void drawHeader(GuiGraphicsExtractor g, int ix, int iw, int wy, int headerH, int accent, int accent2, long ms) {
        int cy = wy + headerH / 2;

        int mark = u(26);
        int mcx = ix + u(14) + mark / 2;
        glow(g, mcx, cy, u(22), accent, 4, 10 + (int) (8 * Math.sin(ms / 500.0)));
        round(g, ix + u(14), cy - mark / 2, mark, mark, u(8), accent);
        textC(g, mcx, cy, "A", Theme.BG, true);

        // comet orbiting the logo
        if (FANCY) {
            double oa = ms / 650.0;
            int orb = u(19);
            for (int k = 0; k < 5; k++) {
                double a = oa - k * 0.25;
                int ox = mcx + (int) Math.round(Math.cos(a) * orb);
                int oy = cy + (int) Math.round(Math.sin(a) * orb);
                int sz = k == 0 ? S + 1 : S;
                fill(g, ox - sz / 2, oy - sz / 2, sz, sz, alpha(0xFFFFFF, 0xFF - k * 50));
            }
        }

        textRainbow(g, ix + u(52), cy - u(7), "ANTHRAX", Theme.hue, 22f, true);
        text(g, ix + u(52), cy + u(8), "combat client", Theme.MUTED, false);
        if ((ms / 500L) % 2 == 0) {
            text(g, ix + u(52) + textWidth("combat client", false) + S, cy + u(8), "_", accent, false);
        }

        int fpsCol = fps >= 60 ? Theme.OK : fps >= 30 ? 0xFFE6C84A : Theme.DANGER;
        chip(g, ix + u(172), cy, u(92), "26.2 / FABRIC", Theme.MUTED);
        chip(g, ix + u(272), cy, u(64), clockText, Theme.MUTED);
        chip(g, ix + u(344), cy, u(56), fpsText, fpsCol);

        int bh = u(22);
        int closeX = ix + iw - u(14) - bh;
        int themeW = u(88);
        int themeX = closeX - u(8) - themeW;
        int fxW = u(32);
        int fxX = themeX - u(6) - fxW;

        button(g, fxX, cy - bh / 2, fxW, bh, FX_NAMES[FX], accent, () -> {
            Draw.setFx(Draw.FX + 1);
            toast("EFFECTS", FX_LONG[Draw.FX], false);
        });
        circle(g, fxX + fxW - u(5), cy - bh / 2 + u(5), u(2), FX == 2 ? accent : FX == 1 ? Theme.OK : Theme.DIM);

        button(g, themeX, cy - bh / 2, themeW, bh, Theme.NAMES[Theme.idx], accent, () -> {
            Theme.next();
            toast("THEME", Theme.NAMES[Theme.idx], false);
        });
        button(g, closeX, cy - bh / 2, bh, bh, "X", Theme.DANGER, this::onClose);
    }

    private void chip(GuiGraphicsExtractor g, int x, int cy, int w, String s, int textCol) {
        int h = u(18);
        roundBorder(g, x, cy - h / 2, w, h, h / 2, S, Theme.LINE, 0xFF151521);
        textC(g, x + w / 2, cy, s, textCol, false);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int hoverCol, Runnable action) {
        boolean hov = inside(mx, my, x, y, w, h);
        if (hov) glow(g, x + w / 2, y + h / 2, w / 2 + u(6), hoverCol, 3, 8);
        roundBorder(g, x, y, w, h, h / 2, S,
                lerp(Theme.LINE, hoverCol, hov ? 0.85f : 0f),
                lerp(0xFF161622, hoverCol, hov ? 0.16f : 0f));
        textC(g, x + w / 2, y + h / 2, label, lerp(Theme.TEXT, hoverCol, hov ? 1f : 0f), false);
        hit(x, y, w, h, false, b -> action.run(), null);
    }

    // =====================================================================
    //  sidebar
    // =====================================================================
    private void drawSidebar(GuiGraphicsExtractor g, int ix, int top, int bottom, int sideW, int accent, int accent2, float dt, long ms) {
        category = Math.max(0, Math.min(category, Math.max(0, cats.size() - 1)));

        text(g, ix + u(16), top + u(16), "CATEGORIES", Theme.DIM, false);

        int tabH = u(32), gap = u(4);
        int x = ix + u(10), w = sideW - u(20) - S;
        int y0 = top + u(34);

        // sliding highlight (with soft glow)
        float target = y0 + category * (tabH + gap);
        if (tabY < 0) tabY = target;
        tabY += (target - tabY) * Math.min(1f, dt * 16f);
        int ty = Math.round(tabY);
        if (FANCY) roundFast(g, x - u(2), ty - u(2), w + u(4), tabH + u(4), u(10), alpha(accent, 16));
        round(g, x, ty, w, tabH, u(8), lerp(Theme.SIDE, accent, 0.16f));
        vgrad(g, x, ty + u(8), u(3), tabH - u(16), accent, accent2);

        for (int i = 0; i < cats.size(); i++) {
            int y = y0 + i * (tabH + gap);
            boolean sel = i == category;
            boolean hov = inside(mx, my, x, y, w, tabH);
            if (!sel && hov) round(g, x, y, w, tabH, u(8), lerp(Theme.SIDE, accent, 0.07f));

            int tx = x + u(14) + (hov && !sel ? u(2) : 0);
            text(g, tx, y + tabH / 2, cats.get(i), sel ? Theme.TEXT : Theme.MUTED, sel);
            textR(g, x + w - u(10), y + tabH / 2, catCountText[i], sel ? accent : Theme.DIM, false);

            final int idx = i;
            hit(x, y, w, tabH, false, b -> {
                if (category != idx) catNs = System.nanoTime();
                category = idx;
                GuiPreferences.setLastCategory(cats.get(idx));
                scrollTarget = 0;
                scroll = 0;
            }, null);
        }

        // status
        if (ms - activeStamp >= 200L) {
            activeStamp = ms;
            int count = 0, all = 0;
            for (GuiModule module : ModuleRegistry.all()) {
                all++;
                if (module.isEnabled()) count++;
            }
            activeCount = count;
            totalCount = all;
        }
        int active = activeCount, total = totalCount;
        if (active != lastActive || total != lastTotal) {
            lastActive = active;
            lastTotal = total;
            activeText = active + " / " + total + " ACTIVE";
        }
        int cy = bottom - u(22);
        int cx = ix + u(20);
        if (active > 0) {
            if (FANCY) {
                // radar ping: a disc that expands and fades out, then repeats
                float ph = (ms % 1500L) / 1500f;
                circle(g, cx, cy, u(3) + Math.round(u(6) * ph), alpha(Theme.OK, Math.round(70 * (1f - ph))));
            } else {
                circle(g, cx, cy, u(6), alpha(Theme.OK, 40));
            }
            circle(g, cx, cy, u(3), Theme.OK);
        } else {
            circle(g, cx, cy, u(3), Theme.DIM);
        }

        // power bar: how much of the module list is on, with a glowing tip and a moving shine
        int pbx = ix + u(14), pbw = sideW - u(30) - S, pbh = u(4), pby = cy + u(10);
        float ratio = total > 0 ? active / (float) total : 0f;
        activeBar += (ratio - activeBar) * Math.min(1f, dt * 8f);
        if (Math.abs(ratio - activeBar) < 0.002f) activeBar = ratio;
        round(g, pbx, pby, pbw, pbh, pbh / 2, 0xFF26263A);
        int pfw = Math.round(pbw * activeBar);
        if (pfw > 0) {
            pfw = Math.max(pbh, pfw);
            pillGrad(g, pbx, pby, pfw, pbh, accent, accent2);
            shine(g, pbx, pfw, pby, pbh, pbx + Math.round((((ms / 1100f) % 1.5f) - 0.25f) * pfw), u(18), 0x90);
            glow(g, pbx + pfw, pby + pbh / 2, u(8), accent2, 2, 14);
        }
        text(g, ix + u(34), cy, activeText, active > 0 ? Theme.OK : Theme.MUTED, false);
    }

    @Override
    public void removed() {
        ModuleConfig.saveNow();
        super.removed();
    }

    private void drawDetachedWindows(GuiGraphicsExtractor g, int pw, int ph, float dt, long ms, long nowNs,
                                     float open, int accent, int accent2) {
        int barH = u(34), barW = u(300), barX = (pw - barW) / 2, barY = u(12);
        roundBorder(g, barX, barY, barW, barH, barH / 2, S, Theme.LINE, 0xE010101D);
        text(g, barX + u(14), barY + barH / 2, "CATEGORY WINDOWS", accent, true);
        button(g, barX + barW - u(118), barY + u(6), u(108), barH - u(12), "TABBED VIEW", accent,
                () -> GuiPreferences.setSeparateCategoryWindows(false));

        if (cats.isEmpty()) return;
        int gap = u(10), maxW = u(260), margin = u(10), top = barY + barH + u(10);
        int columns = Math.max(1, Math.min(cats.size(), Math.max(1, (pw - 2 * margin + gap) / (maxW + gap))));
        int ww = Math.min(maxW, Math.max(u(160), (pw - 2 * margin - gap * (columns - 1)) / columns));
        int rows = (cats.size() + columns - 1) / columns;
        int availableH = Math.max(u(150), ph - top - margin - gap * (rows - 1));
        int wh = Math.min(u(300), Math.max(u(170), availableH / rows));
        int headerH = u(34);
        for (int i = 0; i < cats.size(); i++) {
            String cat = cats.get(i);
            int defaultX = margin + (i % columns) * (ww + gap);
            int defaultY = top + (i / columns) * (wh + gap);
            int maxX = Math.max(0, pw - ww), maxY = Math.max(0, ph - wh);
            double[] saved = GuiPreferences.windowPosition(catKeys[i]);
            double defaultNX = maxX == 0 ? 0 : Math.min(1, defaultX / (double) maxX);
            double defaultNY = maxY == 0 ? 0 : Math.min(1, defaultY / (double) maxY);
            if (saved == null) {
                GuiPreferences.setWindowPosition("category:" + cat, defaultNX, defaultNY);
                saved = new double[]{defaultNX, defaultNY};
            }
            int x = Math.round((float) (saved[0] * maxX));
            int y = Math.round((float) (saved[1] * maxY));
            drawDetachedWindow(g, i, cat, x, y, ww, wh, headerH, maxX, maxY, dt, ms, nowNs, accent, accent2);
        }
    }

    private void drawDetachedWindow(GuiGraphicsExtractor g, int index, String cat, int x, int y, int w, int h,
                                    int headerH, int maxX, int maxY, float dt, long ms, long nowNs,
                                    int accent, int accent2) {
        List<GuiModule> mods = catMods.get(index);
        roundFast(g, x - S, y - S + u(3), w + 2 * S, h + 2 * S, u(10), 0x88000000);
        roundBorder(g, x, y, w, h, u(9), S, lerp(Theme.LINE, accent, .45f), Theme.BG);
        round(g, x + S, y + S, w - 2 * S, headerH - S, u(8), Theme.HEADER, true, false);
        fill(g, x + S, y + headerH, w - 2 * S, S, Theme.LINE);
        text(g, x + u(12), y + headerH / 2, cat, Theme.TEXT, true);
        textR(g, x + w - u(12), y + headerH / 2, moduleCountLabel(mods.size()), Theme.MUTED, false);

        float dragOffsetX = mx - x, dragOffsetY = my - y;
        hit(x + S, y + S, w - 2 * S, headerH - S, false, null, (px, py) -> {
            int nx = Math.max(0, Math.min(maxX, Math.round(px - dragOffsetX)));
            int ny = Math.max(0, Math.min(maxY, Math.round(py - dragOffsetY)));
            GuiPreferences.setWindowPosition("category:" + cat, maxX == 0 ? 0 : nx / (double) maxX,
                    maxY == 0 ? 0 : ny / (double) maxY);
        });

        vx = x + u(8);
        vw = Math.max(1, w - u(16));
        vy = y + headerH + u(6);
        vh = Math.max(1, y + h - u(8) - vy);
        float target = Math.max(0, detachedScrollTarget.getOrDefault(cat, 0f));
        float scroll = detachedScroll.getOrDefault(cat, 0f);
        int total = 0, gap = u(7);
        int[] cardMetrics = detachedCardMetrics.computeIfAbsent(cat, ignored -> new int[mods.size() * 2]);
        if (cardMetrics.length != mods.size() * 2) {
            cardMetrics = new int[mods.size() * 2];
            detachedCardMetrics.put(cat, cardMetrics);
        }
        for (int i = 0; i < mods.size(); i++) {
            GuiModule module = mods.get(i);
            CardState state = states.computeIfAbsent(module, ignored -> new CardState());
            state.expand += ((state.open ? 1f : 0f) - state.expand) * Math.min(1f, dt * 12f);
            if (Math.abs((state.open ? 1f : 0f) - state.expand) < .005f) state.expand = state.open ? 1f : 0f;
            int header = u(48);
            cardMetrics[i * 2] = header;
            int cardHeight = header + Math.round(settingsHeight(module) * state.expand);
            cardMetrics[i * 2 + 1] = cardHeight;
            total += cardHeight + gap;
        }
        float content = Math.max(0, total - gap);
        target = Math.min(target, Math.max(0, content - vh));
        scroll += (target - scroll) * Math.min(1f, dt * 14f);
        if (Math.abs(target - scroll) < .5f) scroll = target;
        detachedScrollTarget.put(cat, target);
        detachedScroll.put(cat, scroll);
        detachedRects.computeIfAbsent(cat, ignored -> new WindowRect()).set(x, y, w, h);

        g.enableScissor(vx, vy, vx + vw, vy + vh);
        int cardY = vy - Math.round(scroll);
        float categoryTime = (nowNs - catNs) / 1_000_000_000f;
        for (int i = 0; i < mods.size(); i++) {
            GuiModule module = mods.get(i);
            CardState state = states.computeIfAbsent(module, ignored -> new CardState());
            int cardHeader = cardMetrics[i * 2];
            int cardHeight = cardMetrics[i * 2 + 1];
            float appear = easeOut((categoryTime - .07f * index - .12f) / .35f);
            int drawnY = cardY + Math.round((1f - appear) * u(14));
            if (appear > .01f && drawnY + cardHeight >= vy && drawnY <= vy + vh)
                drawCard(g, module, state, vx, drawnY, vw, cardHeader, cardHeight, accent, accent2, dt, ms);
            cardY += cardHeight + gap;
        }
        g.disableScissor();
        if (content > vh) {
            int barH = Math.max(u(20), Math.round(vh * (vh / content)));
            int barY = vy + Math.round((vh - barH) * (scroll / Math.max(1f, content - vh)));
            round(g, vx + vw + u(2), barY, u(3), barH, u(1), alpha(accent, 0xB0));
        }
    }

    // =====================================================================
    //  content (cards generated from ModuleRegistry)
    // =====================================================================
    private void drawContent(GuiGraphicsExtractor g, int ix, int iw, int top, int bottom,
                             int sideW, int accent, int accent2, float dt, long ms, long nowNs) {
        if (cats.isEmpty() || category >= catMods.size()) return;
        String cat = cats.get(category);
        List<GuiModule> mods = catMods.get(category);

        vx = ix + sideW + u(14);
        vw = iw - sideW - u(28);
        vy = top + u(44);
        vh = bottom - vy - u(8);

        text(g, vx, top + u(22), cat, Theme.TEXT, true);
        textR(g, vx + vw, top + u(22), moduleCountLabel(mods.size()), Theme.MUTED, false);

        float maxScroll = Math.max(0, contentHeight - vh);
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
        if (Math.abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget;

        float catT = (nowNs - catNs) / 1_000_000_000f;

        g.enableScissor(vx, vy, vx + vw, vy + vh);
        int cursor = vy - Math.round(scroll);
        int total = 0;
        int gap = u(8);
        int index = 0;
        for (GuiModule m : mods) {
            CardState st = states.computeIfAbsent(m, k -> new CardState());
            st.expand += ((st.open ? 1f : 0f) - st.expand) * Math.min(1f, dt * 12f);
            if (Math.abs((st.open ? 1f : 0f) - st.expand) < 0.005f) st.expand = st.open ? 1f : 0f;

            int hdr = u(52);
            int cardH = hdr + Math.round(settingsHeight(m) * st.expand);

            // staggered slide-in
            float ap = easeOut((catT - 0.07f * index - 0.12f) / 0.35f);
            int yy = cursor + Math.round((1f - ap) * u(18));
            if (ap > 0.01f && yy + cardH >= vy && yy <= vy + vh) {
                drawCard(g, m, st, vx, yy, vw, hdr, cardH, accent, accent2, dt, ms);
            }
            cursor += cardH + gap;
            total += cardH + gap;
            index++;
        }
        g.disableScissor();
        contentHeight = Math.max(0, total - gap);

        if (contentHeight > vh) {
            int barH = Math.max(u(24), Math.round(vh * (vh / contentHeight)));
            int barY = vy + Math.round((vh - barH) * (scroll / Math.max(1f, contentHeight - vh)));
            round(g, vx + vw + u(5), barY, u(3), barH, u(1), alpha(accent, 0xB0));
        }
    }

    private int rowHeight(Setting s) {
        if (s instanceof Setting.Num) return u(38);
        if (s instanceof Setting.Mode) return u(30);
        if (s instanceof Setting.Text || s instanceof Setting.Action) return u(32);
        return u(28);
    }

    private int settingsHeight(GuiModule m) {
        List<Setting> list = m.settings();
        if (list.isEmpty()) return 0;
        int h = u(8) + u(6);
        for (Setting s : list) h += rowHeight(s);
        return h;
    }

    private void drawCard(GuiGraphicsExtractor g, GuiModule m, CardState st, int x, int y, int w,
                          int hdr, int cardH, int accent, int accent2, float dt, long ms) {
        boolean on = m.isEnabled();
        if (st.initials == null) st.initials = initials(m.name);
        boolean hov = inside(mx, my, x, y, w, hdr) && inside(mx, my, vx, vy, vw, vh);
        st.hover += ((hov ? 1f : 0f) - st.hover) * Math.min(1f, dt * 14f);
        st.enable += ((on ? 1f : 0f) - st.enable) * Math.min(1f, dt * 12f);

        // outer glow while enabled
        if (st.enable > 0.02f) {
            roundFast(g, x - u(2), y - u(2), w + u(4), cardH + u(4), u(12), alpha(accent, (int) (14 * st.enable)));
        }

        int border = lerp(Theme.LINE, accent, 0.15f * st.hover + 0.45f * st.enable);
        int fillCol = lerp(lerp(Theme.CARD, Theme.CARD_HOVER, st.hover), accent, 0.04f * st.enable);
        roundBorder(g, x, y, w, cardH, u(10), S, border, fillCol);

        if (FANCY && st.hover > 0.02f) {
            // spotlight following the mouse; shineC clamps it to the header, so no scissor is needed
            shineC(g, x + S, w - 2 * S, y + S, hdr - S, mx, u(90), (int) (0x22 * st.hover), accent);
        }

        // enabled: vertical accent bar on the left edge
        if (st.enable > 0.02f) {
            int ba = (int) (0xE0 * st.enable);
            vgrad(g, x + S, y + u(12), S + 1, hdr - u(24), alpha(accent, ba), alpha(accent2, ba));
        }

        // enabled accent line with moving shine
        if (st.enable > 0.02f) {
            int lx = x + u(14), lw = w - u(28), ly = y + hdr - u(3), lh = Math.max(1, S);
            hgrad(g, lx, ly, lw, lh, alpha(accent, (int) (200 * st.enable)), alpha(accent2, (int) (200 * st.enable)));
            float ph = (((ms + (m.name.hashCode() & 255) * 9L) / 1600f) % 1.5f) - 0.25f;
            shine(g, lx, lw, ly, lh, lx + Math.round(ph * lw), u(46), (int) (0xC0 * st.enable));
        }

        // icon tile (glows when enabled)
        int tile = u(30);
        int tx0 = x + u(12), ty0 = y + (hdr - tile) / 2;
        if (FANCY && st.enable > 0.02f) {
            roundFast(g, tx0 - u(3), ty0 - u(3), tile + u(6), tile + u(6), u(11), alpha(accent, (int) (24 * st.enable)));
        }
        round(g, tx0, ty0, tile, tile, u(8), lerp(Theme.CARD_HOVER, accent, 0.12f + 0.25f * st.enable));
        if (!st.iconInit) {
            st.icon = moduleIcon(m.name);
            st.iconInit = true;
        }
        ItemStack icon = st.icon;
        if (ICONS && !icon.isEmpty()) {
            g.pose().pushMatrix();
            g.pose().translate(tx0 + tile / 2f, ty0 + tile / 2f);
            g.pose().scale(S, S);
            g.item(icon, -8, -8);
            g.pose().popMatrix();
        }
        else textC(g, tx0 + tile / 2, ty0 + tile / 2, st.initials, lerp(Theme.MUTED, accent, st.enable), true);

        // toggle
        int tw = u(36), th = u(18);
        int sx = x + w - u(14) - tw, sy = y + (hdr - th) / 2;
        if (st.enable > 0.02f) glow(g, sx + tw / 2, sy + th / 2, u(26), accent, 3, (int) (10 * st.enable));
        toggle(g, sx, sy, tw, th, st.enable, accent);
        int nameX = x + u(52);
        text(g, nameX, y + u(19), fitText(m.name, Math.max(0, sx - u(8) - nameX), true), Theme.TEXT, true);
        if (w >= u(320)) text(g, nameX, y + u(35), fitText(m.description, Math.max(0, sx - u(8) - nameX), false), Theme.MUTED, false);

        List<Setting> moduleSettings = m.settings();
        if (!moduleSettings.isEmpty()) {
            chevron(g, sx - u(24), y + hdr / 2, u(4), st.open, lerp(Theme.MUTED, accent, st.hover));
        }

        hit(x, y, w, hdr, true, b -> {
            if (b == 1 && !moduleSettings.isEmpty()) st.open = !st.open;
            else if (b == 0) {
                m.toggle();
                activeStamp = -1000L;
                toast("MODULE", m.name + (m.isEnabled() ? " enabled" : " disabled"), false);
            }
        }, null);
        if (!moduleSettings.isEmpty()) {
            hit(sx - u(44), y, u(40), hdr, true, b -> st.open = !st.open, null);
        }

        if (st.expand > 0.01f && !moduleSettings.isEmpty()) {
            boolean clip = st.expand < 0.999f;
            if (clip) g.enableScissor(x + S, y + hdr, x + w - S, y + cardH - S);
            fill(g, x + u(12), y + hdr, w - u(24), S, Theme.LINE);
            boolean interactive = st.expand > 0.9f;
            int ry = y + hdr + u(8);
            for (Setting s : moduleSettings) {
                int rh = rowHeight(s);
                drawSetting(g, s, x, ry, w, rh, accent, accent2, interactive, ms);
                ry += rh;
            }
            if (clip) g.disableScissor();
        }
    }

    private void drawSetting(GuiGraphicsExtractor g, Setting s, int x, int y, int w, int rh,
                             int accent, int accent2, boolean interactive, long ms) {
        int pad = u(14);
        int lx = x + pad, rx = x + w - pad;

        if (s instanceof Setting.Bool b) {
            int tw = u(30), th = u(16);
            text(g, lx, y + rh / 2, fitText(b.name, Math.max(0, rx - tw - u(8) - lx), false), Theme.SUBTEXT, false);
            toggle(g, rx - tw, y + (rh - th) / 2, tw, th, b.get() ? 1f : 0f, accent);
            if (interactive) hit(x, y, w, rh, true, btn -> b.toggle(), null);

        } else if (s instanceof Setting.Mode md) {
            int bw = u(96), bh = u(20), bx = rx - bw, by = y + (rh - bh) / 2;
            text(g, lx, y + rh / 2, fitText(md.name, Math.max(0, bx - u(8) - lx), false), Theme.SUBTEXT, false);
            boolean hov = interactive && inside(mx, my, bx, by, bw, bh) && inside(mx, my, vx, vy, vw, vh);
            roundBorder(g, bx, by, bw, bh, bh / 2, S, lerp(Theme.LINE, accent, hov ? 0.8f : 0f), 0xFF1B1B2B);
            textC(g, bx + bw / 2, by + bh / 2, md.get(), accent, false);
            textC(g, bx + u(9), by + bh / 2, "<", hov ? accent : Theme.DIM, false);
            textC(g, bx + bw - u(9), by + bh / 2, ">", hov ? accent : Theme.DIM, false);
            if (interactive) hit(bx, by, bw, bh, true, btn -> md.cycle(btn == 1 ? -1 : 1), null);

        } else if (s instanceof Setting.Num n) {
            String shown = n.display();
            text(g, lx, y + u(12), fitText(n.name, Math.max(0, rx - textWidth(shown, true) - u(12) - lx), false), Theme.SUBTEXT, false);
            textR(g, rx, y + u(12), shown, accent, true);

            int trackX = lx, trackW = rx - lx, trackH = u(6), trackY = y + u(22);
            round(g, trackX, trackY, trackW, trackH, trackH / 2, 0xFF26263A);
            int fw = Math.max(trackH, Math.round(trackW * (float) n.fraction()));
            pillGrad(g, trackX, trackY, fw, trackH, accent, accent2);
            if (FX >= 2) {
                shine(g, trackX, fw, trackY, trackH, trackX + Math.round(((ms / 1200f) % 1.4f - 0.2f) * fw), u(24), 0x60);
            }
            int kx = trackX + Math.round(trackW * (float) n.fraction()), ky = trackY + trackH / 2;
            glow(g, kx, ky, u(12), accent, 4, 14);
            circle(g, kx, ky, u(7), alpha(accent, 0x90));
            circle(g, kx, ky, u(5), 0xFFFFFFFF);

            if (interactive) {
                hit(trackX - u(8), trackY - u(9), trackW + u(16), u(24), true, btn -> {},
                        (px, py) -> n.setFraction((px - trackX) / (double) trackW));
            }
        } else if (s instanceof Setting.Text field) {
            int bw = Math.min(u(132), Math.max(u(72), (w - u(42)) / 2)), bh = u(21), bx = rx - bw, by = y + (rh - bh) / 2;
            text(g, lx, y + rh / 2, fitText(field.name, Math.max(0, bx - u(8) - lx), false), Theme.SUBTEXT, false);
            boolean focused = focusedText == field;
            roundBorder(g, bx, by, bw, bh, bh / 2, S, lerp(Theme.LINE, accent, focused ? 0.9f : 0f), 0xFF171725);
            String value = field.get();
            String visible = value.length() > 18 ? value.substring(value.length() - 18) : value;
            text(g, bx + u(7), by + bh / 2, visible + (focused && (System.currentTimeMillis() / 500 % 2 == 0) ? "|" : ""), focused ? accent : Theme.SUBTEXT, false);
            if (interactive) hit(bx, by, bw, bh, true, btn -> focusedText = field, null);
        } else if (s instanceof Setting.Action action) {
            int bw = Math.min(u(132), w - u(28)), bh = u(22), bx = rx - bw, by = y + (rh - bh) / 2;
            boolean hov = interactive && inside(mx, my, bx, by, bw, bh) && inside(mx, my, vx, vy, vw, vh);
            roundBorder(g, bx, by, bw, bh, bh / 2, S, lerp(Theme.LINE, accent, hov ? 0.9f : 0.3f), lerp(Theme.CARD, accent, hov ? .18f : .08f));
            textC(g, bx + bw / 2, by + bh / 2, action.name, Theme.TEXT, false);
            if (interactive) hit(bx, by, bw, bh, true, btn -> action.run(), null);
        }
    }

    // =====================================================================
    //  footer + toasts
    // =====================================================================
    private void drawFooter(GuiGraphicsExtractor g, int ix, int iw, int footTop, int footH) {
        int cy = footTop + footH / 2 + S;
        text(g, ix + u(16), cy, "ANTHRAX  /  PERSONAL CONFIGURATION", Theme.DIM, false);
        text(g, ix + u(226), cy, costText, Theme.DIM, false);
        int buttonX = ix + iw - u(112), buttonY = cy - u(9);
        textR(g, buttonX - u(8), cy, "DRAG TO MOVE", Theme.MUTED, false);
        button(g, buttonX, buttonY, u(96), u(18), "WINDOWS", Theme.accent(),
                () -> GuiPreferences.setSeparateCategoryWindows(true));
    }

    private void toast(String title, String msg, boolean err) {
        toasts.add(new Toast(title, msg, err, System.currentTimeMillis()));
        if (toasts.size() > 4) toasts.remove(0);
    }

    private void drawToasts(GuiGraphicsExtractor g, int pw, int ph, int accent) {
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now - t.born() > 3300);
        int w = u(230), h = u(46), gap = u(8);
        int yb = ph - u(18);
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            float age = (now - t.born()) / 1000f;
            float slide = (1f - easeOut(age / 0.3f)) + easeOut((age - 2.9f) / 0.35f);
            int x = pw - u(18) - w + Math.round(slide * (w + u(30)));
            int y = yb - h;
            yb -= h + gap;
            int col = t.err() ? Theme.DANGER : accent;

            glow(g, x + w / 2, y + h / 2, w / 2 + u(10), col, 3, 6);
            roundBorder(g, x, y, w, h, u(8), S, lerp(Theme.LINE, col, 0.5f), 0xFF0E0E16);
            round(g, x + u(1), y + u(9), u(3), h - u(18), u(1), col);
            text(g, x + u(16), y + u(14), t.title(), col, true);
            text(g, x + u(16), y + u(30), t.msg(), Theme.TEXT, false);
            float prog = 1f - Math.min(1f, age / 3.0f);
            round(g, x + u(8), y + h - u(6), Math.round((w - u(16)) * prog), u(2), u(1), col);
        }
    }

    private String moduleCountLabel(int count) {
        if (count != previousModuleCount) {
            previousModuleCount = count;
            moduleCountText = count + (count == 1 ? " module" : " modules");
        }
        return moduleCountText;
    }

    private static String fitText(String value, int maxWidth, boolean bold) {
        if (value == null || maxWidth <= 0) return "";
        FIT_PROBE.value = value;
        FIT_PROBE.width = maxWidth;
        FIT_PROBE.bold = bold;
        FIT_PROBE.scale = S;
        String cached = FIT_TEXT_CACHE.get(FIT_PROBE);
        if (cached != null) return cached;
        String result;
        if (textWidth(value, bold) <= maxWidth) {
            result = value;
        } else {
            String suffix = "…";
            int end = value.length();
            while (end > 0 && textWidth(value.substring(0, end) + suffix, bold) > maxWidth) end--;
            result = end == 0 ? "" : value.substring(0, end) + suffix;
        }
        FIT_TEXT_CACHE.put(new FitKey(value, maxWidth, bold, S), result);
        return result;
    }

    // =====================================================================
    //  input
    // =====================================================================
    private void hit(int x, int y, int w, int h, boolean clipped, IntConsumer press, Drag drag) {
        if (hitCount == hits.size()) hits.add(new Hit());
        hits.get(hitCount++).set(x, y, w, h, clipped, clipped ? vx : 0, clipped ? vy : 0,
                clipped ? vw : 0, clipped ? vh : 0, press, drag);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        focusedText = null;
        selectAllText = false;
        int px = Math.round((float) event.x() * gs / uiOverride), py = Math.round((float) event.y() * gs / uiOverride);
        int button = event.button();
        for (int i = hitCount - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(px, py)) continue;
            if (h.clipped && !inside(px, py, h.clipX, h.clipY, h.clipW, h.clipH)) continue;
            if (h.drag != null) {
                activeDrag = h.drag;
                activeDrag.to(px, py);
            }
            if (h.press != null) h.press.accept(button);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedText == null || !event.isAllowedChatCharacter()) return super.charTyped(event);
        String value = selectAllText ? "" : focusedText.get();
        selectAllText = false;
        if (value.length() < focusedText.maxLength) focusedText.set(value + event.codepointAsString());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (focusedText != null) {
            boolean control = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            if (control && event.key() == GLFW.GLFW_KEY_A) {
                selectAllText = true;
                return true;
            }
            if (control && event.key() == GLFW.GLFW_KEY_V) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clipboard != null) {
                    clipboard = clipboard.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
                    String prefix = selectAllText ? "" : focusedText.get();
                    int remaining = focusedText.maxLength - prefix.length();
                    if (remaining > 0) focusedText.set(prefix + clipboard.substring(0, Math.min(remaining, clipboard.length())));
                }
                selectAllText = false;
                return true;
            }
            if (control && (event.key() == GLFW.GLFW_KEY_C || event.key() == GLFW.GLFW_KEY_X)) {
                if (selectAllText) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(focusedText.get());
                    if (event.key() == GLFW.GLFW_KEY_X) focusedText.set("");
                    selectAllText = false;
                }
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                String value = focusedText.get();
                if (selectAllText) focusedText.set("");
                else if (!value.isEmpty()) focusedText.set(value.substring(0, value.length() - 1));
                selectAllText = false;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_ESCAPE) {
                focusedText = null;
                selectAllText = false;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeDrag != null) {
            activeDrag.to(Math.round((float) event.x() * gs / uiOverride),
                    Math.round((float) event.y() * gs / uiOverride));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        activeDrag = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (GuiPreferences.separateCategoryWindows()) {
            int px = Math.round((float) mouseX * gs / uiOverride);
            int py = Math.round((float) mouseY * gs / uiOverride);
            for (int i = cats.size() - 1; i >= 0; i--) {
                String cat = cats.get(i);
                WindowRect rect = detachedRects.get(cat);
                if (rect != null && rect.contains(px, py)) {
                    detachedScrollTarget.merge(cat, (float) -scrollY * u(48), Float::sum);
                    return true;
                }
            }
            return true;
        }
        scrollTarget -= (float) scrollY * u(48);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String initials(String name) {
        String value = name.trim();
        if (value.isEmpty()) return value;
        int firstEnd = 0;
        while (firstEnd < value.length() && !Character.isWhitespace(value.charAt(firstEnd))) firstEnd++;
        int secondStart = firstEnd;
        while (secondStart < value.length() && Character.isWhitespace(value.charAt(secondStart))) secondStart++;
        if (secondStart < value.length()) {
            return ("" + value.charAt(0) + value.charAt(secondStart)).toUpperCase();
        }
        return value.substring(0, Math.min(2, value.length())).toUpperCase();
    }

    /** Native GUI item rendering gives these tiles Minecraft's lit, model-backed item icons. */
    private static ItemStack moduleIcon(String name) {
        return MODULE_ICONS.computeIfAbsent(name, AnthraxScreen::createModuleIcon);
    }

    private static ItemStack createModuleIcon(String name) {
        return switch (name) {
            case "Crystal Aura", "Auto Crystal", "Crystal Spam", "Self Crystal" -> new ItemStack(Items.END_CRYSTAL);
            case "Auto Totem", "Totem Notifier" -> new ItemStack(Items.TOTEM_OF_UNDYING);
            case "Auto Anchor" -> new ItemStack(Items.RESPAWN_ANCHOR);
            case "Aim Assist" -> new ItemStack(Items.TARGET);
            case "Player Dig Safety" -> new ItemStack(Items.IRON_PICKAXE);
            case "Time Changer", "FPS Chart" -> new ItemStack(Items.CLOCK);
            case "Show Player List" -> new ItemStack(Items.PLAYER_HEAD);
            case "Player ESP" -> new ItemStack(Items.PLAYER_HEAD);
            case "Advanced ESP" -> new ItemStack(Items.SPYGLASS);
            case "Seed-Ray" -> new ItemStack(Items.DIAMOND_ORE);
            case "World Themes" -> new ItemStack(Items.AMETHYST_SHARD);
            case "Font Size Override", "Loadout Manager" -> new ItemStack(Items.BOOK);
            case "Cosmic Crosshair", "Show Cosmic Crosshair" -> new ItemStack(Items.NETHER_STAR);
            case "No Render", "Interaction Canceler" -> new ItemStack(Items.BARRIER);
            case "Hand Render" -> new ItemStack(Items.ARMOR_STAND);
            case "Auto Disconnect" -> new ItemStack(Items.ENDER_PEARL);
            case "Auto Sell" -> new ItemStack(Items.EMERALD);
            case "Chat Filter" -> new ItemStack(Items.PAPER);
            case "Storage Scan" -> new ItemStack(Items.CHEST);
            default -> ItemStack.EMPTY;
        };
    }
}
