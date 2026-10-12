package unblonded.anthrax.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.ZoomModule;

/** Scroll adjusts the zoom level while zooming instead of changing the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void anthrax$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (yOffset != 0 && (ZoomModule.INSTANCE.onScroll(yOffset) || unblonded.anthrax.modules.visual.FreecamModule.INSTANCE.onScroll(yOffset))) ci.cancel();
    }
}
