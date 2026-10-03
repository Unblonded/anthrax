package unblonded.anthrax.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import unblonded.anthrax.gui.GuiModule;
import unblonded.anthrax.gui.Modules;
import unblonded.anthrax.gui.Setting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntConsumer;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Modular client GUI. Everything inside the content area is generated from {@link Modules};
 * to add a module or setting you never touch this file.
 */
public class AnthraxScreen extends Screen {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private interface Drag { void to(int mouseX); }

    private record Hit(int x, int y, int w, int h, boolean clipped, IntConsumer press, Drag drag) {
        boolean contains(int px, int py) { return inside(px, py, x, y, w, h); }
    }

    private record Toast(String title, String msg, boolean err, long born) {}

    private static final class CardState {
        boolean open;
        float expand, hover, enable;
    }

    private static final class Particle { float x, y, vx, vy; }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<GuiModule, CardState> states = new IdentityHashMap<>();
    private final List<Toast> toasts = new ArrayList<>();

    private int category = 0;
    private float scroll, scrollTarget, contentHeight;
    private float tabY = -1;
    private Drag activeDrag;

    private float gs = 1f;
    private int mx, my;                    // mouse in real pixels
    private int vx, vy, vw, vh;            // scrollable viewport (real pixels)
    private long lastNs = System.nanoTime();
    private final long openNs = System.nanoTime();
    private long catNs = openNs;
    private final long startMs = System.currentTimeMillis();

    private int frames, fps;
    private long fpsStamp = System.currentTimeMillis();

    private Particle[] particles;
    private int partW, partH;

    public AnthraxScreen() {
        super(Component.literal("Anthrax"));
    }

    // =====================================================================
    //  frame
    // =====================================================================
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        Draw.measureFont = this.font;
        gs = (float) minecraft.getWindow().getGuiScale();
        int pw = Math.round(width * gs), ph = Math.round(height * gs);
        mx = Math.round(mouseX * gs);
        my = Math.round(mouseY * gs);

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;

        frames++;
        long ms = System.currentTimeMillis();
        if (ms - fpsStamp >= 1000) {
            fps = Math.round(frames * 1000f / (ms - fpsStamp));
            frames = 0;
            fpsStamp = ms;
        }

        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            drawAll(g, pw, ph, dt);
        } finally {
            g.pose().popMatrix();
        }
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float i = 1f - t;
        return 1f - i * i * i;
    }

    private void drawAll(GuiGraphicsExtractor g, int pw, int ph, float dt) {
        hits.clear();
        long ms = System.currentTimeMillis() - startMs;
        Theme.tick(System.currentTimeMillis());
        Draw.S = Math.max(1, Math.min(4, Math.min(Math.round(ph / 720f), Math.min(pw / 600, ph / 400))));
        int accent = Theme.accent(), accent2 = Theme.accent2();

        float open = easeOut((System.nanoTime() - openNs) / 1_000_000_000f / 0.4f);

        // ---- backdrop: dim, vignette, scanlines, particle network
        g.fill(0, 0, pw, ph, alpha(0x050509, (int) (0xC8 * open)));
        for (int i = 0; i < 8; i++) {
            int c = alpha(0x000000, (int) ((8 - i) * 9 * open));
            int e = u(i * 3), th = u(3);
            g.fill(0, e, pw, e + th, c);
            g.fill(0, ph - e - th, pw, ph - e, c);
            g.fill(e, 0, e + th, ph, c);
            g.fill(pw - e - th, 0, pw - e, ph, c);
        }
        if (FANCY) drawParticles(g, pw, ph, dt, open, accent, accent2);

        // ---- window geometry
        int ww = u(580), wh = u(380);
        int wx = (pw - ww) / 2, wy = (ph - wh) / 2 + Math.round((1f - open) * u(30));
        int r = u(14), t = S;
        int headerH = u(44), sideW = u(128), footH = u(26);
        int ix = wx + t, iw = ww - 2 * t;
        int footTop = wy + wh - t - footH;
        int bodyTop = wy + headerH;

        // shadow + pulsing accent glow
        for (int i = 3; i >= 1; i--) {
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

        roundBorder(g, wx, wy, ww, wh, r, t, lerp(Theme.LINE, accent, 0.35f), Theme.BG);

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

        drawHeader(g, ix, iw, wy, headerH, accent, accent2, ms);
        drawSidebar(g, ix, bodyTop, footTop, sideW, accent, dt, ms);
        drawContent(g, ix, iw, bodyTop, footTop, sideW, accent, accent2, dt, ms);
        drawFooter(g, ix, iw, footTop, footH);
        drawToasts(g, pw, ph, accent);
    }

    // =====================================================================
    //  backdrop particles
    // =====================================================================
    private void drawParticles(GuiGraphicsExtractor g, int pw, int ph, float dt, float open, int accent, int accent2) {
        if (particles == null || partW != pw || partH != ph) {
            Random rnd = new Random(7);
            particles = new Particle[22];
            for (int i = 0; i < particles.length; i++) {
                Particle p = new Particle();
                p.x = rnd.nextFloat() * pw;
                p.y = rnd.nextFloat() * ph;
                p.vx = (rnd.nextFloat() - 0.5f) * u(22);
                p.vy = (rnd.nextFloat() - 0.5f) * u(22);
                particles[i] = p;
            }
            partW = pw;
            partH = ph;
        }
        int link = u(90), mlink = u(110);
        for (Particle p : particles) {
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.x < 0 || p.x > pw) p.vx = -p.vx;
            if (p.y < 0 || p.y > ph) p.vy = -p.vy;
            fill(g, (int) p.x, (int) p.y, S + 1, S + 1, alpha(accent2, (int) (0xA0 * open)));
        }
        for (int i = 0; i < particles.length; i++) {
            Particle a = particles[i];
            for (int j = i + 1; j < particles.length; j++) {
                Particle b = particles[j];
                float dx = a.x - b.x, dy = a.y - b.y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < link) {
                    dotLine(g, (int) a.x, (int) a.y, (int) b.x, (int) b.y, u(8), S,
                            alpha(accent, (int) ((1 - d / link) * 0x48 * open)));
                }
            }
            float dx = a.x - mx, dy = a.y - my;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < mlink) {
                dotLine(g, (int) a.x, (int) a.y, mx, my, u(7), S,
                        alpha(accent2, (int) ((1 - d / mlink) * 0x90 * open)));
            }
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

        textRainbow(g, ix + u(52), cy - u(7), "ANTHRAX", Theme.hue, 22f, true);
        text(g, ix + u(52), cy + u(8), "combat client", Theme.MUTED, false);

        chip(g, ix + u(172), cy, u(92), "26.2 / FABRIC");
        chip(g, ix + u(272), cy, u(64), LocalTime.now().format(CLOCK));
        chip(g, ix + u(344), cy, u(56), "FPS " + fps);

        int bh = u(22);
        int closeX = ix + iw - u(14) - bh;
        int themeW = u(88);
        int themeX = closeX - u(8) - themeW;

        button(g, themeX, cy - bh / 2, themeW, bh, Theme.NAMES[Theme.idx], accent, () -> {
            Theme.next();
            toast("THEME", Theme.NAMES[Theme.idx], false);
        });
        button(g, closeX, cy - bh / 2, bh, bh, "X", Theme.DANGER, this::onClose);
    }

    private void chip(GuiGraphicsExtractor g, int x, int cy, int w, String s) {
        int h = u(18);
        roundBorder(g, x, cy - h / 2, w, h, h / 2, S, Theme.LINE, 0xFF151521);
        textC(g, x + w / 2, cy, s, Theme.MUTED, false);
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
    private void drawSidebar(GuiGraphicsExtractor g, int ix, int top, int bottom, int sideW, int accent, float dt, long ms) {
        List<String> cats = Modules.categories();
        category = Math.max(0, Math.min(category, Math.max(0, cats.size() - 1)));

        text(g, ix + u(16), top + u(16), "CATEGORIES", Theme.DIM, false);

        int tabH = u(32), gap = u(4);
        int x = ix + u(10), w = sideW - u(20) - S;
        int y0 = top + u(34);

        // sliding highlight
        float target = y0 + category * (tabH + gap);
        if (tabY < 0) tabY = target;
        tabY += (target - tabY) * Math.min(1f, dt * 16f);
        int ty = Math.round(tabY);
        round(g, x, ty, w, tabH, u(8), lerp(Theme.SIDE, accent, 0.16f));
        round(g, x, ty + u(8), u(3), tabH - u(16), u(1), accent);

        for (int i = 0; i < cats.size(); i++) {
            int y = y0 + i * (tabH + gap);
            boolean sel = i == category;
            boolean hov = inside(mx, my, x, y, w, tabH);
            if (!sel && hov) round(g, x, y, w, tabH, u(8), lerp(Theme.SIDE, accent, 0.07f));

            text(g, x + u(14), y + tabH / 2, cats.get(i), sel ? Theme.TEXT : Theme.MUTED, sel);
            textR(g, x + w - u(10), y + tabH / 2, String.valueOf(Modules.inCategory(cats.get(i)).size()), Theme.DIM, false);

            final int idx = i;
            hit(x, y, w, tabH, false, b -> {
                if (category != idx) catNs = System.nanoTime();
                category = idx;
                scrollTarget = 0;
                scroll = 0;
            }, null);
        }

        // status
        long active = Modules.all().stream().filter(GuiModule::isEnabled).count();
        int cy = bottom - u(22);
        int cx = ix + u(20);
        if (active > 0) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(ms / 220.0);
            circle(g, cx, cy, u(6), alpha(Theme.OK, 28 + (int) (22 * pulse)));
            circle(g, cx, cy, u(3), Theme.OK);
        } else {
            circle(g, cx, cy, u(3), Theme.DIM);
        }
        text(g, ix + u(34), cy, active + " ACTIVE", active > 0 ? Theme.OK : Theme.MUTED, false);
    }

    // =====================================================================
    //  content (cards generated from Modules)
    // =====================================================================
    private void drawContent(GuiGraphicsExtractor g, int ix, int iw, int top, int bottom,
                             int sideW, int accent, int accent2, float dt, long ms) {
        List<String> cats = Modules.categories();
        if (cats.isEmpty()) return;
        String cat = cats.get(category);
        List<GuiModule> mods = Modules.inCategory(cat);

        vx = ix + sideW + u(14);
        vw = iw - sideW - u(28);
        vy = top + u(44);
        vh = bottom - vy - u(8);

        text(g, vx, top + u(22), cat, Theme.TEXT, true);
        textR(g, vx + vw, top + u(22), mods.size() + (mods.size() == 1 ? " module" : " modules"), Theme.MUTED, false);

        float maxScroll = Math.max(0, contentHeight - vh);
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
        if (Math.abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget;

        float catT = (System.nanoTime() - catNs) / 1_000_000_000f;

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
        return u(28);
    }

    private int settingsHeight(GuiModule m) {
        if (m.settings().isEmpty()) return 0;
        int h = u(8) + u(6);
        for (Setting s : m.settings()) h += rowHeight(s);
        return h;
    }

    private void drawCard(GuiGraphicsExtractor g, GuiModule m, CardState st, int x, int y, int w,
                          int hdr, int cardH, int accent, int accent2, float dt, long ms) {
        boolean on = m.isEnabled();
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

        // cursor spotlight
        if (st.hover > 0.02f) {
            g.enableScissor(x + S, y + S, x + w - S, y + hdr);
            glow(g, mx, my, u(80), accent, 6, (int) (7 * st.hover));
            g.disableScissor();
        }

        // enabled accent line with moving shine
        if (st.enable > 0.02f) {
            int lx = x + u(14), lw = w - u(28), ly = y + hdr - u(3), lh = Math.max(1, S);
            hgrad(g, lx, ly, lw, lh, alpha(accent, (int) (200 * st.enable)), alpha(accent2, (int) (200 * st.enable)));
            float ph = (((ms + (m.name.hashCode() & 255) * 9L) / 1600f) % 1.5f) - 0.25f;
            shine(g, lx, lw, ly, lh, lx + Math.round(ph * lw), u(46), (int) (0xC0 * st.enable));
        }

        // icon tile
        int tile = u(30);
        int tx0 = x + u(12), ty0 = y + (hdr - tile) / 2;
        round(g, tx0, ty0, tile, tile, u(8), lerp(Theme.CARD_HOVER, accent, 0.12f + 0.25f * st.enable));
        textC(g, tx0 + tile / 2, ty0 + tile / 2, initials(m.name), lerp(Theme.MUTED, accent, st.enable), true);

        text(g, x + u(52), y + u(19), m.name, Theme.TEXT, true);
        text(g, x + u(52), y + u(35), m.description, Theme.MUTED, false);

        // toggle
        int tw = u(36), th = u(18);
        int sx = x + w - u(14) - tw, sy = y + (hdr - th) / 2;
        if (st.enable > 0.02f) glow(g, sx + tw / 2, sy + th / 2, u(26), accent, 3, (int) (10 * st.enable));
        toggle(g, sx, sy, tw, th, st.enable, accent);

        boolean hasSettings = !m.settings().isEmpty();
        if (hasSettings) {
            chevron(g, sx - u(24), y + hdr / 2, u(4), st.open, lerp(Theme.MUTED, accent, st.hover));
        }

        hit(x, y, w, hdr, true, b -> {
            if (b == 1 && hasSettings) st.open = !st.open;
            else if (b == 0) {
                m.toggle();
                toast("MODULE", m.name + (m.isEnabled() ? " enabled" : " disabled"), false);
            }
        }, null);
        if (hasSettings) {
            hit(sx - u(44), y, u(40), hdr, true, b -> st.open = !st.open, null);
        }

        if (st.expand > 0.01f && hasSettings) {
            g.enableScissor(x + S, y + hdr, x + w - S, y + cardH - S);
            fill(g, x + u(12), y + hdr, w - u(24), S, Theme.LINE);
            boolean interactive = st.expand > 0.9f;
            int ry = y + hdr + u(8);
            for (Setting s : m.settings()) {
                int rh = rowHeight(s);
                drawSetting(g, s, x, ry, w, rh, accent, accent2, interactive, ms);
                ry += rh;
            }
            g.disableScissor();
        }
    }

    private void drawSetting(GuiGraphicsExtractor g, Setting s, int x, int y, int w, int rh,
                             int accent, int accent2, boolean interactive, long ms) {
        int pad = u(14);
        int lx = x + pad, rx = x + w - pad;

        if (s instanceof Setting.Bool b) {
            text(g, lx, y + rh / 2, b.name, Theme.SUBTEXT, false);
            int tw = u(30), th = u(16);
            toggle(g, rx - tw, y + (rh - th) / 2, tw, th, b.get() ? 1f : 0f, accent);
            if (interactive) hit(x, y, w, rh, true, btn -> b.toggle(), null);

        } else if (s instanceof Setting.Mode md) {
            text(g, lx, y + rh / 2, md.name, Theme.SUBTEXT, false);
            int bw = u(96), bh = u(20), bx = rx - bw, by = y + (rh - bh) / 2;
            boolean hov = interactive && inside(mx, my, bx, by, bw, bh) && inside(mx, my, vx, vy, vw, vh);
            roundBorder(g, bx, by, bw, bh, bh / 2, S, lerp(Theme.LINE, accent, hov ? 0.8f : 0f), 0xFF1B1B2B);
            textC(g, bx + bw / 2, by + bh / 2, md.get(), accent, false);
            if (interactive) hit(bx, by, bw, bh, true, btn -> md.cycle(btn == 1 ? -1 : 1), null);

        } else if (s instanceof Setting.Num n) {
            text(g, lx, y + u(12), n.name, Theme.SUBTEXT, false);
            textR(g, rx, y + u(12), n.display(), accent, true);

            int trackX = lx, trackW = rx - lx, trackH = u(6), trackY = y + u(22);
            round(g, trackX, trackY, trackW, trackH, trackH / 2, 0xFF26263A);
            int fw = Math.max(trackH, Math.round(trackW * (float) n.fraction()));
            pillGrad(g, trackX, trackY, fw, trackH, accent, accent2);
            shine(g, trackX, fw, trackY, trackH, trackX + Math.round(((ms / 1200f) % 1.4f - 0.2f) * fw), u(24), 0x60);
            int kx = trackX + Math.round(trackW * (float) n.fraction()), ky = trackY + trackH / 2;
            glow(g, kx, ky, u(12), accent, 4, 14);
            circle(g, kx, ky, u(5), 0xFFFFFFFF);

            if (interactive) {
                hit(trackX - u(8), trackY - u(9), trackW + u(16), u(24), true, btn -> {},
                        px -> n.setFraction((px - trackX) / (double) trackW));
            }
        }
    }

    // =====================================================================
    //  footer + toasts
    // =====================================================================
    private void drawFooter(GuiGraphicsExtractor g, int ix, int iw, int footTop, int footH) {
        int cy = footTop + footH / 2 + S;
        text(g, ix + u(16), cy, "ANTHRAX  /  PERSONAL CONFIGURATION", Theme.DIM, false);
        textR(g, ix + iw - u(16), cy, "LEFT CLICK toggle   RIGHT CLICK settings   ESC close", Theme.MUTED, false);
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

    // =====================================================================
    //  input
    // =====================================================================
    private void hit(int x, int y, int w, int h, boolean clipped, IntConsumer press, Drag drag) {
        hits.add(new Hit(x, y, w, h, clipped, press, drag));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int px = Math.round((float) event.x() * gs), py = Math.round((float) event.y() * gs);
        int button = event.button();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(px, py)) continue;
            if (h.clipped() && !inside(px, py, vx, vy, vw, vh)) continue;
            if (h.drag() != null) {
                activeDrag = h.drag();
                activeDrag.to(px);
            }
            if (h.press() != null) h.press().accept(button);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeDrag != null) {
            activeDrag.to(Math.round((float) event.x() * gs));
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
        scrollTarget -= (float) scrollY * u(48);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String initials(String name) {
        String[] p = name.trim().split("\\s+");
        if (p.length >= 2) return ("" + p[0].charAt(0) + p[1].charAt(0)).toUpperCase();
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}