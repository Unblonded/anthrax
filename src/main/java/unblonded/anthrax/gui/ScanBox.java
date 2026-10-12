package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.modules.utility.StorageScanModule;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Storage Scan's search box, shown in container screens while the module is on (bottom-right until you
 * move it). Type to highlight matching slots live; it shows how many slots match. Click it (or press
 * Ctrl+F) to focus, Esc or Enter to unfocus. While focused it takes the keyboard, so typing "e" does not
 * close the inventory. Drag the icon at its left end to move it (or use the HUD editor); the spot is saved
 * with the GUI settings as a fraction of the screen, so it stays put across sessions and window sizes.
 */
public final class ScanBox {
    private static final TextEdit EDIT = new TextEdit(StorageScanModule::query, StorageScanModule::setQuery, 64);
    /** Shares the HUD layout, so the HUD editor can move it too. */
    private static final String HUD_ID = "scanbox";
    private static boolean focused, moving;
    private static int bx, by, bw, bh, textX, grabX, grabY, screenW, screenH;

    private ScanBox() {}

    public static boolean active() { return StorageScanModule.INSTANCE.isEnabled(); }

    private static float scale() { return (float) Minecraft.getInstance().getWindow().getGuiScale(); }

    public static void render(GuiGraphicsExtractor g, AbstractContainerMenu menu) {
        if (!active()) { focused = false; return; }
        Minecraft mc = Minecraft.getInstance();
        float gs = scale();
        int pw = Math.round(g.guiWidth() * gs), ph = Math.round(g.guiHeight() * gs);
        int savedS = Draw.S;
        Font savedFont = Draw.measureFont;
        Draw.S = Math.max(1, Math.min(4, Math.round(ph / 720f)));
        Draw.measureFont = mc.font;
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            int accent = Theme.accent();
            bw = u(190);
            bh = u(28);
            int margin = u(10);
            screenW = pw;
            screenH = ph;
            int[] at = HudLayout.place(HUD_ID, bw, bh, pw, ph, pw - bw - margin, ph - bh - margin);
            bx = at[0];
            by = at[1];
            HudLayout.record(HUD_ID, bx, by, bw, bh);
            int r = bh / 2, cy = by + bh / 2;
            boolean hov = inside(mouseX(), mouseY(), bx, by, bw, bh);
            boolean handleHov = moving || inside(mouseX(), mouseY(), bx, by, handleWidth(), bh);
            shadow(g, bx, by, bw, bh, r, u(6), 0x48);
            glass(g, bx, by, bw, bh, r, alpha(0x0E1119, focused ? 0xE0 : 0xC8), focused ? 1f : hov ? 0.8f : 0.5f);
            if (focused) outline(g, bx, by, bw, bh, r, Math.max(1, S / 2), alpha(accent, 0xE0), alpha(accent, 0x80));
            if (handleHov) {
                icon(g, bx + u(15), cy, Icons.ARROWS_UP_DOWN_LEFT_RIGHT, accent);
                String hint = moving ? "Release to place" : "Drag to move";
                int hw = textWidth(hint, false) + u(14), hh = u(18), hx = bx, hy = by - hh - u(5);
                if (hy < 0) hy = by + bh + u(5);
                glass(g, hx, hy, hw, hh, hh / 2, alpha(0x0E1119, 0xD8), 0.6f);
                text(g, hx + u(7), hy + hh / 2, hint, Theme.SUBTEXT, false);
            } else {
                icon(g, bx + u(15), cy, Icons.MAGNIFYING_GLASS, focused || !StorageScanModule.query().isEmpty() ? accent : Theme.MUTED);
            }

            // live match count on the right
            int matches = 0;
            if (!StorageScanModule.query().isBlank()) for (Slot s : menu.slots) if (StorageScanModule.matches(s.getItem())) matches++;
            String count = StorageScanModule.query().isBlank() ? "" : matches + (matches == 1 ? " match" : " matches");
            int countW = count.isEmpty() ? 0 : textWidth(count, false) + u(10);
            if (!count.isEmpty()) textR(g, bx + bw - u(12), cy, count, matches > 0 ? accent : Theme.DIM, false);

            textX = bx + u(28);
            int width = bw - u(28) - u(12) - countW;
            if (StorageScanModule.query().isEmpty() && !focused) text(g, textX, cy, "Search storage...", Theme.MUTED, false);
            else EDIT.draw(g, textX, cy, width, by, bh, Theme.TEXT, accent, focused);
        } finally {
            g.pose().popMatrix();
            Draw.S = savedS;
            Draw.measureFont = savedFont;
        }
    }

    /** The icon at the left end doubles as the drag handle. */
    private static int handleWidth() { return u(28); }

    private static int mouseX() {
        Minecraft mc = Minecraft.getInstance();
        return (int) Math.round(mc.mouseHandler.getScaledXPos(mc.getWindow()) * scale());
    }

    private static int mouseY() {
        Minecraft mc = Minecraft.getInstance();
        return (int) Math.round(mc.mouseHandler.getScaledYPos(mc.getWindow()) * scale());
    }

    /** Returns true if the click was on the box (and so is consumed). Clicking elsewhere unfocuses. */
    public static boolean mouseClicked(double guiX, double guiY, int button) {
        if (!active()) return false;
        int px = (int) Math.round(guiX * scale()), py = (int) Math.round(guiY * scale());
        if (!inside(px, py, bx, by, bw, bh)) { focused = false; return false; }
        if (button == 0 && inside(px, py, bx, by, handleWidth(), bh)) {
            moving = true;
            grabX = px - bx;
            grabY = py - by;
            return true;
        }
        if (button == 1) { StorageScanModule.setQuery(""); focused = true; return true; }   // right-click clears
        focused = true;
        EDIT.press(EDIT.indexAt(px, textX), Minecraft.getInstance().hasShiftDown());
        return true;
    }

    public static boolean mouseDragged(double guiX, double guiY) {
        if (!active()) return false;
        if (moving) {
            int nx = Math.max(0, Math.min(screenW - bw, (int) Math.round(guiX * scale()) - grabX));
            int ny = Math.max(0, Math.min(screenH - bh, (int) Math.round(guiY * scale()) - grabY));
            // saved as a fraction of the free space, picked up by the config autosave
            HudLayout.move(HUD_ID, nx, ny, bw, bh, screenW, screenH);
            return true;
        }
        if (!focused) return false;
        EDIT.drag(EDIT.indexAt((int) Math.round(guiX * scale()), textX));
        return true;
    }

    public static boolean mouseReleased() {
        if (!moving) return false;
        moving = false;
        return true;
    }

    /** Returns true if the key was used by the box. */
    public static boolean keyPressed(int key, int modifiers) {
        if (!active()) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0, shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (!focused) {
            if (ctrl && key == GLFW.GLFW_KEY_F) { focused = true; EDIT.selectAll(); return true; }
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { focused = false; return true; }
        EDIT.key(key, ctrl, shift);
        return true;   // swallow everything else so inventory keys (E, 1-9, Q) do not fire while typing
    }

    public static boolean charTyped(String s) {
        if (!active() || !focused) return false;
        EDIT.insert(s);
        return true;
    }
}
