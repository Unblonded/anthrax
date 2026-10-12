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
import unblonded.anthrax.Anthrax;
import unblonded.anthrax.modules.GuiModule;
import unblonded.anthrax.modules.Keybinds;
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
import com.mojang.blaze3d.platform.InputConstants;

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
        float expand, hover, enable, lift, liftV;
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
    // search, keybinding and tooltip state
    private String search = "";
    private boolean searchFocused;
    private final List<GuiModule> searchResults = new ArrayList<>();
    private String searchComputedFor;
    private GuiModule binding;
    private GuiModule bindingBeforeClick;
    private Setting.Num hoveredSlider;
    private boolean swallowNextChar;
    private GuiModule hoverModule;
    private long hoverSince;
    private String tooltip;
    private int tooltipX, tooltipY;
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
    private float tabY = -1, tabV;
    /** Per-setting animation state: [0] value position, [1] hover, [2] spring velocity. */
    private final Map<Setting, float[]> settingAnim = new IdentityHashMap<>();
    private Setting.Num draggingSlider;
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
    /** Blur the live world behind the menu; the liquid-glass panes are drawn on top of it. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (Draw.FX > 0) extractBlurredBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Not calling super: this screen paints its own glass UI and has no vanilla widgets.

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
        tooltip = null;
        hoveredSlider = null;
        long ms = wallMs - startMs;
        Theme.tick(wallMs);
        Draw.S = Math.max(1, Math.min(4, Math.min(Math.round(ph / 720f), Math.min(pw / 600, ph / 400))));
        int accent = Theme.accent(), accent2 = Theme.accent2();

        float introT = (nowNs - openNs) / 1_000_000_000f;
        float open = easeOut(introT / 0.45f);

        // ---- backdrop: veil over the blurred world, tinted light, drifting liquid orbs
        g.fill(0, 0, pw, ph, alpha(0x05060B, (int) ((FX > 0 ? 0x5C : 0xC0) * open)));
        g.fillGradient(0, 0, pw, ph, alpha(accent, (int) (0x10 * open)), alpha(accent2, (int) (0x18 * open)));
        if (FX >= 2) drawOrbs(g, pw, ph, ms, open, accent, accent2);

        if (GuiPreferences.separateCategoryWindows()) {
            drawDetachedWindows(g, pw, ph, dt, ms, nowNs, open, accent, accent2);
            Hud.toasts(g, pw, ph, accent, u(18));
            drawTooltip(g, pw, ph, accent);
            return;
        }

        int ww = u(600), wh = u(392);
        int maxWX = Math.max(0, pw - ww), maxWY = Math.max(0, ph - wh);
        double[] storedMain = GuiPreferences.windowPosition("__main");
        if (storedMain == null) storedMain = new double[]{.5, .5};
        int wx = Math.round((float) (storedMain[0] * maxWX));
        int wy = Math.round((float) (storedMain[1] * maxWY));
        int introOffset = Math.round((1f - open) * u(24));
        wy += introOffset;
        int r = u(24);
        int headerH = u(52), sideW = u(136), footH = u(30);
        int ix = wx, iw = ww;
        int footTop = wy + wh - footH;
        int bodyTop = wy + headerH;

        // intro: the pane settles from 96% scale, like a sheet landing on glass
        float scaleIn = 0.96f + 0.04f * open;
        g.pose().pushMatrix();
        g.pose().translate(wx + ww / 2f, wy + wh / 2f);
        g.pose().scale(scaleIn, scaleIn);
        g.pose().translate(-(wx + ww / 2f), -(wy + wh / 2f));
        try {
            shadow(g, wx, wy, ww, wh, r, u(22), (int) (0x70 * open));
            glass(g, wx, wy, ww, wh, r, alpha(0x0E1119, 0xB4), 1f);
            if (FX >= 2) drawCursorLight(g, wx, wy, ww, wh);
            drawRimSweep(g, wx, wy, ww, r, ms, introT);

            fill(g, wx + r, bodyTop, ww - 2 * r, Math.max(1, S / 2), 0x16FFFFFF);
            fill(g, wx + r, footTop, ww - 2 * r, Math.max(1, S / 2), 0x10FFFFFF);

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
        } finally {
            g.pose().popMatrix();
        }

        Hud.toasts(g, pw, ph, accent, u(18));
        drawTooltip(g, pw, ph, accent);
    }

    // =====================================================================
    //  backdrop effects
    // =====================================================================
    /** Big, slow, soft color orbs drifting behind the glass; they give the panes something to refract. */
    private void drawOrbs(GuiGraphicsExtractor g, int pw, int ph, long ms, float open, int accent, int accent2) {
        if (open < 0.05f) return;
        int a = (int) (22 * open);
        glow(g, Math.round(pw * (0.24f + 0.08f * (float) Math.sin(ms / 5200.0))),
                Math.round(ph * (0.28f + 0.10f * (float) Math.cos(ms / 4300.0))), u(260), accent, 4, a);
        glow(g, Math.round(pw * (0.78f + 0.06f * (float) Math.cos(ms / 6100.0))),
                Math.round(ph * (0.70f + 0.09f * (float) Math.sin(ms / 4700.0))), u(240), accent2, 4, a);
        if (FX >= 2) {
            glow(g, Math.round(pw * (0.55f + 0.20f * (float) Math.sin(ms / 7300.0))),
                    Math.round(ph * (0.15f + 0.05f * (float) Math.cos(ms / 3900.0))), u(180),
                    hsl(Theme.hue + 120, 0.8f, 0.6f), 3, a * 2 / 3);
        }
    }

    /** A soft light that follows the cursor across the glass, clipped to the pane. */
    private void drawCursorLight(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (!inside(mx, my, x - u(40), y - u(40), w + u(80), h + u(80))) return;
        g.enableScissor(x + u(6), y + u(6), x + w - u(6), y + h - u(6));
        glow(g, mx, my, u(150), 0xFFFFFF, 3, 7);
        g.disableScissor();
    }

    /** A highlight that glides along the top rim now and then, plus a bright pass when the menu opens. */
    private void drawRimSweep(GuiGraphicsExtractor g, int x, int y, int w, int r, long ms, float introT) {
        if (!FANCY) return;
        int rimX = x + r, rimW = w - 2 * r, t = Math.max(1, S / 2 + (S > 2 ? 1 : 0));
        float cycle = (ms % 6500L) / 6500f;
        if (cycle < 0.35f) {
            float p = cycle / 0.35f;
            int a = (int) (0xB0 * Math.sin(Math.PI * p));
            shineC(g, rimX, rimW, y, t, rimX + Math.round(p * rimW), u(120), a, 0xFFFFFF);
        }
        if (introT < 1f) {
            float p = easeOut(introT / 0.8f);
            shineC(g, rimX, rimW, y, t * 2, rimX + Math.round(p * rimW), u(160), (int) (0xFF * (1f - introT)), 0xFFFFFF);
        }
    }

    // =====================================================================
    //  header
    // =====================================================================
    private void drawHeader(GuiGraphicsExtractor g, int ix, int iw, int wy, int headerH, int accent, int accent2, long ms) {
        int cy = wy + headerH / 2;

        // app mark: the Anthrax logo
        int mark = u(28);
        int mx0 = ix + u(16), my0 = cy - mark / 2;
        if (FANCY) glow(g, mx0 + mark / 2, cy, u(24), accent, 3, 10 + (int) (6 * Math.sin(ms / 600.0)));
        logo(g, mx0, my0, mark, accent, Theme.accent2());

        int tx = mx0 + mark + u(10);
        if (Theme.idx == 0) textRainbow(g, tx, cy - u(6), "Anthrax", Theme.hue, 22f, true);
        else text(g, tx, cy - u(6), "Anthrax", Theme.TEXT, true);
        text(g, tx, cy + u(8), "Liquid client", Theme.MUTED, false);

        int fpsCol = fps >= 60 ? Theme.OK : fps >= 30 ? 0xFFFFC94A : Theme.DANGER;
        drawSearch(g, ix + u(166), cy, u(170), accent);
        chip(g, ix + u(344), cy, u(60), fpsText, fpsCol);

        int bh = u(26);
        int closeX = ix + iw - u(16) - bh;
        int themeW = u(84);
        int themeX = closeX - u(8) - themeW;
        int fxW = u(46);
        int fxX = themeX - u(6) - fxW;

        button(g, fxX, cy - bh / 2, fxW, bh, FX_NAMES[FX], accent, () -> {
            Draw.setFx(Draw.FX + 1);
            toast("Effects", FX_LONG[Draw.FX], false);
        });
        button(g, themeX, cy - bh / 2, themeW, bh, Theme.NAMES[Theme.idx], accent, () -> {
            Theme.next();
            toast("Theme", Theme.NAMES[Theme.idx], false);
        });
        circle(g, themeX + u(12), cy, u(4), accent);
        iconButton(g, closeX, cy - bh / 2, bh, Icons.XMARK, Theme.DANGER, this::onClose);
    }

    private void drawSearch(GuiGraphicsExtractor g, int x, int cy, int w, int accent) {
        int h = u(26), y = cy - h / 2;
        boolean hov = inside(mx, my, x, y, w, h);
        boolean active = searchFocused || !search.isEmpty();
        round(g, x, y, w, h, h / 2, alpha(0x000000, searchFocused ? 0x48 : 0x30));
        outline(g, x, y, w, h, h / 2, Math.max(1, S / 2),
                searchFocused ? alpha(accent, 0xE0) : alpha(0xFFFFFF, hov ? 0x40 : 0x22),
                searchFocused ? alpha(accent, 0x90) : alpha(0xFFFFFF, hov ? 0x20 : 0x12));
        if (searchFocused && FANCY) roundFast(g, x - u(3), y - u(3), w + u(6), h + u(6), h / 2 + u(3), alpha(accent, 0x18));
        icon(g, x + u(14), cy, Icons.MAGNIFYING_GLASS, active ? accent : Theme.MUTED);
        int tx = x + u(26), maxW = w - u(26) - u(24);
        String shown = fitTail(search, maxW);
        if (search.isEmpty()) {
            if (!searchFocused) text(g, tx, cy, "Search", Theme.MUTED, false);
            if (!searchFocused) textR(g, x + w - u(12), cy, "Ctrl F", Theme.DIM, false);
        } else {
            text(g, tx, cy, shown, Theme.TEXT, false);
        }
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = tx + (search.isEmpty() ? 0 : textWidth(shown, false)) + S;
            fill(g, caretX, cy - u(7), S, u(14), accent);
        }
        hit(x, y, w, h, false, b -> { searchFocused = true; focusedText = null; }, null);
        if (!search.isEmpty()) {
            int cx = x + w - u(14);
            boolean clearHov = inside(mx, my, cx - u(9), y, u(18), h);
            circle(g, cx, cy, u(7), clearHov ? alpha(Theme.DANGER, 0xC0) : 0x40FFFFFF);
            icon(g, cx, cy, Icons.XMARK, 0xFFFFFFFF);
            hit(cx - u(9), y, u(18), h, false, b -> { search = ""; searchFocused = false; scrollTarget = 0; }, null);
        }
    }

    private boolean searching() { return !search.isBlank() && !GuiPreferences.separateCategoryWindows(); }

    /** Modules whose name, description, category or setting names contain every typed word. */
    private List<GuiModule> searchResults() {
        String query = search.trim().toLowerCase(java.util.Locale.ROOT);
        if (query.equals(searchComputedFor)) return searchResults;
        searchComputedFor = query;
        searchResults.clear();
        String[] words = query.split("\\s+");
        for (GuiModule module : ModuleRegistry.all()) {
            StringBuilder hay = new StringBuilder(module.name).append(' ').append(module.description)
                    .append(' ').append(module.category);
            for (Setting setting : module.settings()) hay.append(' ').append(setting.name);
            String haystack = hay.toString().toLowerCase(java.util.Locale.ROOT);
            boolean all = true;
            for (String word : words) if (!haystack.contains(word)) { all = false; break; }
            if (all) searchResults.add(module);
        }
        // Modules whose name matches come first, then alphabetical.
        searchResults.sort(java.util.Comparator
                .comparing((GuiModule m) -> !m.name.toLowerCase(java.util.Locale.ROOT).contains(words[0]))
                .thenComparing(m -> m.name));
        return searchResults;
    }

    private static String categoryIcon(String category) {
        return switch (category) {
            case "Combat" -> Icons.CROSSHAIRS;
            case "Mining" -> Icons.GEM;
            case "World" -> Icons.MAP_LOCATION_DOT;
            case "Visuals" -> Icons.EYE;
            case "Utility" -> Icons.SCREWDRIVER_WRENCH;
            case "Client" -> Icons.SLIDERS;
            default -> Icons.LAYER_GROUP;
        };
    }

    private void chip(GuiGraphicsExtractor g, int x, int cy, int w, String s, int textCol) {
        int h = u(22);
        round(g, x, cy - h / 2, w, h, h / 2, 0x1CFFFFFF);
        outline(g, x, cy - h / 2, w, h, h / 2, Math.max(1, S / 2), 0x30FFFFFF, 0x10FFFFFF);
        circle(g, x + u(10), cy, u(3), textCol);
        textC(g, x + w / 2 + u(5), cy, s, Theme.SUBTEXT, false);
    }

    /** Glass pill button: lifts and catches light on hover, tints with its color while pressed. */
    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int hoverCol, Runnable action) {
        boolean hov = inside(mx, my, x, y, w, h);
        boolean down = hov && mouseDown();
        if (hov && FANCY) roundFast(g, x - u(3), y - u(2), w + u(6), h + u(6), h / 2 + u(3), alpha(hoverCol, 0x18));
        glass(g, x, y + (down ? S : 0), w, h, h / 2,
                down ? alpha(hoverCol, 0x50) : hov ? alpha(0xFFFFFF, 0x26) : alpha(0xFFFFFF, 0x14), hov ? 1f : 0.4f);
        textC(g, x + w / 2, y + h / 2 + (down ? S : 0), label, hov ? 0xFFFFFFFF : Theme.SUBTEXT, false);
        hit(x, y, w, h, false, b -> action.run(), null);
    }

    private void iconButton(GuiGraphicsExtractor g, int x, int y, int size, String glyph, int hoverCol, Runnable action) {
        boolean hov = inside(mx, my, x, y, size, size);
        glass(g, x, y, size, size, size / 2, hov ? alpha(hoverCol, 0x90) : alpha(0xFFFFFF, 0x14), hov ? 1f : 0.4f);
        icon(g, x + size / 2, y + size / 2, glyph, hov ? 0xFFFFFFFF : Theme.SUBTEXT);
        hit(x, y, size, size, false, b -> action.run(), null);
    }

    private static boolean mouseDown() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), 0) == GLFW.GLFW_PRESS;
    }

    /** Critically-damped-ish spring step; returns the new value and updates velocity in v[0]. */
    private static float spring(float value, float target, float[] v, int vi, float dt, float stiffness, float damping) {
        float accel = (target - value) * stiffness - v[vi] * damping;
        v[vi] += accel * dt;
        return value + v[vi] * dt;
    }

    // =====================================================================
    //  sidebar
    // =====================================================================
    private final float[] tabVel = new float[1];

    private void drawSidebar(GuiGraphicsExtractor g, int ix, int top, int bottom, int sideW, int accent, int accent2, float dt, long ms) {
        category = Math.max(0, Math.min(category, Math.max(0, cats.size() - 1)));

        int px = ix + u(12), pw = sideW - u(16), py = top + u(10), ph = bottom - top - u(20);
        glass(g, px, py, pw, ph, u(18), 0x0CFFFFFF, 0.3f);
        text(g, px + u(14), py + u(16), "Categories", Theme.DIM, true);

        int tabH = u(32), gap = u(4);
        int x = px + u(6), w = pw - u(12);
        int y0 = py + u(30);

        // the selection is a glass drop that springs between tabs (with a little overshoot)
        boolean searching = searching();
        float target = y0 + category * (tabH + gap);
        if (tabY < 0) tabY = target;
        tabY = spring(tabY, target, tabVel, 0, dt, 260f, 22f);
        int ty = Math.round(tabY);
        float stretch = Math.min(1f, Math.abs(tabVel[0]) / (u(900)));
        int dropH = tabH + Math.round(stretch * u(8));
        if (!searching) {
            if (FANCY) roundFast(g, x - u(2), ty - u(2) - (dropH - tabH) / 2, w + u(4), dropH + u(4), u(14), alpha(accent, 0x1C));
            glassTint(g, x, ty - (dropH - tabH) / 2, w, dropH, tabH / 2, accent, 0.55f);
        }

        for (int i = 0; i < cats.size(); i++) {
            int y = y0 + i * (tabH + gap);
            boolean sel = i == category && !searching;
            boolean hov = inside(mx, my, x, y, w, tabH);
            if (!sel && hov) round(g, x, y, w, tabH, tabH / 2, 0x12FFFFFF);

            int tx = x + u(12) + (hov && !sel ? u(2) : 0);
            icon(g, tx + u(6), y + tabH / 2, categoryIcon(cats.get(i)), sel ? 0xFFFFFFFF : hov ? Theme.SUBTEXT : Theme.MUTED);
            tx += u(20);
            text(g, tx, y + tabH / 2, cats.get(i), sel ? 0xFFFFFFFF : Theme.SUBTEXT, sel);
            int countW = u(20), countH = u(16);
            round(g, x + w - u(8) - countW, y + (tabH - countH) / 2, countW, countH, countH / 2, sel ? 0x30FFFFFF : 0x14FFFFFF);
            textC(g, x + w - u(8) - countW / 2, y + tabH / 2, catCountText[i], sel ? 0xFFFFFFFF : Theme.MUTED, false);

            final int idx = i;
            hit(x, y, w, tabH, false, b -> {
                if (category != idx) catNs = System.nanoTime();
                category = idx;
                GuiPreferences.setLastCategory(cats.get(idx));
                scrollTarget = 0;
                scroll = 0;
                search = "";
                searchFocused = false;
            }, null);
        }

        // status: active modules, as a small glass gauge
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
            activeText = active + " of " + total + " active";
        }
        int cy = py + ph - u(30);
        int cx = px + u(16);
        if (active > 0 && FANCY) {
            float pulse = (ms % 1600L) / 1600f;
            circle(g, cx, cy, u(3) + Math.round(u(6) * pulse), alpha(Theme.OK, Math.round(70 * (1f - pulse))));
        }
        circle(g, cx, cy, u(3), active > 0 ? Theme.OK : Theme.DIM);
        text(g, cx + u(10), cy, activeText, active > 0 ? Theme.SUBTEXT : Theme.MUTED, false);

        int pbx = px + u(12), pbw = pw - u(24), pbh = u(5), pby = cy + u(12);
        float ratio = total > 0 ? active / (float) total : 0f;
        activeBar += (ratio - activeBar) * Math.min(1f, dt * 8f);
        if (Math.abs(ratio - activeBar) < 0.002f) activeBar = ratio;
        round(g, pbx, pby, pbw, pbh, pbh / 2, 0x22FFFFFF);
        int pfw = Math.round(pbw * activeBar);
        if (pfw > 0) {
            pfw = Math.max(pbh, pfw);
            pillGrad(g, pbx, pby, pfw, pbh, accent, accent2);
            shine(g, pbx, pfw, pby, pbh, pbx + Math.round((((ms / 1300f) % 1.5f) - 0.25f) * pfw), u(20), 0x90);
        }
    }

    @Override
    public void removed() {
        ModuleConfig.saveNow();
        super.removed();
    }

    private void drawDetachedWindows(GuiGraphicsExtractor g, int pw, int ph, float dt, long ms, long nowNs,
                                     float open, int accent, int accent2) {
        int barH = u(36), barW = u(320), barX = (pw - barW) / 2, barY = u(12);
        shadow(g, barX, barY, barW, barH, barH / 2, u(10), 0x50);
        glass(g, barX, barY, barW, barH, barH / 2, alpha(0x0E1119, 0xB4), 1f);
        icon(g, barX + u(18), barY + barH / 2, Icons.TABLE_COLUMNS, accent);
        text(g, barX + u(32), barY + barH / 2, "Category windows", Theme.TEXT, true);
        button(g, barX + barW - u(118), barY + u(6), u(108), barH - u(12), "Tabbed view", accent,
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
        shadow(g, x, y, w, h, u(18), u(14), 0x60);
        glass(g, x, y, w, h, u(18), alpha(0x0E1119, 0xB4), 1f);
        fill(g, x + u(14), y + headerH, w - u(28), Math.max(1, S / 2), 0x16FFFFFF);
        icon(g, x + u(16), y + headerH / 2, categoryIcon(cat), accent);
        text(g, x + u(30), y + headerH / 2, cat, Theme.TEXT, true);
        textR(g, x + w - u(14), y + headerH / 2, moduleCountLabel(mods.size()), Theme.MUTED, false);

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
            round(g, vx + vw + u(2), barY, u(3), barH, u(2), 0x50FFFFFF);
        }
    }

    // =====================================================================
    //  content (cards generated from ModuleRegistry)
    // =====================================================================
    private void drawContent(GuiGraphicsExtractor g, int ix, int iw, int top, int bottom,
                             int sideW, int accent, int accent2, float dt, long ms, long nowNs) {
        if (cats.isEmpty() || category >= catMods.size()) return;
        boolean searching = searching();
        String cat = searching ? "Search results" : cats.get(category);
        List<GuiModule> mods = searching ? searchResults() : catMods.get(category);

        vx = ix + sideW + u(10);
        vw = iw - sideW - u(28);
        vy = top + u(40);
        vh = bottom - vy - u(8);

        text(g, vx + u(2), top + u(22), cat, Theme.TEXT, true);
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

            int hdr = u(54);
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
        if (mods.isEmpty() && searching) {
            icon(g, vx + vw / 2, vy + u(40), Icons.MAGNIFYING_GLASS, Theme.DIM);
            textC(g, vx + vw / 2, vy + u(62), "No modules match \"" + search.trim() + "\"", Theme.MUTED, false);
        }

        if (contentHeight > vh) {
            int barH = Math.max(u(24), Math.round(vh * (vh / contentHeight)));
            int barY = vy + Math.round((vh - barH) * (scroll / Math.max(1f, contentHeight - vh)));
            round(g, vx + vw + u(6), barY, u(3), barH, u(2), 0x50FFFFFF);
        }
    }

    private int rowHeight(Setting s) {
        if (s instanceof Setting.Num) return u(42);
        if (s instanceof Setting.Mode || s instanceof Setting.Text || s instanceof Setting.Action) return u(34);
        return u(30);
    }

    private int settingsHeight(GuiModule m) {
        List<Setting> list = m.settings();
        if (list.isEmpty()) return 0;
        int h = u(8) + u(6) + u(30);
        for (Setting s : list) h += rowHeight(s);
        return h;
    }

    private static boolean allDefault(GuiModule m) {
        for (Setting s : m.settings()) if (!s.isDefault()) return false;
        return true;
    }

    private final float[] scratchVel = new float[1];

    private void drawCard(GuiGraphicsExtractor g, GuiModule m, CardState st, int x, int y, int w,
                          int hdr, int cardH, int accent, int accent2, float dt, long ms) {
        boolean on = m.isEnabled();
        if (st.initials == null) st.initials = initials(m.name);
        boolean hov = inside(mx, my, x, y, w, hdr) && inside(mx, my, vx, vy, vw, vh);
        st.hover += ((hov ? 1f : 0f) - st.hover) * Math.min(1f, dt * 14f);
        // the enable state springs, so the switch knob and glow overshoot slightly like liquid
        scratchVel[0] = st.liftV;
        st.enable = spring(st.enable, on ? 1f : 0f, scratchVel, 0, dt, 220f, 18f);
        st.liftV = scratchVel[0];
        float en = Math.max(0f, Math.min(1f, st.enable));

        if (en > 0.02f && FX >= 2) roundFast(g, x - u(3), y - u(2), w + u(6), cardH + u(6), u(18), alpha(accent, (int) (0x16 * en)));
        int body = lerp(lerp(0x10FFFFFF, 0x1CFFFFFF, st.hover), alpha(accent, 0x2A), 0.55f * en);
        glass(g, x, y, w, cardH, u(16), body, 0.35f + 0.45f * st.hover + 0.2f * en);

        if (FANCY && st.hover > 0.02f) {
            // a lens of light under the cursor, confined to the header strip
            shineC(g, x + u(10), w - u(20), y + S, hdr - 2 * S, mx, u(110), (int) (0x16 * st.hover), 0xFFFFFF);
        }

        // icon tile: frosted square that fills with accent when enabled
        int tile = u(32);
        int tx0 = x + u(12), ty0 = y + (hdr - tile) / 2;
        if (FX >= 2 && en > 0.02f) glow(g, tx0 + tile / 2, ty0 + tile / 2, u(24), accent, 3, (int) (12 * en));
        round(g, tx0, ty0, tile, tile, u(10), lerp(0x18FFFFFF, alpha(accent, 0x90), en));
        if (FANCY) round(g, tx0 + S, ty0 + S, tile - 2 * S, tile / 2 - S, u(9), 0x18FFFFFF, true, false);
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
        else textC(g, tx0 + tile / 2, ty0 + tile / 2, st.initials, lerp(Theme.MUTED, 0xFFFFFFFF, en), true);

        // toggle
        int tw = u(40), th = u(22);
        int sx = x + w - u(14) - tw, sy = y + (hdr - th) / 2;
        if (en > 0.02f && FX >= 2) glow(g, sx + tw / 2, sy + th / 2, u(24), accent, 3, (int) (10 * en));
        toggle(g, sx, sy, tw, th, st.enable, accent);

        List<Setting> moduleSettings = m.settings();
        if (!moduleSettings.isEmpty()) {
            int ccx = sx - u(22);
            if (st.hover > 0.05f) circle(g, ccx, y + hdr / 2, u(11), alpha(0xFFFFFF, (int) (0x18 * st.hover)));
            chevron(g, ccx, y + hdr / 2, u(4), st.open, lerp(Theme.MUTED, 0xFFFFFFFF, st.hover));
        }

        // keybind chip: always shown when bound or listening, otherwise only on hover
        boolean listening = binding == m;
        int bh = u(18), by = y + (hdr - bh) / 2;
        String bindLabel = listening ? "PRESS A KEY OR MOUSE" : m.hasKey() ? Keybinds.keyName(m.key()) : "BIND";
        int bw = Math.max(u(34), textWidth(bindLabel, false) + u(16));
        int bx = (moduleSettings.isEmpty() ? sx - u(10) : sx - u(40)) - bw;
        boolean bindHov = inside(mx, my, bx, by, bw, bh) && inside(mx, my, vx, vy, vw, vh);
        boolean showBind = listening || m.hasKey() || st.hover > 0.05f;
        if (showBind) {
            float pulse = listening ? 0.55f + 0.45f * (float) Math.sin(ms / 140.0) : 0f;
            round(g, bx, by, bw, bh, bh / 2, listening ? alpha(accent, (int) (0x30 + 0x40 * pulse)) : bindHov ? 0x28FFFFFF : 0x16FFFFFF);
            outline(g, bx, by, bw, bh, bh / 2, Math.max(1, S / 2), listening ? alpha(accent, 0xE0) : 0x30FFFFFF, listening ? alpha(accent, 0x80) : 0x10FFFFFF);
            int labelCol = listening ? accent : m.hasKey() ? (bindHov ? Theme.TEXT : Theme.SUBTEXT) : (bindHov ? Theme.SUBTEXT : Theme.DIM);
            textC(g, bx + bw / 2, by + bh / 2, bindLabel, labelCol, false);
        }

        int nameX = x + u(52);
        int nameMax = Math.max(0, (showBind ? bx : moduleSettings.isEmpty() ? sx : sx - u(36)) - u(8) - nameX);
        boolean showDescription = w >= u(320);
        text(g, nameX, y + (showDescription ? u(19) : hdr / 2), fitText(m.name, nameMax, true), Theme.TEXT, true);
        if (showDescription) text(g, nameX, y + u(35), fitText(m.description, nameMax, false), Theme.MUTED, false);

        // hover tooltips: the bind chip explains itself; narrow cards show their description
        if (hov) {
            if (hoverModule != m) { hoverModule = m; hoverSince = System.currentTimeMillis(); }
            boolean settled = System.currentTimeMillis() - hoverSince > 450;
            if (bindHov && showBind) setTooltip(listening ? "Press a key or a mouse button (middle/side). Esc cancels, Backspace unbinds." : "Click to set a toggle key");
            else if (settled && !showDescription) setTooltip(m.description);
        } else if (hoverModule == m) {
            hoverModule = null;
        }

        hit(x, y, w, hdr, true, b -> {
            if (b == 1 && !moduleSettings.isEmpty()) st.open = !st.open;
            else if (b == 2) binding = m;
            else if (b == 0) {
                m.toggle();
                activeStamp = -1000L;
                if (m.isEnabled()) Notifications.success(m.name, "Enabled");
                else Notifications.info(m.name, "Disabled");
            }
        }, null);
        if (!moduleSettings.isEmpty()) {
            hit(sx - u(36), y, u(32), hdr, true, b -> st.open = !st.open, null);
        }
        if (showBind) {
            hit(bx, by, bw, bh, true, b -> {
                if (b == 1 || b == 2) { m.setKey(GuiModule.NO_KEY); binding = null; }
                else binding = bindingBeforeClick == m ? null : m;
            }, null);
        }

        if (st.expand > 0.01f && !moduleSettings.isEmpty()) {
            boolean clip = st.expand < 0.999f;
            if (clip) g.enableScissor(x + S, y + hdr, x + w - S, y + cardH - S);
            fill(g, x + u(14), y + hdr, w - u(28), Math.max(1, S / 2), 0x18FFFFFF);
            boolean interactive = st.expand > 0.9f;
            int ry = y + hdr + u(8);
            for (Setting s : moduleSettings) {
                int rh = rowHeight(s);
                drawSetting(g, s, x, ry, w, rh, accent, accent2, interactive, ms);
                ry += rh;
            }
            drawCardFooter(g, m, x, ry, w, accent, interactive);
            if (clip) g.disableScissor();
        }
    }

    /** Keybind summary and a reset-all button under a module's settings. */
    private void drawCardFooter(GuiGraphicsExtractor g, GuiModule m, int x, int y, int w, int accent, boolean interactive) {
        int pad = u(14), rh = u(30), cy = y + rh / 2 + u(2);
        fill(g, x + u(14), y + u(2), w - u(28), Math.max(1, S / 2), 0x10FFFFFF);
        icon(g, x + pad + u(5), cy, Icons.KEYBOARD, Theme.DIM);
        text(g, x + pad + u(16), cy, m.hasKey() ? "Toggle key  " + Keybinds.keyName(m.key()) : "No toggle key  (middle-click to bind)",
                Theme.DIM, false);
        boolean dirty = !allDefault(m);
        int bw = u(74), bh = u(18), bx = x + w - pad - bw, by = cy - bh / 2;
        boolean hov = interactive && dirty && inside(mx, my, bx, by, bw, bh) && inside(mx, my, vx, vy, vw, vh);
        glass(g, bx, by, bw, bh, bh / 2, hov ? alpha(Theme.DANGER, 0x60) : 0x12FFFFFF, hov ? 0.9f : 0.3f);
        icon(g, bx + u(12), cy, Icons.ROTATE_LEFT, dirty ? (hov ? Theme.DANGER : Theme.MUTED) : Theme.DIM);
        text(g, bx + u(22), cy, "Reset all", dirty ? (hov ? Theme.TEXT : Theme.SUBTEXT) : Theme.DIM, false);
        if (interactive && dirty) hit(bx, by, bw, bh, true, b -> {
            ModuleConfig.resetModule(m);
            Notifications.info(m.name, "Settings reset to defaults");
        }, null);
    }

    /** Small reset glyph drawn after a label when the hovered setting differs from its default. */
    private void resetGlyph(GuiGraphicsExtractor g, Setting s, int labelEndX, int cy, int rowX, int rowY, int rowW, int rowH,
                            boolean interactive) {
        if (!interactive || s.isDefault() || !inside(mx, my, rowX, rowY, rowW, rowH) || !inside(mx, my, vx, vy, vw, vh)) return;
        int cx = labelEndX + u(9), size = u(14);
        boolean hov = inside(mx, my, cx - size / 2, cy - size / 2, size, size);
        icon(g, cx, cy, Icons.ROTATE_LEFT, hov ? Theme.DANGER : Theme.DIM);
        if (hov) setTooltip("Reset to default");
        hit(cx - size / 2, cy - size / 2, size, size, true, b -> s.reset(), null);
    }

    private float[] anim(Setting s) {
        return settingAnim.computeIfAbsent(s, k -> new float[]{Float.NaN, 0f, 0f});
    }

    private void drawSetting(GuiGraphicsExtractor g, Setting s, int x, int y, int w, int rh,
                             int accent, int accent2, boolean interactive, long ms) {
        int pad = u(16);
        int lx = x + pad, rx = x + w - pad;
        float dt = Math.min(0.05f, 1f / Math.max(30, fps));
        float[] a = anim(s);
        boolean rowHov = interactive && inside(mx, my, x + u(6), y, w - u(12), rh) && inside(mx, my, vx, vy, vw, vh);
        a[1] += ((rowHov ? 1f : 0f) - a[1]) * Math.min(1f, dt * 14f);
        if (a[1] > 0.02f) round(g, x + u(8), y + S, w - u(16), rh - 2 * S, u(10), alpha(0xFFFFFF, (int) (0x0C * a[1])));

        if (s instanceof Setting.Bool b) {
            int tw = u(34), th = u(19);
            if (Float.isNaN(a[0])) a[0] = b.get() ? 1f : 0f;
            a[0] = spring(a[0], b.get() ? 1f : 0f, a, 2, dt, 240f, 19f);
            String label = fitText(b.name, Math.max(0, rx - tw - u(28) - lx), false);
            text(g, lx, y + rh / 2, label, Theme.SUBTEXT, false);
            toggle(g, rx - tw, y + (rh - th) / 2, tw, th, a[0], accent);
            if (interactive) hit(x, y, w, rh, true, btn -> { if (btn == 2) b.reset(); else b.toggle(); }, null);
            resetGlyph(g, s, lx + textWidth(label, false), y + rh / 2, x, y, w, rh, interactive);

        } else if (s instanceof Setting.Mode md) {
            List<String> options = md.options;
            int segH = u(22), segPad = u(10);
            int segW = 0;
            for (String o : options) segW += textWidth(o, false) + segPad * 2;
            int maxSeg = (w - pad * 2) * 3 / 5;
            int labelMax;
            if (options.size() <= 4 && segW <= maxSeg) {
                // segmented control with a sliding glass thumb
                int bx = rx - segW, by = y + (rh - segH) / 2;
                labelMax = Math.max(0, bx - u(28) - lx);
                round(g, bx, by, segW, segH, segH / 2, 0x30000000);
                outline(g, bx, by, segW, segH, segH / 2, Math.max(1, S / 2), 0x14FFFFFF, 0x22FFFFFF);
                int sel = Math.max(0, options.indexOf(md.get()));
                int selX = bx, cx = bx;
                int[] xs = new int[options.size()], ws = new int[options.size()];
                for (int i = 0; i < options.size(); i++) {
                    ws[i] = textWidth(options.get(i), false) + segPad * 2;
                    xs[i] = cx;
                    cx += ws[i];
                }
                selX = xs[sel];
                if (Float.isNaN(a[0])) a[0] = selX - bx;
                a[0] = spring(a[0], selX - bx, a, 2, dt, 300f, 24f);
                int thumbX = bx + Math.round(a[0]);
                int thumbW = ws[sel];
                glassTint(g, thumbX + S, by + S, thumbW - 2 * S, segH - 2 * S, (segH - 2 * S) / 2, accent, 0.65f);
                for (int i = 0; i < options.size(); i++) {
                    boolean isSel = i == sel;
                    boolean oh = interactive && inside(mx, my, xs[i], by, ws[i], segH) && inside(mx, my, vx, vy, vw, vh);
                    textC(g, xs[i] + ws[i] / 2, by + segH / 2, options.get(i), isSel ? 0xFFFFFFFF : oh ? Theme.TEXT : Theme.MUTED, isSel);
                    final String option = options.get(i);
                    if (interactive) hit(xs[i], by, ws[i], segH, true, btn -> { if (btn == 2) md.reset(); else md.set(option); }, null);
                }
            } else {
                int bw = Math.max(u(100), Math.min(u(150), maxOptionWidth(md) + u(40))), bh = segH, bx = rx - bw, by = y + (rh - bh) / 2;
                labelMax = Math.max(0, bx - u(28) - lx);
                boolean hov = interactive && inside(mx, my, bx, by, bw, bh) && inside(mx, my, vx, vy, vw, vh);
                glass(g, bx, by, bw, bh, bh / 2, hov ? 0x26FFFFFF : 0x16FFFFFF, hov ? 0.9f : 0.4f);
                textC(g, bx + bw / 2, by + bh / 2, fitText(md.get(), bw - u(36), false), 0xFFFFFFFF, false);
                icon(g, bx + u(11), by + bh / 2, Icons.ANGLE_LEFT, hov ? accent : Theme.MUTED);
                icon(g, bx + bw - u(11), by + bh / 2, Icons.ANGLE_RIGHT, hov ? accent : Theme.MUTED);
                if (interactive) hit(bx, by, bw, bh, true, btn -> {
                    if (btn == 2) md.reset();
                    else md.cycle(btn == 1 || mx < bx + bw / 3 ? -1 : 1);
                }, null);
            }
            String label = fitText(md.name, labelMax, false);
            text(g, lx, y + rh / 2, label, Theme.SUBTEXT, false);
            resetGlyph(g, s, lx + textWidth(label, false), y + rh / 2, x, y, w, rh, interactive);

        } else if (s instanceof Setting.Num n) {
            String shown = n.display();
            String label = fitText(n.name, Math.max(0, rx - textWidth(shown, true) - u(40) - lx), false);
            text(g, lx, y + u(12), label, Theme.SUBTEXT, false);
            int valW = textWidth(shown, true) + u(14), valH = u(18);
            round(g, rx - valW, y + u(12) - valH / 2, valW, valH, valH / 2, 0x1CFFFFFF);
            textC(g, rx - valW / 2, y + u(12), shown, 0xFFFFFFFF, true);

            int trackX = lx, trackW = rx - lx, trackH = u(5), trackY = y + u(27);
            float frac = (float) n.fraction();
            if (Float.isNaN(a[0])) a[0] = frac;
            a[0] = spring(a[0], frac, a, 2, dt, 420f, 32f);
            float shownFrac = Math.max(0f, Math.min(1f, a[0]));
            round(g, trackX, trackY, trackW, trackH, trackH / 2, 0x26FFFFFF);
            int fw = Math.max(trackH, Math.round(trackW * shownFrac));
            pillGrad(g, trackX, trackY, fw, trackH, accent, accent2);
            boolean dragging = draggingSlider == n && activeDrag != null;
            int kx = trackX + Math.round(trackW * shownFrac), ky = trackY + trackH / 2;
            boolean knobHot = dragging || (interactive && inside(mx, my, trackX - u(8), trackY - u(10), trackW + u(16), u(26))
                    && inside(mx, my, vx, vy, vw, vh));
            int kw = knobHot ? u(22) : u(18), kh = knobHot ? u(16) : u(14);
            if (FANCY) roundFast(g, kx - kw / 2 - S, ky - kh / 2 + S, kw + 2 * S, kh + S, kh / 2, 0x40000000);
            if (FANCY && knobHot) glow(g, kx, ky, u(16), accent, 3, 12);
            round(g, kx - kw / 2, ky - kh / 2, kw, kh, kh / 2, 0xFFFFFFFF);
            round(g, kx - kw / 2 + S, ky, kw - 2 * S, kh / 2 - S, kh / 2, 0x16000000, false, true);
            if (dragging) {
                // value bubble floating above the knob while dragging
                int bw = textWidth(shown, true) + u(16), bh = u(20);
                int bxx = Math.max(x + S, Math.min(x + w - bw - S, kx - bw / 2)), byy = ky - kh / 2 - bh - u(5);
                glassTint(g, bxx, byy, bw, bh, bh / 2, accent, 0.8f);
                textC(g, bxx + bw / 2, byy + bh / 2, shown, 0xFFFFFFFF, true);
            }

            if (interactive) {
                hit(trackX - u(8), trackY - u(10), trackW + u(16), u(26), true, btn -> {
                    if (btn == 2) n.reset();
                    else if (btn == 0) draggingSlider = n;
                }, (px, py) -> n.setFraction((px - trackX) / (double) trackW));
                if (rowHov) hoveredSlider = n;
                if (knobHot && !dragging) setTooltip("Drag, or hold Shift and scroll for exact steps");
            }
            resetGlyph(g, s, lx + textWidth(label, false), y + u(12), x, y, w, rh, interactive);
        } else if (s instanceof Setting.Text field) {
            int bw = Math.min(u(160), Math.max(u(80), (w - u(42)) / 2)), bh = u(23), bx = rx - bw, by = y + (rh - bh) / 2;
            String label = fitText(field.name, Math.max(0, bx - u(28) - lx), false);
            text(g, lx, y + rh / 2, label, Theme.SUBTEXT, false);
            boolean focused = focusedText == field;
            boolean fieldHov = interactive && inside(mx, my, bx, by, bw, bh) && inside(mx, my, vx, vy, vw, vh);
            if (focused && FANCY) roundFast(g, bx - u(3), by - u(3), bw + u(6), bh + u(6), bh / 2 + u(3), alpha(accent, 0x1C));
            round(g, bx, by, bw, bh, u(9), focused ? 0x50000000 : 0x34000000);
            outline(g, bx, by, bw, bh, u(9), Math.max(1, S / 2),
                    focused ? alpha(accent, 0xE0) : fieldHov ? 0x2AFFFFFF : 0x12FFFFFF,
                    focused ? alpha(accent, 0x90) : fieldHov ? 0x3AFFFFFF : 0x22FFFFFF);
            String value = field.get();
            int innerW = bw - u(18);
            if (focused && selectAllText && !value.isEmpty()) {
                String shownSel = fitTail(value, innerW);
                fill(g, bx + u(8), by + u(5), textWidth(shownSel, false) + S, bh - u(10), alpha(accent, 0x60));
            }
            if (value.isEmpty() && !focused) {
                text(g, bx + u(9), by + bh / 2, "Click to type", Theme.DIM, false);
            } else {
                String visible = fitTail(value, innerW);
                text(g, bx + u(9), by + bh / 2, visible, focused ? 0xFFFFFFFF : Theme.SUBTEXT, false);
                if (focused && (System.currentTimeMillis() / 500 % 2 == 0))
                    fill(g, bx + u(9) + textWidth(visible, false) + S, by + bh / 2 - u(7), S, u(14), accent);
            }
            if (interactive) hit(bx, by, bw, bh, true, btn -> {
                if (btn == 2) field.reset();
                else { focusedText = field; searchFocused = false; }
            }, null);
            resetGlyph(g, s, lx + textWidth(label, false), y + rh / 2, x, y, w, rh, interactive);
        } else if (s instanceof Setting.Action action) {
            int bw = Math.min(Math.max(u(136), textWidth(action.name, false) + u(30)), w - u(32)), bh = u(24), bx = rx - bw, by = y + (rh - bh) / 2;
            boolean hov = interactive && inside(mx, my, bx, by, bw, bh) && inside(mx, my, vx, vy, vw, vh);
            boolean pressed = hov && activeDrag == null && mouseDown();
            int ay = by + (pressed ? S : 0);
            if (hov && FANCY) roundFast(g, bx - u(3), ay - u(2), bw + u(6), bh + u(6), bh / 2 + u(3), alpha(accent, 0x22));
            glassTint(g, bx, ay, bw, bh, bh / 2, accent, pressed ? 1f : hov ? 0.7f : 0.25f);
            textC(g, bx + bw / 2, ay + bh / 2, fitText(action.name, bw - u(16), false), 0xFFFFFFFF, hov);
            if (interactive) hit(bx, by, bw, bh, true, btn -> action.run(), null);
        }
    }

    // =====================================================================
    //  footer + toasts
    // =====================================================================
    private void drawFooter(GuiGraphicsExtractor g, int ix, int iw, int footTop, int footH) {
        int cy = footTop + footH / 2;
        int x = ix + u(22);
        x = hint(g, x, cy, "LMB", "toggle");
        x = hint(g, x, cy, "RMB", "settings");
        x = hint(g, x, cy, "MMB", "bind");
        hint(g, x, cy, "Type", "search");
        int buttonX = ix + iw - u(118), buttonY = cy - u(10);
        textR(g, buttonX - u(12), cy, costText + "   " + clockText, Theme.MUTED, false);
        button(g, buttonX, buttonY, u(100), u(20), "Windows", Theme.accent(),
                () -> GuiPreferences.setSeparateCategoryWindows(true));
    }

    /** A keycap-style hint, returns the x after it. */
    private int hint(GuiGraphicsExtractor g, int x, int cy, String key, String what) {
        int kw = textWidth(key, true) + u(10), kh = u(16);
        round(g, x, cy - kh / 2, kw, kh, u(5), 0x1CFFFFFF);
        textC(g, x + kw / 2, cy, key, Theme.SUBTEXT, true);
        text(g, x + kw + u(5), cy, what, Theme.MUTED, false);
        return x + kw + u(5) + textWidth(what, false) + u(14);
    }

    private void toast(String title, String msg, boolean err) {
        if (err) Notifications.error(title, msg);
        else Notifications.info(title, msg);
    }

    private void setTooltip(String text) {
        if (text == null || text.isBlank()) return;
        tooltip = text;
        tooltipX = mx;
        tooltipY = my;
    }

    private void drawTooltip(GuiGraphicsExtractor g, int pw, int ph, int accent) {
        if (tooltip == null) return;
        String shown = fitText(tooltip, u(300), false);
        int w = textWidth(shown, false) + u(20), h = u(24);
        int x = Math.min(pw - w - u(4), tooltipX + u(12));
        int y = tooltipY + u(16);
        if (y + h > ph - u(4)) y = tooltipY - h - u(8);
        shadow(g, x, y, w, h, h / 2, u(8), 0x60);
        glass(g, x, y, w, h, h / 2, alpha(0x14161F, 0xE6), 1f);
        text(g, x + u(10), y + h / 2, shown, Theme.TEXT, false);
    }

    private int maxOptionWidth(Setting.Mode mode) {
        int max = 0;
        for (String option : mode.options) max = Math.max(max, textWidth(option, false));
        return max;
    }

    /** Longest suffix of value that fits maxWidth (text fields show the end being typed). */
    private static String fitTail(String value, int maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0) return "";
        if (textWidth(value, false) <= maxWidth) return value;
        int start = 0;
        while (start < value.length() && textWidth(value.substring(start), false) > maxWidth) start++;
        return value.substring(start);
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
        searchFocused = false;
        // listening for a bind: middle and side mouse buttons bind (left/right stay for clicking)
        if (binding != null && event.button() >= 2) {
            GuiModule module = binding;
            binding = null;
            module.setKey(Keybinds.MOUSE_BASE + event.button());
            Notifications.success(module.name, "Bound to " + Keybinds.keyName(module.key()));
            return true;
        }
        bindingBeforeClick = binding;
        binding = null;
        int px = Math.round((float) event.x() * gs / uiOverride), py = Math.round((float) event.y() * gs / uiOverride);
        int button = event.button();
        for (int i = hitCount - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(px, py)) continue;
            if (h.clipped && !inside(px, py, h.clipX, h.clipY, h.clipW, h.clipH)) continue;
            if (h.drag != null && button == 0) {
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
        if (swallowNextChar) { swallowNextChar = false; return true; }
        if (binding != null) return true;
        if (focusedText == null) {
            // Typing anywhere searches, like a command palette.
            if (GuiPreferences.separateCategoryWindows() || !event.isAllowedChatCharacter()) return super.charTyped(event);
            if (!searchFocused && Character.isWhitespace(event.codepoint())) return super.charTyped(event);
            searchFocused = true;
            if (search.length() < 48) search += event.codepointAsString();
            scrollTarget = 0;
            return true;
        }
        if (!event.isAllowedChatCharacter()) return super.charTyped(event);
        String value = selectAllText ? "" : focusedText.get();
        selectAllText = false;
        if (value.length() < focusedText.maxLength) focusedText.set(value + event.codepointAsString());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        swallowNextChar = false;
        int key = event.key();
        boolean control = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (binding != null) {
            GuiModule module = binding;
            binding = null;
            if (key == GLFW.GLFW_KEY_ESCAPE) return true;
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                module.setKey(GuiModule.NO_KEY);
                Notifications.info(module.name, "Keybind cleared");
            } else {
                module.setKey(key);
                Notifications.success(module.name, "Bound to " + Keybinds.keyName(key));
            }
            swallowNextChar = true;
            return true;
        }
        if (focusedText != null) {
            if (control && key == GLFW.GLFW_KEY_A) {
                selectAllText = true;
                return true;
            }
            if (control && key == GLFW.GLFW_KEY_V) {
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
            if (control && (key == GLFW.GLFW_KEY_C || key == GLFW.GLFW_KEY_X)) {
                if (selectAllText) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(focusedText.get());
                    if (key == GLFW.GLFW_KEY_X) focusedText.set("");
                    selectAllText = false;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                String value = focusedText.get();
                if (selectAllText || control) focusedText.set("");
                else if (!value.isEmpty()) focusedText.set(value.substring(0, value.length() - 1));
                selectAllText = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                focusedText = null;
                selectAllText = false;
                return true;
            }
            return true;
        }
        if (control && key == GLFW.GLFW_KEY_F && !GuiPreferences.separateCategoryWindows()) {
            searchFocused = true;
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (control) search = "";
                else if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                return true;
            }
            if (control && key == GLFW.GLFW_KEY_V) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clipboard != null) search = (search + clipboard.replaceAll("\\s+", " ")).substring(0, Math.min(48, search.length() + clipboard.length()));
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                // Enter toggles the top result, so "type, Enter" works like a launcher.
                List<GuiModule> results = searching() ? searchResults() : List.of();
                if (!results.isEmpty()) {
                    GuiModule top = results.getFirst();
                    top.toggle();
                    if (top.isEnabled()) Notifications.success(top.name, "Enabled");
                    else Notifications.info(top.name, "Disabled");
                }
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && (searchFocused || !search.isEmpty())) {
            if (!search.isEmpty()) search = "";
            searchFocused = false;
            scrollTarget = 0;
            return true;
        }
        if (!searchFocused && Anthrax.guiKey != null && Anthrax.guiKey.matches(event)) {
            onClose();
            return true;
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
        draggingSlider = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            Setting.Num slider = hoveredSlider;
            if (slider != null) {
                slider.nudge(scrollY > 0 ? 1 : -1);
                return true;
            }
        }
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
            case "Kill Aura" -> new ItemStack(Items.NETHERITE_SWORD);
            case "Trigger Bot" -> new ItemStack(Items.TRIPWIRE_HOOK);
            case "Auto Weapon" -> new ItemStack(Items.DIAMOND_AXE);
            case "Velocity" -> new ItemStack(Items.SLIME_BALL);
            case "Auto Eat" -> new ItemStack(Items.GOLDEN_APPLE);
            case "Surround" -> new ItemStack(Items.OBSIDIAN);
            case "Target HUD" -> new ItemStack(Items.PLAYER_HEAD);
            case "Render Method" -> new ItemStack(Items.SPYGLASS);
            case "Player Dig Safety" -> new ItemStack(Items.IRON_PICKAXE);
            case "Time Changer", "FPS Chart" -> new ItemStack(Items.CLOCK);
            case "Show Player List" -> new ItemStack(Items.PLAYER_HEAD);
            case "Player ESP" -> new ItemStack(Items.PLAYER_HEAD);
            case "Advanced ESP" -> new ItemStack(Items.SPYGLASS);
            case "Seed-Ray" -> new ItemStack(Items.DIAMOND_ORE);
            case "Base Finder" -> new ItemStack(Items.RECOVERY_COMPASS);
            case "Waypoints" -> new ItemStack(Items.LODESTONE);
            case "Amethyst ESP" -> new ItemStack(Items.AMETHYST_CLUSTER);
            case "Auto Armor" -> new ItemStack(Items.NETHERITE_CHESTPLATE);
            case "Nametags" -> new ItemStack(Items.NAME_TAG);
            case "Tracers" -> new ItemStack(Items.LEAD);
            case "Storage ESP" -> new ItemStack(Items.SHULKER_BOX);
            case "Hole ESP" -> new ItemStack(Items.OBSIDIAN);
            case "Trajectories" -> new ItemStack(Items.BOW);
            case "Logout Spots" -> new ItemStack(Items.OAK_DOOR);
            case "Breadcrumbs" -> new ItemStack(Items.BREAD);
            case "Fullbright" -> new ItemStack(Items.GLOWSTONE);
            case "Freecam" -> new ItemStack(Items.ENDER_EYE);
            case "Zoom" -> new ItemStack(Items.SPYGLASS);
            case "Friends" -> new ItemStack(Items.TOTEM_OF_UNDYING);
            case "Commands" -> new ItemStack(Items.COMMAND_BLOCK);
            case "Config Profiles" -> new ItemStack(Items.WRITABLE_BOOK);
            case "HUD" -> new ItemStack(Items.ITEM_FRAME);
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
