package unblonded.anthrax.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.gui.ScanBox;

/**
 * Draws overlays on top of a whole screen, after the screen and its subclasses have drawn everything
 * (several container screens override their drawing without calling up the chain).
 */
@Mixin(Screen.class)
public abstract class ScreenOverlayMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void anthrax$overlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof AbstractContainerScreen<?> container && ScanBox.active()) {
            graphics.nextStratum();
            ScanBox.render(graphics, container.getMenu());
        }
    }
}
