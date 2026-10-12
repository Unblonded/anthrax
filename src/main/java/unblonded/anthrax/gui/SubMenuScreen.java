package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Keybinds;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.client.ClientSettingsModule;
import unblonded.anthrax.modules.visual.FontSizeModule;
import unblonded.anthrax.modules.world.BaseFinderModule;
import unblonded.anthrax.modules.world.NewChunksModule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Sub module menu: a small launcher for the screens modules own (Base Intel, the chunk list). Lists only
 * the ones whose module is on, or all of them, per Client Settings. Click a tile, press its number, or use
 * the arrows and Enter; Esc or the menu key closes it.
 */
public final class SubMenuScreen extends Screen {
    /** One launchable screen: what it is, the module it belongs to, its own bind, and how to open it. */
    private record Entry(String name, String about, String glyph, FeatureModule module, IntSupplier key, Supplier<Screen> open) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("Base Intel", "Bases, natural structures and the intel map", Icons.MAP_LOCATION_DOT,
                    BaseFinderModule.INSTANCE, BaseFinderModule.INSTANCE::intelKey, IntelScreen::new),
            new Entry("Chunk list", "New, old and old-generation chunks with a mini map", Icons.LAYER_GROUP,
                    NewChunksModule.INSTANCE, NewChunksModule.INSTANCE::listKey, ChunkListScreen::new));

    private record Hit(int x, int y, int w, int h, IntConsumer press) {}
    private final List<Hit> hits = new ArrayList<>();
    private final float[] hover = new float[ENTRIES.size()];
    private int selected;
    private float gs = 1;
    private int mx, my;
    private final long openNs = System.nanoTime();
    private long lastNs = System.nanoTime();

    public SubMenuScreen() { super(Component.literal("Sub menus")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (Draw.FX > 0) extractBlurredBackground(g);
    }

    private List<Entry> shown() {
        List<Entry> out = new ArrayList<>();
        boolean onlyOn = ClientSettingsModule.INSTANCE.onlyEnabled();
        for (Entry e : ENTRIES) if (!onlyOn || e.module.isEnabled()) out.add(e);
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Draw.measureFont = font;
        gs = (float) minecraft.getWindow().getGuiScale();
        int pw = Math.round(width * gs), ph = Math.round(height * gs);
        mx = Math.round(mouseX * gs);
        my = Math.round(mouseY * gs);
        long now = System.nanoTime();
        float dt = Math.min(.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        Theme.tick(System.currentTimeMillis());
        Draw.S = FontSizeModule.apply(Math.min(Math.round(ph / 720f), Math.min(pw / 600, ph / 400)));
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            hits.clear();
            draw(g, pw, ph, dt);
            Hud.toasts(g, pw, ph, Theme.accent(), u(18));
        } finally {
            g.pose().popMatrix();
        }
    }

    private void draw(GuiGraphicsExtractor g, int pw, int ph, float dt) {
        int accent = Theme.accent(), accent2 = Theme.accent2();
        float open = Hud.easeOut((System.nanoTime() - openNs) / 1e9f / .3f);
        g.fill(0, 0, pw, ph, alpha(0x05060B, (int) ((FX > 0 ? 0x50 : 0xB0) * open)));
        List<Entry> list = shown();
        selected = Math.max(0, Math.min(selected, Math.max(0, list.size() - 1)));

        int tileH = u(58), gap = u(8), headH = u(48);
        int ww = Math.min(pw - u(24), u(380));
        int body = list.isEmpty() ? u(90) : list.size() * (tileH + gap) - gap;
        int wh = headH + body + u(44);
        int wx = (pw - ww) / 2, wy = (ph - wh) / 2 + Math.round((1 - open) * u(16));
        float scale = .96f + .04f * open;
        g.pose().pushMatrix();
        g.pose().translate(wx + ww / 2f, wy + wh / 2f);
        g.pose().scale(scale, scale);
        g.pose().translate(-(wx + ww / 2f), -(wy + wh / 2f));
        try {
            shadow(g, wx, wy, ww, wh, u(22), u(20), (int) (0x70 * open));
            glass(g, wx, wy, ww, wh, u(22), alpha(0x0E1119, 0xC8), 1f);
            int cy = wy + headH / 2;
            icon(g, wx + u(22), cy, Icons.TABLE_COLUMNS, accent);
            text(g, wx + u(36), cy, "Sub menus", Theme.TEXT, true);
            boolean onlyOn = ClientSettingsModule.INSTANCE.onlyEnabled();
            String filter = onlyOn ? "Enabled only" : "All";
            int fw = textWidth(filter, false) + u(30), fh = u(22), fx = wx + ww - u(14) - fw, fy = cy - fh / 2;
            boolean fhov = inside(mx, my, fx, fy, fw, fh);
            glass(g, fx, fy, fw, fh, fh / 2, fhov ? 0x28FFFFFF : 0x14FFFFFF, fhov ? 1 : .4f);
            icon(g, fx + u(11), cy, onlyOn ? Icons.EYE : Icons.LAYER_GROUP, fhov ? 0xFFFFFFFF : Theme.SUBTEXT);
            text(g, fx + u(21), cy, filter, fhov ? 0xFFFFFFFF : Theme.SUBTEXT, false);
            hits.add(new Hit(fx, fy, fw, fh, b -> ClientSettingsModule.INSTANCE.setOnlyEnabled(!onlyOn)));
            fill(g, wx + u(18), wy + headH, ww - u(36), Math.max(1, S / 2), 0x16FFFFFF);

            int y = wy + headH + u(10);
            if (list.isEmpty()) {
                textC(g, wx + ww / 2, y + u(26), "No sub menus are enabled", Theme.SUBTEXT, true);
                textC(g, wx + ww / 2, y + u(42), "Turn on Base Finder or New Chunks, or show all", Theme.DIM, false);
                int bw = u(96), bh = u(22), bx = wx + (ww - bw) / 2, by = y + u(56);
                boolean hov = inside(mx, my, bx, by, bw, bh);
                glassTint(g, bx, by, bw, bh, bh / 2, accent, hov ? .9f : .6f);
                textC(g, bx + bw / 2, by + bh / 2, "Show all", 0xFFFFFFFF, true);
                hits.add(new Hit(bx, by, bw, bh, b -> ClientSettingsModule.INSTANCE.setOnlyEnabled(false)));
            }
            for (int i = 0; i < list.size(); i++) {
                Entry e = list.get(i);
                int tx = wx + u(12), tw = ww - u(24);
                boolean hov = inside(mx, my, tx, y, tw, tileH);
                if (hov) selected = i;
                int idx = ENTRIES.indexOf(e);
                hover[idx] += ((i == selected ? 1 : 0) - hover[idx]) * Math.min(1, dt * 14);
                float hv = hover[idx];
                int body2 = lerp(0x10FFFFFF, alpha(accent, 0x30), hv);
                glass(g, tx, y, tw, tileH, u(16), body2, .35f + .5f * hv);
                if (FANCY && hv > .05f) shineC(g, tx + u(10), tw - u(20), y + S, tileH - 2 * S, mx, u(110), (int) (0x14 * hv), 0xFFFFFF);
                int bead = u(34), bx = tx + u(12), bcy = y + tileH / 2;
                bead(g, bx, bcy - bead / 2, bead, e.module.isEnabled() ? accent : 0xFF4A4E60);
                icon(g, bx + bead / 2, bcy, e.glyph, 0xFFFFFFFF);
                int nx = bx + bead + u(12);
                text(g, nx, y + u(20), e.name, Theme.TEXT, true);
                text(g, nx, y + u(36), Hud.fit(e.about, tw - (nx - tx) - u(90), false), Theme.MUTED, false);
                // number hotkey, the screen's own bind, and whether its module is on
                int right = tx + tw - u(14);
                String num = Integer.toString(i + 1);
                int kw = u(18);
                round(g, right - kw, y + u(12), kw, kw, u(5), 0x1CFFFFFF);
                textC(g, right - kw / 2, y + u(12) + kw / 2, num, Theme.SUBTEXT, true);
                int bind = e.key.getAsInt();
                String status = (e.module.isEnabled() ? "On" : "Off") + (bind != Setting.Key.NONE ? "   " + Keybinds.keyName(bind) : "");
                textR(g, right, y + u(44), status, e.module.isEnabled() ? Theme.OK : Theme.DIM, false);
                final Entry target = e;
                hits.add(new Hit(tx, y, tw, tileH, b -> launch(target)));
                y += tileH + gap;
            }
            textC(g, wx + ww / 2, wy + wh - u(16), "Click, press 1-" + Math.max(1, list.size()) + ", or arrows + Enter   ·   Esc closes", Theme.DIM, false);
        } finally {
            g.pose().popMatrix();
        }
    }

    private void launch(Entry e) { Minecraft.getInstance().gui.setScreen(e.open.get()); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int px = Math.round((float) event.x() * gs), py = Math.round((float) event.y() * gs);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (inside(px, py, h.x(), h.y(), h.w(), h.h())) { h.press().accept(event.button()); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        List<Entry> list = shown();
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && key - GLFW.GLFW_KEY_1 < list.size()) { launch(list.get(key - GLFW.GLFW_KEY_1)); return true; }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) { selected = Math.min(list.size() - 1, selected + 1); return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) { selected = Math.max(0, selected - 1); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) && !list.isEmpty()) { launch(list.get(selected)); return true; }
        Setting.Key menu = ClientSettingsModule.INSTANCE.subMenuKey();
        if (menu.bound() && key == menu.get()) { onClose(); return true; }
        return super.keyPressed(event);
    }
}
