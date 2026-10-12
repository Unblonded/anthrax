package unblonded.anthrax.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Drag-and-drop editor for every movable overlay block (watermark, panels, module list, keystrokes,
 * notifications, Storage Scan box...). The live HUD keeps drawing underneath; blocks that are hidden
 * right now (module off, nothing to show, container-only) get a placeholder so they can still be placed.
 * Drag to move (snaps to screen edges and centre lines), right-click a block to reset it.
 */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private float gs = 1;
    private int pw, ph, mx, my;
    private String dragging;
    /** Last clicked block; arrow keys / WASD nudge it. */
    private String selected;
    private int grabX, grabY;
    private boolean snapX, snapY;
    private int snapLineX = -1, snapLineY = -1;
    private int doneX, doneY, doneW, resetX, resetW, barH;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Layout"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // no blur: the point is to see the live HUD
    }

    /** Current box for an element: where it drew, else a placeholder spot. */
    private int[] box(HudLayout.Element e, int index) {
        int[] drawn = HudLayout.drawn(e.id());
        if (drawn != null) return new int[]{drawn[0], drawn[1], drawn[2], drawn[3], 1};
        int[] size = HudLayout.size(e.id());
        // placeholders that were never moved line up in the middle so they are easy to grab
        int col = index % 3, row = index / 3;
        int defX = pw / 2 - u(330) + col * u(230), defY = ph / 2 - u(110) + row * u(60);
        int[] at = HudLayout.place(e.id(), size[0], size[1], pw, ph, defX, defY);
        return new int[]{at[0], at[1], size[0], size[1], 0};
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        gs = (float) minecraft.getWindow().getGuiScale();
        pw = Math.round(width * gs);
        ph = Math.round(height * gs);
        mx = Math.round(mouseX * gs);
        my = Math.round(mouseY * gs);
        int savedS = Draw.S;
        Font savedFont = Draw.measureFont;
        Draw.S = Hud.hudScale(ph);
        Draw.measureFont = font;
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            draw(g);
        } finally {
            g.pose().popMatrix();
            Draw.S = savedS;
            Draw.measureFont = savedFont;
        }
    }

    private void draw(GuiGraphicsExtractor g) {
        int accent = Theme.accent();
        g.fill(0, 0, pw, ph, 0x30000000);
        // grid and centre lines
        int step = u(24);
        for (int x = step; x < pw; x += step) fill(g, x, 0, 1, ph, 0x0CFFFFFF);
        for (int y = step; y < ph; y += step) fill(g, 0, y, pw, 1, 0x0CFFFFFF);
        fill(g, pw / 2, 0, Math.max(1, S / 2), ph, alpha(accent, 0x30));
        fill(g, 0, ph / 2, pw, Math.max(1, S / 2), alpha(accent, 0x30));
        if (dragging != null) {
            if (snapLineX >= 0) fill(g, snapLineX, 0, Math.max(1, S / 2), ph, alpha(accent, 0xC0));
            if (snapLineY >= 0) fill(g, 0, snapLineY, pw, Math.max(1, S / 2), alpha(accent, 0xC0));
        }

        String hovered = hoveredId();
        for (int i = 0; i < HudLayout.ELEMENTS.size(); i++) {
            HudLayout.Element e = HudLayout.ELEMENTS.get(i);
            int[] b = box(e, i);
            boolean live = b[4] == 1, hot = e.id().equals(hovered) || e.id().equals(dragging) || e.id().equals(selected);
            if (!live) {
                round(g, b[0], b[1], b[2], b[3], u(6), alpha(0x0E1119, 0x90));
                textC(g, b[0] + b[2] / 2, b[1] + b[3] / 2, "hidden right now", Theme.DIM, false);
            } else if (hot) round(g, b[0], b[1], b[2], b[3], u(6), alpha(accent, 0x22));
            outline(g, b[0] - S, b[1] - S, b[2] + 2 * S, b[3] + 2 * S, u(6), Math.max(1, S / 2),
                    alpha(accent, hot ? 0xF0 : live ? 0x90 : 0x50), alpha(accent, hot ? 0xF0 : live ? 0x90 : 0x50));
            brackets(g, b[0] - S, b[1] - S, b[2] + 2 * S, b[3] + 2 * S, u(6), Math.max(1, S / 2), alpha(accent, hot ? 0xFF : 0xA0));
            // name tag above (or below at the top edge)
            String label = e.label() + (HudLayout.hasSaved(e.id()) ? "" : "  · default");
            int lw = textWidth(label, true) + u(12), lh = u(15);
            int ly = b[1] - lh - u(3);
            if (ly < barH + u(2)) ly = b[1] + b[3] + u(3);
            int lx = Math.max(0, Math.min(pw - lw, b[0]));
            round(g, lx, ly, lw, lh, lh / 2, hot ? alpha(accent, 0xE0) : alpha(0x0E1119, 0xD8));
            text(g, lx + u(6), ly + lh / 2, label, hot ? 0xFFFFFFFF : Theme.SUBTEXT, true);
        }

        // toolbar
        barH = u(34);
        int barW = Math.min(pw - u(20), u(560)), barX = (pw - barW) / 2, barY = u(8);
        shadow(g, barX, barY, barW, barH, barH / 2, u(10), 0x50);
        glass(g, barX, barY, barW, barH, barH / 2, alpha(0x0E1119, 0xD8), 1f);
        icon(g, barX + u(18), barY + barH / 2, Icons.ARROWS_UP_DOWN_LEFT_RIGHT, accent);
        text(g, barX + u(32), barY + barH / 2, "HUD LAYOUT", Theme.TEXT, true);
        text(g, barX + u(32) + textWidth("HUD LAYOUT", true) + u(10), barY + barH / 2,
                Hud.fit(selected != null ? HudLayout.element(selected).label() + ": arrows / WASD nudge 1px, Shift 10px"
                        : "Drag to move  ·  Click, then arrows / WASD to nudge  ·  Right-click resets", barW - u(300), false), Theme.MUTED, false);
        doneW = u(64);
        doneX = barX + barW - u(6) - doneW;
        doneY = barY + u(6);
        resetW = u(78);
        resetX = doneX - u(6) - resetW;
        pill(g, resetX, doneY, resetW, barH - u(12), "Reset all", Theme.DANGER);
        pill(g, doneX, doneY, doneW, barH - u(12), "Done", accent);
    }

    private void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int color) {
        boolean hov = inside(mx, my, x, y, w, h);
        glass(g, x, y, w, h, h / 2, hov ? alpha(color, 0x80) : alpha(0xFFFFFF, 0x18), hov ? 1f : 0.5f);
        textC(g, x + w / 2, y + h / 2, label, hov ? 0xFFFFFFFF : Theme.SUBTEXT, true);
    }

    /** Topmost block under the mouse (later elements draw on top). */
    private String hoveredId() {
        for (int i = HudLayout.ELEMENTS.size() - 1; i >= 0; i--) {
            HudLayout.Element e = HudLayout.ELEMENTS.get(i);
            int[] b = box(e, i);
            if (inside(mx, my, b[0] - S, b[1] - S, b[2] + 2 * S, b[3] + 2 * S)) return e.id();
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        mx = (int) Math.round(event.x() * gs);
        my = (int) Math.round(event.y() * gs);
        int bh = barH - u(12);
        if (event.button() == 0 && inside(mx, my, doneX, doneY, doneW, bh)) { onClose(); return true; }
        if (event.button() == 0 && inside(mx, my, resetX, doneY, resetW, bh)) {
            for (HudLayout.Element e : HudLayout.ELEMENTS) HudLayout.reset(e.id());
            Notifications.info("HUD Layout", "Every block is back at its default spot");
            return true;
        }
        String id = hoveredId();
        selected = id;
        if (id == null) return super.mouseClicked(event, doubleClick);
        if (event.button() == 1) {
            HudLayout.reset(id);
            Notifications.info("HUD Layout", HudLayout.element(id).label() + " reset");
            return true;
        }
        if (event.button() == 0) {
            int[] b = box(HudLayout.element(id), HudLayout.ELEMENTS.indexOf(HudLayout.element(id)));
            dragging = id;
            grabX = mx - b[0];
            grabY = my - b[1];
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null) return super.mouseDragged(event, dragX, dragY);
        mx = (int) Math.round(event.x() * gs);
        my = (int) Math.round(event.y() * gs);
        HudLayout.Element e = HudLayout.element(dragging);
        int[] b = box(e, HudLayout.ELEMENTS.indexOf(e));
        int w = b[2], h = b[3];
        int nx = Math.max(0, Math.min(pw - w, mx - grabX));
        int ny = Math.max(0, Math.min(ph - h, my - grabY));
        // snap to edges and centre lines unless Shift is held
        boolean free = minecraft.hasShiftDown();
        int snap = u(8);
        snapLineX = snapLineY = -1;
        if (!free) {
            int[][] xs = {{0, 0}, {pw - w, pw - 1}, {pw / 2 - w / 2, pw / 2}};
            for (int[] c : xs) if (Math.abs(nx - c[0]) <= snap) { nx = c[0]; snapLineX = c[1]; break; }
            int[][] ys = {{0, 0}, {ph - h, ph - 1}, {ph / 2 - h / 2, ph / 2}};
            for (int[] c : ys) if (Math.abs(ny - c[0]) <= snap) { ny = c[0]; snapLineY = c[1]; break; }
        }
        HudLayout.move(dragging, nx, ny, w, h, pw, ph);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        snapLineX = snapLineY = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (selected != null) { selected = null; return true; }   // first Esc drops the selection
            onClose();
            return true;
        }
        if (selected != null) {
            int step = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1, dx = 0, dy = 0;
            switch (event.key()) {
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> dx = -step;
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> dx = step;
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> dy = -step;
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> dy = step;
                default -> {}
            }
            if (dx != 0 || dy != 0) {
                HudLayout.Element e = HudLayout.element(selected);
                int[] b = box(e, HudLayout.ELEMENTS.indexOf(e));
                // read the saved spot directly, so several key repeats inside one frame all count
                if (HudLayout.hasSaved(selected)) {
                    int[] at = HudLayout.place(selected, b[2], b[3], pw, ph, b[0], b[1]);
                    b[0] = at[0];
                    b[1] = at[1];
                }
                int nx = Math.max(0, Math.min(pw - b[2], b[0] + dx));
                int ny = Math.max(0, Math.min(ph - b[3], b[1] + dy));
                HudLayout.move(selected, nx, ny, b[2], b[3], pw, ph);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        unblonded.anthrax.modules.ModuleConfig.saveNow();
        minecraft.gui.setScreen(parent);
    }
}
