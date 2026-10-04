package unblonded.anthrax.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnthraxGui {
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("anthrax", "segoeui"));
    private static final FontDescription ICON_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("anthrax", "fa-solid-900"));

    // ---------------------------------------------------------------- component cache
    // Styled components are immutable, so build each (text, color, font) once and reuse it every
    // frame. Lookups use a reusable probe key so a cache hit allocates nothing. Render thread only.
    private static final class CKey {
        String text;
        int color;
        boolean icon;

        CKey() {}

        CKey(String text, int color, boolean icon) {
            this.text = text;
            this.color = color;
            this.icon = icon;
        }

        @Override public int hashCode() {
            return 31 * (31 * text.hashCode() + color) + (icon ? 1 : 0);
        }

        @Override public boolean equals(Object o) {
            return o instanceof CKey k && color == k.color && icon == k.icon && text.equals(k.text);
        }
    }

    private static final CKey PROBE = new CKey();
    private static final Map<CKey, Component> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<CKey, Component> eldest) {
            return size() > 1024;
        }
    };

    private static Component styled(String text, int color, boolean icon) {
        PROBE.text = text;
        PROBE.color = color;
        PROBE.icon = icon;
        Component cached = CACHE.get(PROBE);
        if (cached != null) return cached;
        Component made = Component.literal(text).withStyle(s -> s.withColor(color).withFont(icon ? ICON_FONT : FONT));
        CACHE.put(new CKey(text, color, icon), made);
        return made;
    }

    private Component icon(String unicode, int color) {
        return styled(unicode, color, true);
    }

    // font helper
    private Component txt(String text, int color) {
        return styled(text, color, false);
    }

    // ---------------------------------------------------------------- slider label cache
    // "label  1.23" is only rebuilt when the displayed value actually changes (no String.format
    // and no concatenation every frame).
    private static final class LabelEntry {
        int key = Integer.MIN_VALUE;
        boolean fl;
        String text;
    }

    private final Map<String, LabelEntry> labels = new HashMap<>();

    private static String fmt2(int hundredths) {
        boolean neg = hundredths < 0;
        int a = Math.abs(hundredths);
        int frac = a % 100;
        return (neg ? "-" : "") + (a / 100) + "." + (frac < 10 ? "0" : "") + frac;
    }

    private String sliderLabel(String label, int key, boolean fl) {
        LabelEntry e = labels.get(label);
        if (e == null) {
            e = new LabelEntry();
            labels.put(label, e);
        }
        if (e.text == null || e.key != key || e.fl != fl) {
            e.key = key;
            e.fl = fl;
            e.text = label + "  " + (fl ? fmt2(key) : Integer.toString(key));
        }
        return e.text;
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

    public int totalH() {
        return collapsed ? TITLE_H : TITLE_H + winH;
    }

    private final String title;

    public AnthraxGui(int x, int y, String title) {
        this.winX = x;
        this.winY = y;
        this.title = title;
    }

    public String getTitle() { return title; }

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

        // border rect + inner rect (2 fills instead of 5)
        gfx.fill(cx, cy, cx + CHECKBOX_SIZE, cy + CHECKBOX_SIZE, 0xFF5555FF);
        gfx.fill(cx + 1, cy + 1, cx + CHECKBOX_SIZE - 1, cy + CHECKBOX_SIZE - 1, 0xFF222244);
        if (value) gfx.fill(cx + 2, cy + 2, cx + CHECKBOX_SIZE - 2, cy + CHECKBOX_SIZE - 2, 0xFF5555FF);

        gfx.textRenderer().accept(cx + CHECKBOX_SIZE + 4, cy - 2, txt(label, 0xFFFFFF));

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
                txt(sliderLabel(label, Math.round(value * 100f), true), 0xAAAAAA));

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
                txt(sliderLabel(label, value, false), 0xAAAAAA));

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

        // resize grip: a few stair-step fills instead of one per pixel column
        for (int i = 0; i < RESIZE_HANDLE; i += 2) {
            int col = winX + winW - RESIZE_HANDLE + i;
            gfx.fill(col, winY + totalH() - i - 2, winX + winW, winY + totalH(), 0xFF5555FF);
        }

        clicking = false;
    }

    public boolean isHovered(double mx, double my) {
        return mx >= winX && mx <= winX + winW && my >= winY && my <= winY + totalH();
    }
}