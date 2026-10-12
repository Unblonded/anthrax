package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Editing state for one text box: caret, selection and horizontal scroll, with the usual desktop
 * behaviour. Click to place the caret, drag or Shift+click to select, double-click selects a word and
 * triple-click everything; arrows (Ctrl jumps words, Shift selects), Home/End, Backspace/Delete (Ctrl for
 * a word), Ctrl+A/C/X/V. The text itself lives elsewhere and is read and written through the binding.
 */
final class TextEdit {
    private static final long MULTI_CLICK_MS = 350;

    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final int maxLength;
    private int caret, anchor, scroll;
    private long lastClickAt;
    private int clicks, lastClickIndex = -1;
    private long blinkFrom = System.currentTimeMillis();

    TextEdit(Supplier<String> getter, Consumer<String> setter, int maxLength) {
        this.getter = getter;
        this.setter = setter;
        this.maxLength = maxLength;
        caret = anchor = text().length();
    }

    String text() { String t = getter.get(); return t == null ? "" : t; }

    private void clamp() {
        int n = text().length();
        caret = Math.max(0, Math.min(caret, n));
        anchor = Math.max(0, Math.min(anchor, n));
    }

    boolean hasSelection() { return caret != anchor; }
    private int selStart() { return Math.min(caret, anchor); }
    private int selEnd() { return Math.max(caret, anchor); }
    private void moved() { blinkFrom = System.currentTimeMillis(); }

    void selectAll() { clamp(); anchor = 0; caret = text().length(); moved(); }

    /** Replaces the selection (or inserts at the caret) with {@code s}, single line, within the length limit. */
    void insert(String s) {
        clamp();
        String clean = s.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        String t = text();
        int a = selStart(), b = selEnd();
        int room = maxLength - (t.length() - (b - a));
        if (room <= 0 && clean.length() > 0 && a == b) return;
        if (clean.length() > room) clean = clean.substring(0, Math.max(0, room));
        setter.accept(t.substring(0, a) + clean + t.substring(b));
        caret = anchor = a + clean.length();
        moved();
    }

    private void deleteRange(int a, int b) {
        String t = text();
        if (a >= b) return;
        setter.accept(t.substring(0, a) + t.substring(b));
        caret = anchor = a;
        moved();
    }

    private int wordLeft(int i) {
        String t = text();
        while (i > 0 && Character.isWhitespace(t.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(t.charAt(i - 1))) i--;
        return i;
    }

    private int wordRight(int i) {
        String t = text();
        while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++;
        while (i < t.length() && !Character.isWhitespace(t.charAt(i))) i++;
        return i;
    }

    private void moveTo(int index, boolean select) {
        caret = Math.max(0, Math.min(index, text().length()));
        if (!select) anchor = caret;
        moved();
    }

    /** Handles an editing key. Returns true if it was used. */
    boolean key(int key, boolean ctrl, boolean shift) {
        clamp();
        var keyboard = Minecraft.getInstance().keyboardHandler;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                if (hasSelection() && !shift) moveTo(selStart(), false);
                else moveTo(ctrl ? wordLeft(caret) : caret - 1, shift);
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (hasSelection() && !shift) moveTo(selEnd(), false);
                else moveTo(ctrl ? wordRight(caret) : caret + 1, shift);
            }
            case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_UP -> moveTo(0, shift);
            case GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_DOWN -> moveTo(text().length(), shift);
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) deleteRange(selStart(), selEnd());
                else deleteRange(ctrl ? wordLeft(caret) : Math.max(0, caret - 1), caret);
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) deleteRange(selStart(), selEnd());
                else deleteRange(caret, ctrl ? wordRight(caret) : Math.min(text().length(), caret + 1));
            }
            case GLFW.GLFW_KEY_A -> { if (!ctrl) return false; selectAll(); }
            case GLFW.GLFW_KEY_C -> {
                if (!ctrl) return false;
                if (hasSelection()) keyboard.setClipboard(text().substring(selStart(), selEnd()));
            }
            case GLFW.GLFW_KEY_X -> {
                if (!ctrl) return false;
                if (hasSelection()) { keyboard.setClipboard(text().substring(selStart(), selEnd())); deleteRange(selStart(), selEnd()); }
            }
            case GLFW.GLFW_KEY_V -> {
                if (!ctrl) return false;
                String clip = keyboard.getClipboard();
                if (clip != null) insert(clip);
            }
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------ mouse
    /** Caret index nearest to an x position, for text drawn starting at {@code textX}. */
    int indexAt(int px, int textX) {
        String t = text();
        int target = px - (textX - scroll), best = 0, bestD = Integer.MAX_VALUE;
        for (int i = 0; i <= t.length(); i++) {
            int d = Math.abs(textWidth(t.substring(0, i), false) - target);
            if (d < bestD) { bestD = d; best = i; }
            else if (d > bestD) break;   // widths only grow, so past the minimum we are done
        }
        return best;
    }

    /** Mouse down at a caret index: place the caret, extend with Shift, or select word / all on multi-click. */
    void press(int index, boolean shift) {
        long now = System.currentTimeMillis();
        clicks = now - lastClickAt < MULTI_CLICK_MS && Math.abs(index - lastClickIndex) <= 1 ? clicks + 1 : 1;
        lastClickAt = now;
        lastClickIndex = index;
        if (clicks == 2) {
            String t = text();
            int a = index, b = index;
            while (a > 0 && !Character.isWhitespace(t.charAt(a - 1))) a--;
            while (b < t.length() && !Character.isWhitespace(t.charAt(b))) b++;
            anchor = a;
            caret = b;
            moved();
        } else if (clicks >= 3) {
            selectAll();
        } else {
            moveTo(index, shift);
        }
    }

    /** Mouse dragged to a caret index (after a single click): extend the selection. */
    void drag(int index) {
        if (clicks == 1) moveTo(index, true);
    }

    // ------------------------------------------------------------------ drawing
    /**
     * Draws the text clipped to the box, with the selection, and a blinking caret when focused. The view
     * scrolls so the caret is always visible.
     */
    void draw(GuiGraphicsExtractor g, int textX, int cy, int width, int boxY, int boxH, int color, int accent, boolean focused) {
        clamp();
        String t = text();
        int caretPx = textWidth(t.substring(0, caret), false);
        int full = textWidth(t, false);
        if (focused) {
            if (caretPx - scroll > width - S * 2) scroll = caretPx - width + S * 2;
            if (caretPx - scroll < 0) scroll = caretPx;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, full - width + S * 2)));
        int originX = textX - scroll;
        g.enableScissor(textX - S, boxY, textX + width + S, boxY + boxH);
        if (focused && hasSelection()) {
            int a = originX + textWidth(t.substring(0, selStart()), false);
            int b = originX + textWidth(t.substring(0, selEnd()), false);
            fill(g, a, cy - u(7), Math.max(S, b - a), u(14), alpha(accent, 0x70));
        }
        Draw.text(g, originX, cy, t, color, false);
        if (focused && ((System.currentTimeMillis() - blinkFrom) / 500) % 2 == 0)
            fill(g, originX + caretPx, cy - u(7), S, u(14), accent);
        g.disableScissor();
    }
}
