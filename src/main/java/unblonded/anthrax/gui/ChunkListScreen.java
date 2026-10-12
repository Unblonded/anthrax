package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.modules.visual.FontSizeModule;
import unblonded.anthrax.modules.world.NewChunksModule;
import unblonded.anthrax.modules.world.NewChunksModule.Found;
import unblonded.anthrax.modules.world.NewChunksModule.Kind;
import unblonded.anthrax.modules.world.WaypointsModule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Chunk list for New Chunks: every classified chunk on this server and dimension, filtered by kind and
 * sorted by distance or time, beside a mini map of the chunks around you. Select one for details and
 * to set a waypoint, copy its coordinates or remove it. Tab, sort and zoom are remembered for the session.
 */
public final class ChunkListScreen extends Screen {
    private record Hit(int x, int y, int w, int h, IntConsumer press) {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm"), DAY = DateTimeFormatter.ofPattern("MMM d HH:mm");
    private static int tab;                 // 0 all, 1 new, 2 old, 3 old generation
    private static boolean newestFirst;
    private static int cell = 8;            // mini map chunk size in design units
    private final List<Hit> hits = new ArrayList<>();
    private final float[] tabAnim = {-1};
    private Found selected;
    private float scroll, scrollTarget;
    private int listX, listY, listW, listH, mapX, mapY, mapW, mapH;
    private float gs = 1;
    private int mx, my;
    private long lastNs = System.nanoTime(), clearArmedAt;
    private final long openNs = System.nanoTime();

    public ChunkListScreen() { super(Component.literal("New Chunks")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (Draw.FX > 0) extractBlurredBackground(g);
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
        Draw.S = FontSizeModule.apply(Math.min(Math.round(ph / 720f), Math.min(pw / 660, ph / 420)));
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

    private List<Found> filtered() {
        Minecraft mc = Minecraft.getInstance();
        List<Found> out = new ArrayList<>();
        for (Found f : NewChunksModule.INSTANCE.found()) {
            if (tab == 1 && f.kind() != Kind.NEW || tab == 2 && f.kind() != Kind.OLD || tab == 3 && f.kind() != Kind.OLD_GEN) continue;
            out.add(f);
        }
        if (newestFirst) out.sort(Comparator.comparingLong(Found::found).reversed());
        else if (mc.player != null) {
            double px = mc.player.getX(), pz = mc.player.getZ();
            out.sort(Comparator.comparingDouble(f -> sq(f.blockX() - px) + sq(f.blockZ() - pz)));
        }
        return out;
    }

    private void draw(GuiGraphicsExtractor g, int pw, int ph, float dt) {
        NewChunksModule module = NewChunksModule.INSTANCE;
        int accent = Theme.accent(), accent2 = Theme.accent2();
        float open = Hud.easeOut((System.nanoTime() - openNs) / 1e9f / .4f);
        g.fill(0, 0, pw, ph, alpha(0x05060B, (int) ((FX > 0 ? 0x5C : 0xC0) * open)));
        g.fillGradient(0, 0, pw, ph, alpha(accent, (int) (0x10 * open)), alpha(accent2, (int) (0x18 * open)));

        int ww = Math.min(pw - u(20), u(720)), wh = Math.min(ph - u(20), u(440));
        int wx = (pw - ww) / 2, wy = (ph - wh) / 2 + Math.round((1 - open) * u(20));
        int r = u(24);
        shadow(g, wx, wy, ww, wh, r, u(22), (int) (0x70 * open));
        glass(g, wx, wy, ww, wh, r, alpha(0x0E1119, 0xB8), 1f);

        // ---- header
        List<Found> all = module.found();
        int nNew = 0, nOld = 0, nGen = 0;
        for (Found f : all) switch (f.kind()) { case NEW -> nNew++; case OLD -> nOld++; default -> nGen++; }
        int headH = u(52), cy = wy + headH / 2, bead = u(28), bx = wx + u(16);
        bead(g, bx, cy - bead / 2, bead, accent);
        icon(g, bx + bead / 2, cy, Icons.LAYER_GROUP, 0xFFFFFFFF);
        text(g, bx + bead + u(10), cy - u(6), "New Chunks", Theme.TEXT, true);
        String sub = module.isEnabled() ? module.placeName() + "   " + all.size() + " chunks"
                : "New Chunks is off: turn it on in the World category to start finding chunks";
        text(g, bx + bead + u(10), cy + u(8), Hud.fit(sub, u(260), false), module.isEnabled() ? Theme.MUTED : 0xFFFFC94A, false);

        int close = u(26), closeX = wx + ww - u(16) - close;
        boolean closeHov = inside(mx, my, closeX, cy - close / 2, close, close);
        glass(g, closeX, cy - close / 2, close, close, close / 2, closeHov ? alpha(Theme.DANGER, 0x90) : 0x14FFFFFF, closeHov ? 1 : .4f);
        icon(g, closeX + close / 2, cy, Icons.XMARK, closeHov ? 0xFFFFFFFF : Theme.SUBTEXT);
        hits.add(new Hit(closeX, cy - close / 2, close, close, b -> onClose()));
        int segW = u(330), segH = u(26), segX = closeX - u(10) - segW;
        segmented(g, segX, cy - segH / 2, segW, segH, new String[]{"All  " + all.size(), "New  " + nNew, "Old  " + nOld, "Old gen  " + nGen}, accent);
        fill(g, wx + r, wy + headH, ww - 2 * r, Math.max(1, S / 2), 0x16FFFFFF);

        // ---- list
        List<Found> list = filtered();
        if (selected != null && !list.contains(selected)) selected = null;
        listX = wx + u(12); listY = wy + headH + u(10); listW = u(300); listH = wh - headH - u(22);
        glass(g, listX, listY, listW, listH, u(18), 0x0CFFFFFF, .3f);
        int sortH = u(22), sortY = listY + u(8);
        text(g, listX + u(14), sortY + sortH / 2, list.size() + (list.size() == 1 ? " chunk" : " chunks"), Theme.DIM, true);
        pill(g, listX + listW - u(10) - u(92), sortY, u(92), sortH, newestFirst ? "Newest" : "Nearest",
                newestFirst ? Icons.CLOCK_ROTATE_LEFT : Icons.LOCATION_ARROW, accent, () -> { newestFirst = !newestFirst; scrollTarget = 0; });
        int rowsTop = sortY + sortH + u(6), rowsH = listY + listH - rowsTop - u(6);
        drawList(g, list, rowsTop, rowsH, dt, accent);

        // ---- map + details
        mapX = listX + listW + u(12); mapY = listY; mapW = wx + ww - u(12) - mapX; mapH = listH - u(96);
        drawMap(g, list, accent);
        drawDetails(g, mapX, mapY + mapH + u(8), mapW, u(88), accent);
    }

    private void drawList(GuiGraphicsExtractor g, List<Found> list, int top, int h, float dt, int accent) {
        Minecraft mc = Minecraft.getInstance();
        int rowH = u(40), pad = u(6);
        float content = list.size() * rowH;
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, content - h)));
        scroll += (scrollTarget - scroll) * Math.min(1, dt * 14);
        if (list.isEmpty()) {
            icon(g, listX + listW / 2, top + h / 2 - u(18), Icons.LAYER_GROUP, Theme.DIM);
            textC(g, listX + listW / 2, top + h / 2 + u(4), "Nothing here yet", Theme.MUTED, true);
            textC(g, listX + listW / 2, top + h / 2 + u(20), "Fly around with New Chunks on", Theme.DIM, false);
            return;
        }
        g.enableScissor(listX, top, listX + listW, top + h);
        int y = top - Math.round(scroll);
        for (Found f : list) {
            if (y + rowH >= top && y <= top + h) {
                int rx = listX + pad, rw = listW - pad * 2, rh = rowH - u(4);
                boolean sel = f.equals(selected), hov = inside(mx, my, rx, y, rw, rh) && inside(mx, my, listX, top, listW, h);
                if (sel) glassTint(g, rx, y, rw, rh, u(12), accent, .5f);
                else if (hov) round(g, rx, y, rw, rh, u(12), 0x14FFFFFF);
                int sq = u(12), cyy = y + rh / 2;
                round(g, rx + u(10), cyy - sq / 2, sq, sq, u(3), alpha(f.kind().color, 0xC0));
                text(g, rx + u(30), y + u(12), "Chunk " + f.cx() + ", " + f.cz(), Theme.TEXT, true);
                text(g, rx + u(30), y + u(26), f.blockX() + "  " + f.blockZ() + "   ·   " + f.kind().label + "   ·   " + when(f.found()), Theme.MUTED, false);
                if (mc.player != null) {
                    double d = Math.sqrt(sq(f.blockX() - mc.player.getX()) + sq(f.blockZ() - mc.player.getZ()));
                    textR(g, rx + rw - u(10), y + u(12), Math.round(d) + "m", Theme.SUBTEXT, false);
                }
                hits.add(new Hit(rx, Math.max(y, top), rw, Math.min(rh, top + h - Math.max(y, top)), b -> selected = f));
            }
            y += rowH;
        }
        g.disableScissor();
        if (content > h) {
            int barH = Math.max(u(24), Math.round(h * (h / content)));
            int barY = top + Math.round((h - barH) * (scroll / Math.max(1, content - h)));
            round(g, listX + listW - u(5), barY, u(3), barH, u(2), 0x50FFFFFF);
        }
    }

    /** Chunks around the player as a grid; scroll zooms, click a cell to select that chunk. */
    private void drawMap(GuiGraphicsExtractor g, List<Found> list, int accent) {
        Minecraft mc = Minecraft.getInstance();
        round(g, mapX, mapY, mapW, mapH, u(16), 0xC006080E);
        outline(g, mapX, mapY, mapW, mapH, u(16), Math.max(1, S / 2), 0x22FFFFFF, 0x10FFFFFF);
        if (mc.player == null) return;
        int c = u(cell), pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        int ccx = mapX + mapW / 2, ccy = mapY + mapH / 2;
        int halfX = mapW / 2 / c + 1, halfZ = mapH / 2 / c + 1;
        Map<Long, Found> byPos = new HashMap<>();
        for (Found f : list) byPos.put(((long) f.cx() << 32) ^ (f.cz() & 0xFFFFFFFFL), f);
        g.enableScissor(mapX + S, mapY + S, mapX + mapW - S, mapY + mapH - S);
        for (int dx = -halfX; dx <= halfX; dx++)
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                int x = ccx + dx * c - c / 2, y = ccy + dz * c - c / 2;
                Found f = byPos.get(((long) (pcx + dx) << 32) ^ ((pcz + dz) & 0xFFFFFFFFL));
                if (f == null) {
                    if (c >= u(6)) fill(g, x, y, c - 1, c - 1, 0x0AFFFFFF);
                    continue;
                }
                boolean sel = f.equals(selected), hov = inside(mx, my, x, y, c, c);
                fill(g, x, y, c - 1, c - 1, alpha(f.kind().color, sel ? 0xF0 : hov ? 0xD0 : 0x90));
                hits.add(new Hit(x, y, c, c, b -> selected = f));
            }
        // you are here
        circle(g, ccx, ccy, Math.max(u(3), c / 3), 0xFFFFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(ccx, ccy);
        g.pose().rotate((float) Math.toRadians(mc.player.getYRot() + 180 - 45));
        icon(g, 0, 0, Icons.LOCATION_ARROW, accent);
        g.pose().popMatrix();
        if (selected != null) {
            int x = ccx + (selected.cx() - pcx) * c - c / 2, y = ccy + (selected.cz() - pcz) * c - c / 2;
            outline(g, x - S, y - S, c + 2 * S, c + 2 * S, u(2), Math.max(1, S), 0xFFFFFFFF, 0xFFFFFFFF);
        }
        g.disableScissor();
        // legend
        int lx = mapX + u(12), ly = mapY + mapH - u(16);
        for (Kind k : Kind.values()) {
            round(g, lx, ly - u(4), u(8), u(8), u(2), k.color);
            text(g, lx + u(12), ly, k.label, Theme.SUBTEXT, false);
            lx += textWidth(k.label, false) + u(28);
        }
        textR(g, mapX + mapW - u(12), ly, "Scroll to zoom", Theme.DIM, false);
    }

    private void drawDetails(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        glass(g, x, y, w, h, u(16), 0x0CFFFFFF, .3f);
        NewChunksModule module = NewChunksModule.INSTANCE;
        int cy = y + u(20);
        long now = System.currentTimeMillis();
        boolean armed = now - clearArmedAt < 3000;
        int cw = u(130), cx = x + w - u(12) - cw;
        pill(g, cx, y + h - u(32), cw, u(22), armed ? "Click to confirm" : "Clear dimension", Icons.TRASH, armed ? Theme.DANGER : 0,
                () -> {
                    if (armed) { module.clearHere(); selected = null; clearArmedAt = 0; Notifications.info("New Chunks", "Cleared this dimension"); }
                    else clearArmedAt = now;
                });
        if (selected == null) {
            text(g, x + u(16), cy, "Select a chunk", Theme.SUBTEXT, true);
            text(g, x + u(16), cy + u(16), "Click a row or a square on the map", Theme.DIM, false);
            return;
        }
        Found f = selected;
        round(g, x + u(16), cy - u(5), u(10), u(10), u(3), f.kind().color);
        text(g, x + u(32), cy, f.kind().label + " chunk  " + f.cx() + ", " + f.cz(), Theme.TEXT, true);
        text(g, x + u(16), cy + u(16), "Blocks " + (f.cx() << 4) + ".." + ((f.cx() << 4) + 15) + ",  " + (f.cz() << 4) + ".." + ((f.cz() << 4) + 15)
                + "   ·   found " + when(f.found()), Theme.MUTED, false);
        int by = y + h - u(32), bw = u(96);
        pill(g, x + u(12), by, bw, u(22), "Waypoint", Icons.LOCATION_DOT, accent, () ->
                WaypointsModule.INSTANCE.add("Chunk_" + f.cx() + "_" + f.cz(), f.blockX(), Minecraft.getInstance().player == null ? 64
                        : Minecraft.getInstance().player.getBlockY(), f.blockZ()));
        pill(g, x + u(12) + bw + u(6), by, u(76), u(22), "Copy", Icons.COPY, accent, () -> {
            minecraft.keyboardHandler.setClipboard(f.blockX() + " ~ " + f.blockZ());
            Notifications.success("New Chunks", "Copied " + f.blockX() + " ~ " + f.blockZ());
        });
        pill(g, x + u(12) + bw + u(88), by, u(84), u(22), "Remove", Icons.XMARK, Theme.DANGER, () -> { module.remove(f); selected = null; });
    }

    // ------------------------------------------------------------------ widgets
    private void segmented(GuiGraphicsExtractor g, int x, int y, int w, int h, String[] labels, int accent) {
        round(g, x, y, w, h, h / 2, 0x30000000);
        outline(g, x, y, w, h, h / 2, Math.max(1, S / 2), 0x14FFFFFF, 0x22FFFFFF);
        int part = w / labels.length;
        if (tabAnim[0] < 0) tabAnim[0] = tab;
        tabAnim[0] += (tab - tabAnim[0]) * .25f;
        glassTint(g, x + Math.round(tabAnim[0] * part) + S, y + S, part - 2 * S, h - 2 * S, (h - 2 * S) / 2, accent, .65f);
        for (int i = 0; i < labels.length; i++) {
            boolean on = i == tab;
            textC(g, x + i * part + part / 2, y + h / 2, labels[i], on ? 0xFFFFFFFF : Theme.MUTED, on);
            final int idx = i;
            hits.add(new Hit(x + i * part, y, part, h, b -> { tab = idx; scrollTarget = 0; }));
        }
    }

    /** Glass pill button; {@code color} 0 means a neutral button. */
    private void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String glyph, int color, Runnable action) {
        boolean hov = inside(mx, my, x, y, w, h);
        if (hov && color != 0) glassTint(g, x, y, w, h, h / 2, color, .8f);
        else glass(g, x, y, w, h, h / 2, hov ? 0x28FFFFFF : 0x14FFFFFF, hov ? 1 : .4f);
        icon(g, x + u(13), y + h / 2, glyph, hov ? 0xFFFFFFFF : Theme.SUBTEXT);
        text(g, x + u(25), y + h / 2, label, hov ? 0xFFFFFFFF : Theme.SUBTEXT, false);
        hits.add(new Hit(x, y, w, h, b -> action.run()));
    }

    private static String when(long epochMs) {
        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
        return t.toLocalDate().equals(LocalDate.now()) ? t.format(TIME) : t.format(DAY);
    }

    private static double sq(double v) { return v * v; }

    // ------------------------------------------------------------------ input
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int px = Math.round((float) mouseX * gs), py = Math.round((float) mouseY * gs);
        if (inside(px, py, mapX, mapY, mapW, mapH)) cell = Math.max(3, Math.min(20, cell + (scrollY > 0 ? 1 : -1)));
        else scrollTarget -= (float) scrollY * u(40);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_TAB) { tab = (tab + 1) % 4; scrollTarget = 0; return true; }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            List<Found> list = filtered();
            if (list.isEmpty()) return true;
            int i = selected == null ? -1 : list.indexOf(selected);
            i = Math.max(0, Math.min(list.size() - 1, i + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            selected = list.get(i);
            return true;
        }
        return super.keyPressed(event);
    }
}
