package unblonded.anthrax.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public class AnthraxGui {
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("anthrax", "segoeui"));
    private static final FontDescription ICON_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("anthrax", "fa-solid-900"));

    private Component icon(String unicode, int color) {
        return Component.literal(unicode).withStyle(s -> s.withColor(color).withFont(ICON_FONT));
    }

    // window state
    private int winX = 20, winY = 20;
    private int winW = 220, winH = 150;
    private boolean collapsed = false;

    private static final int TITLE_H = 14;
    private static final int MIN_W = 150, MIN_H = 60;
    private static final int RESIZE_HANDLE = 8;
    private static final int SLIDER_H = 4;
    private static final int CHECKBOX_SIZE = 7;
    private static final int PADDING = 8;
    private static final int ROW_H = 12;

    // drag/resize state
    private boolean dragging = false;
    private boolean resizing = false;
    private double dragOffX, dragOffY;

    // per-frame input state
    private double mouseX, mouseY;
    private boolean clicking = false;
    private boolean draggingMouse = false;

    private boolean pendingClick = false;
    private double pendingClickX, pendingClickY;
    private boolean visible = true;

    // render state
    private GuiGraphicsExtractor gfx;
    private int cursorY;
    private int screenW, screenH;

    // font helper
    private Component txt(String text, int color) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withFont(FONT));
    }

    public int totalH() {
        return collapsed ? TITLE_H : TITLE_H + winH;
    }

    // --- input ---

    public void onMouseClicked(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        mouseX = mx;
        mouseY = my;
        pendingClick = true;
        pendingClickX = mx;
        pendingClickY = my;

        if (!collapsed && mx >= winX + winW - RESIZE_HANDLE && my >= winY + totalH() - RESIZE_HANDLE
                && mx <= winX + winW && my <= winY + totalH()) {
            resizing = true;
            pendingClick = false;
            return;
        }
        if (mx >= winX && mx <= winX + winW && my >= winY && my <= winY + TITLE_H) {
            dragging = true;
            dragOffX = mx - winX;
            dragOffY = my - winY;
        }
    }

    public void onMouseReleased() {
        dragging = false;
        resizing = false;
        draggingMouse = false;
    }

    public void onMouseDragged(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        mouseX = mx;
        mouseY = my;
        draggingMouse = true;

        if (dragging) {
            winX = (int) Math.max(0, Math.min(screenW - winW, mx - dragOffX));
            winY = (int) Math.max(0, Math.min(screenH - totalH(), my - dragOffY));
        }
        if (resizing) {
            winW = (int) Math.max(MIN_W, mx - winX);
            winH = (int) Math.max(MIN_H, my - winY - TITLE_H);
            int minNeededH = (cursorY - (winY + TITLE_H)) + PADDING;
            winH = (int) Math.max(minNeededH, winH);
        }
    }

    public void onTitleBarClicked() {
        if (!clicking) return;

        // close - hide window
        if (mouseX >= winX + winW - 14 && mouseX <= winX + winW
                && mouseY >= winY && mouseY <= winY + TITLE_H) {
            clicking = false;
            visible = false;
            return;
        }
        // collapse
        if (mouseX >= winX && mouseX <= winX + 16
                && mouseY >= winY && mouseY <= winY + TITLE_H) {
            collapsed = !collapsed;
            clicking = false;
        }
    }

    // --- begin ---

    public void begin(GuiGraphicsExtractor gfx, String title, int screenW, int screenH) {
        this.gfx = gfx;
        this.screenW = screenW;
        this.screenH = screenH;
        this.cursorY = winY + TITLE_H + PADDING;

        clicking = pendingClick;
        mouseX = pendingClick ? pendingClickX : mouseX;
        mouseY = pendingClick ? pendingClickY : mouseY;
        pendingClick = false;

        // title bar
        gfx.fill(winX, winY, winX + winW, winY + TITLE_H, 0xFF1A1A2E);
        gfx.fill(winX, winY + TITLE_H - 1, winX + winW, winY + TITLE_H, 0xFF5555FF);

        gfx.textRenderer().accept(winX + 4, winY + 3, icon(collapsed ? Icons.CHEVRON_RIGHT : Icons.CHEVRON_DOWN, 0xAAAAAA));
        gfx.textRenderer().accept(TextAlignment.CENTER, winX + winW / 2, winY + 3, txt(title, 0xDDDDDD));
        gfx.textRenderer().accept(TextAlignment.RIGHT, winX + winW - 4, winY + 3, icon(Icons.XMARK, 0xFF5555));

        if (!collapsed) {
            gfx.fill(winX, winY + TITLE_H, winX + winW, winY + totalH(), 0xCC0D0D1A);
            gfx.fill(winX, winY, winX + 1, winY + totalH(), 0xFF333366);
            gfx.fill(winX + winW - 1, winY, winX + winW, winY + totalH(), 0xFF333366);
            gfx.fill(winX, winY + totalH() - 1, winX + winW, winY + totalH(), 0xFF333366);
        }
    }

    public boolean isCollapsed() { return collapsed; }
    public boolean isVisible() { return visible; }
    public void show() { visible = true; }
    public void hide() { visible = false; }

    // --- widgets ---

    public void separator() {
        gfx.fill(winX + PADDING, cursorY, winX + winW - PADDING, cursorY + 1, 0xFF333366);
        cursorY += 5;
    }

    public void text(String label, int color) {
        gfx.textRenderer().accept(winX + PADDING, cursorY, txt(label, color));
        cursorY += ROW_H + 2;
    }

    public void text(String label) {
        text(label, 0xAAAAAA);
    }

    public boolean checkbox(String label, boolean value) {
        int cx = winX + PADDING;
        int cy = cursorY;

        gfx.fill(cx, cy, cx + CHECKBOX_SIZE, cy + CHECKBOX_SIZE, 0xFF222244);
        gfx.fill(cx, cy, cx + CHECKBOX_SIZE, cy + 1, 0xFF5555FF);
        gfx.fill(cx, cy + CHECKBOX_SIZE - 1, cx + CHECKBOX_SIZE, cy + CHECKBOX_SIZE, 0xFF5555FF);
        gfx.fill(cx, cy, cx + 1, cy + CHECKBOX_SIZE, 0xFF5555FF);
        gfx.fill(cx + CHECKBOX_SIZE - 1, cy, cx + CHECKBOX_SIZE, cy + CHECKBOX_SIZE, 0xFF5555FF);
        if (value) gfx.fill(cx + 2, cy + 2, cx + CHECKBOX_SIZE - 2, cy + CHECKBOX_SIZE - 2, 0xFF5555FF);

        gfx.textRenderer().accept(cx + CHECKBOX_SIZE + 4, cy - 2, txt(label, 0xFFFFFF));
        //gfx.textRenderer().accept(TextAlignment.RIGHT, winX + winW - PADDING, cy,
        //        txt(value ? "ON" : "OFF", value ? 0x55FF55 : 0xFF5555));

        boolean clicked = clicking
                && mouseX >= cx && mouseX <= winX + winW - PADDING
                && mouseY >= cy && mouseY <= cy + ROW_H;
        cursorY += ROW_H + 4;
        return clicked ? !value : value;
    }

    public float slider(String label, float value, float min, float max) {
        int sx = winX + PADDING;
        int sw = winW - PADDING * 2;
        int sy = cursorY + ROW_H + 2;

        gfx.textRenderer().accept(winX + PADDING, cursorY,
                txt(label + "  " + String.format("%.2f", value), 0xAAAAAA));

        float t = (value - min) / (max - min);
        int fillW = (int)(t * sw);
        gfx.fill(sx, sy, sx + sw, sy + SLIDER_H, 0xFF111122);
        gfx.fill(sx, sy, sx + fillW, sy + SLIDER_H, 0xFF5555FF);
        gfx.fill(sx + fillW - 1, sy - 1, sx + fillW + 1, sy + SLIDER_H + 1, 0xFFAAAAAA);

        if (draggingMouse && mouseY >= sy - 4 && mouseY <= sy + SLIDER_H + 4) {
            float newT = (float) Math.max(0, Math.min(1, (mouseX - sx) / sw));
            value = min + newT * (max - min);
        }

        cursorY += ROW_H + SLIDER_H + 8;
        return value;
    }

    public int sliderInt(String label, int value, int min, int max) {
        int sx = winX + PADDING;
        int sw = winW - PADDING * 2;
        int sy = cursorY + ROW_H + 2;

        gfx.textRenderer().accept(winX + PADDING, cursorY,
                txt(label + "  " + value, 0xAAAAAA));

        float t = (float)(value - min) / (max - min);
        int fillW = (int)(t * sw);
        gfx.fill(sx, sy, sx + sw, sy + SLIDER_H, 0xFF111122);
        gfx.fill(sx, sy, sx + fillW, sy + SLIDER_H, 0xFF5555FF);
        gfx.fill(sx + fillW - 1, sy - 1, sx + fillW + 1, sy + SLIDER_H + 1, 0xFFAAAAAA);

        if (draggingMouse && mouseY >= sy - 4 && mouseY <= sy + SLIDER_H + 4) {
            float newT = (float) Math.max(0, Math.min(1, (mouseX - sx) / sw));
            value = (int)(min + newT * (max - min));
        }

        cursorY += ROW_H + SLIDER_H + 8;
        return value;
    }

    public void end() {
        if (collapsed) return;

        int minNeededH = (cursorY - (winY + TITLE_H)) + PADDING;
        if (winH < minNeededH) winH = minNeededH;

        for (int i = 0; i < RESIZE_HANDLE; i++) {
            int col = winX + winW - RESIZE_HANDLE + i;
            gfx.fill(col, winY + totalH() - i - 1, winX + winW, winY + totalH(), 0xFF5555FF);
        }

        clicking = false;
    }
}