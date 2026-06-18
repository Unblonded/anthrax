package unblonded.anthrax;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class AnthraxScreen extends Screen {

    // window state
    private int winX = 20, winY = 20;
    private int winW = 210, winH = 170;
    private boolean collapsed = false;

    private static final int TITLE_H = 14;
    private static final int MIN_W = 150, MIN_H = 80;
    private static final int RESIZE_HANDLE = 6;

    // drag state
    private boolean dragging = false;
    private boolean resizing = false;
    private double dragOffX, dragOffY;

    public AnthraxScreen() {
        super(Component.literal("Anthrax"));
    }

    private int contentH() {
        return collapsed ? 0 : winH - TITLE_H;
    }

    private int totalH() {
        return TITLE_H + contentH();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        // title bar
        gfx.fill(winX, winY, winX + winW, winY + TITLE_H, 0xFF1A1A2E);
        gfx.fill(winX, winY + TITLE_H - 1, winX + winW, winY + TITLE_H, 0xFF5555FF);

        // collapse button
        String collapseIcon = collapsed ? "▸" : "▾";
        gfx.textRenderer().accept(winX + 4, winY + 3, Component.literal(collapseIcon).withStyle(s -> s.withColor(0xAAAAAA)));

        // title centered
        gfx.textRenderer().accept(TextAlignment.CENTER, winX + winW / 2, winY + 3,
                Component.literal("Anthrax").withStyle(s -> s.withColor(0xDDDDDD)));

        // close button
        gfx.textRenderer().accept(TextAlignment.RIGHT, winX + winW - 4, winY + 3,
                Component.literal("✕").withStyle(s -> s.withColor(0xFF5555)));

        if (!collapsed) {
            // content background
            gfx.fill(winX, winY + TITLE_H, winX + winW, winY + totalH(), 0xCC0D0D1A);

            // border
            gfx.fill(winX, winY, winX + 1, winY + totalH(), 0xFF333366);
            gfx.fill(winX + winW - 1, winY, winX + winW, winY + totalH(), 0xFF333366);
            gfx.fill(winX, winY + totalH() - 1, winX + winW, winY + totalH(), 0xFF333366);

            int y = winY + TITLE_H + 8;
            int contentW = winW - 24;

            // crystal aura toggle
            boolean enabled = CrystalAuraModule.INSTANCE.isEnabled();
            gfx.textRenderer().accept(winX + 12, y, Component.literal("Crystal Aura").withStyle(s -> s.withColor(0xFFFFFF)));
            gfx.textRenderer().accept(TextAlignment.RIGHT, winX + winW - 12, y,
                    Component.literal(enabled ? "ON" : "OFF").withStyle(s -> s.withColor(enabled ? 0x55FF55 : 0xFF5555)));
            y += 18;

            // smoothing
            gfx.textRenderer().accept(winX + 12, y, Component.literal("Smoothing  " + String.format("%.2f", CrystalAuraModule.INSTANCE.getSmoothing())).withStyle(s -> s.withColor(0xAAAAAA)));
            y += 10;
            drawSlider(gfx, winX + 12, y, contentW, 4, CrystalAuraModule.INSTANCE.getSmoothing(), 0.05f, 0.8f);
            y += 14;

            // step ms
            gfx.textRenderer().accept(winX + 12, y, Component.literal("Step MS  " + CrystalAuraModule.INSTANCE.getStepMs()).withStyle(s -> s.withColor(0xAAAAAA)));
            y += 10;
            drawSlider(gfx, winX + 12, y, contentW, 4, CrystalAuraModule.INSTANCE.getStepMs(), 1, 20);
            y += 14;

            // tick delay
            gfx.textRenderer().accept(winX + 12, y, Component.literal("Tick Delay  " + CrystalAuraModule.INSTANCE.getTickDelay()).withStyle(s -> s.withColor(0xAAAAAA)));
            y += 10;
            drawSlider(gfx, winX + 12, y, contentW, 4, CrystalAuraModule.INSTANCE.getTickDelay(), 0, 10);

            // resize triangle (bottom right corner)
            for (int i = 0; i < RESIZE_HANDLE; i++) {
                gfx.fill(winX + winW - RESIZE_HANDLE + i, winY + totalH() - i - 1,
                        winX + winW - RESIZE_HANDLE + i + 1, winY + totalH() - i, 0xFF5555FF);
            }
        }

        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    private void drawSlider(GuiGraphicsExtractor gfx, int x, int y, int w, int h, float value, float min, float max) {
        float t = (value - min) / (max - min);
        int fillW = (int)(t * w);
        gfx.fill(x, y, x + w, y + h, 0xFF1A1A2E);
        gfx.fill(x, y, x + fillW, y + h, 0xFF5555FF);
        gfx.fill(x + fillW - 1, y - 1, x + fillW + 1, y + h + 1, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        // close button
        if (mx >= winX + winW - 14 && mx <= winX + winW && my >= winY && my <= winY + TITLE_H) {
            onClose();
            return true;
        }

        // collapse button
        if (mx >= winX && mx <= winX + 16 && my >= winY && my <= winY + TITLE_H) {
            collapsed = !collapsed;
            return true;
        }

        // toggle crystal aura
        if (!collapsed && mx >= winX + 12 && mx <= winX + winW - 12
                && my >= winY + TITLE_H + 8 && my <= winY + TITLE_H + 20) {
            CrystalAuraModule.INSTANCE.toggle();
            return true;
        }

        // resize handle
        if (!collapsed && mx >= winX + winW - RESIZE_HANDLE && mx <= winX + winW
                && my >= winY + totalH() - RESIZE_HANDLE && my <= winY + totalH()) {
            resizing = true;
            return true;
        }

        // drag title bar
        if (mx >= winX && mx <= winX + winW && my >= winY && my <= winY + TITLE_H) {
            dragging = true;
            dragOffX = mx - winX;
            dragOffY = my - winY;
            return true;
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        resizing = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mx = event.x();
        double my = event.y();

        if (dragging) {
            winX = (int)(mx - dragOffX);
            winY = (int)(my - dragOffY);
            // clamp to screen
            winX = Math.max(0, Math.min(width - winW, winX));
            winY = Math.max(0, Math.min(height - totalH(), winY));
            return true;
        }

        if (resizing) {
            winW = (int) Math.max(MIN_W, mx - winX);
            winH = (int) Math.max(MIN_H, my - winY - TITLE_H + TITLE_H);
            return true;
        }

        // sliders
        int sliderX = winX + 12;
        int sliderW = winW - 24;
        float t = (float) Math.max(0, Math.min(1, (mx - sliderX) / sliderW));

        int baseY = winY + TITLE_H + 8;
        if (my >= baseY + 28 && my <= baseY + 34) {
            CrystalAuraModule.INSTANCE.setSmoothing(0.05f + t * (0.8f - 0.05f));
            return true;
        }
        if (my >= baseY + 56 && my <= baseY + 62) {
            CrystalAuraModule.INSTANCE.setStepMs((int)(1 + t * 19));
            return true;
        }
        if (my >= baseY + 84 && my <= baseY + 90) {
            CrystalAuraModule.INSTANCE.setTickDelay((int)(t * 10));
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}