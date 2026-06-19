package unblonded.anthrax.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import unblonded.anthrax.CrystalAuraModule;

public class AnthraxScreen extends Screen {

    private final AnthraxGui gui = new AnthraxGui();

    public AnthraxScreen() {
        super(Component.literal("Anthrax"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        if (gui.isVisible()) {
            gui.begin(gfx, "Anthrax", width, height);

            gui.onTitleBarClicked();

            if (!gui.isCollapsed()) {
                CrystalAuraModule.INSTANCE.setEnabled(
                        gui.checkbox("Crystal Aura", CrystalAuraModule.INSTANCE.isEnabled()));

                gui.separator();

                CrystalAuraModule.INSTANCE.setSmoothing(
                        gui.slider("Smoothing", CrystalAuraModule.INSTANCE.getSmoothing(), 0.05f, 0.8f));

                CrystalAuraModule.INSTANCE.setStepMs(
                        gui.sliderInt("Step MS", CrystalAuraModule.INSTANCE.getStepMs(), 1, 20));

                CrystalAuraModule.INSTANCE.setTickDelay(
                        gui.sliderInt("Tick Delay", CrystalAuraModule.INSTANCE.getTickDelay(), 0, 10));

                gui.end();
            }

        }

        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        gui.onMouseClicked(event);
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        gui.onMouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        gui.onMouseDragged(event);
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}