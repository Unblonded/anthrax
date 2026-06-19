package unblonded.anthrax.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import unblonded.anthrax.CrystalAuraModule;

public class AnthraxScreen extends Screen {

    private final AnthraxGui anthraxGui = new AnthraxGui(20, 20, "Anthrax");
    private final AnthraxGui testGui = new AnthraxGui(250, 20, "Test");

    private final AnthraxGui[] guis = { anthraxGui, testGui };
    private AnthraxGui focusedGui = null;

    public AnthraxScreen() {
        super(Component.literal("Anthrax"));
    }

    @Override
    protected void init() {
        for (AnthraxGui gui : guis) gui.show();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        for (AnthraxGui gui : guis) {
            if (!gui.isVisible()) continue;
            gui.begin(gfx, gui.getTitle(), width, height);
            gui.onTitleBarClicked();
            if (!gui.isCollapsed()) {
                drawContents(gui);
                gui.end();
            }
        }
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    private void drawContents(AnthraxGui gui) {
        if (gui == anthraxGui) {
            CrystalAuraModule.INSTANCE.setEnabled(
                    gui.checkbox("Crystal Aura", CrystalAuraModule.INSTANCE.isEnabled()));
            gui.separator();
            CrystalAuraModule.INSTANCE.setSmoothing(
                    gui.slider("Smoothing", CrystalAuraModule.INSTANCE.getSmoothing(), 0.05f, 0.8f));
            CrystalAuraModule.INSTANCE.setStepMs(
                    gui.sliderInt("Step MS", CrystalAuraModule.INSTANCE.getStepMs(), 1, 20));
            CrystalAuraModule.INSTANCE.setTickDelay(
                    gui.sliderInt("Tick Delay", CrystalAuraModule.INSTANCE.getTickDelay(), 0, 10));
        } else if (gui == testGui) {
            gui.text("hello from test gui");
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        focusedGui = null;
        for (int i = guis.length - 1; i >= 0; i--) {
            if (guis[i].isVisible() && guis[i].isHovered(event.x(), event.y())) {
                focusedGui = guis[i];
                break;
            }
        }
        if (focusedGui != null) focusedGui.onMouseClicked(event);
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (focusedGui != null) focusedGui.onMouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (focusedGui != null) focusedGui.onMouseDragged(event);
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}