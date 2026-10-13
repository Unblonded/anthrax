package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.FreecamModule;

/**
 * Vanilla draws the first-person hand whenever the camera mode is first person, even with a detached camera,
 * so in freecam your arm and held item hung in front of the view and covered the bottom of the screen.
 */
@Mixin(GameRenderer.class)
public abstract class FreecamHandMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void anthrax$noHandInFreecam(CameraRenderState camera, float partial, Matrix4fc projection, CallbackInfo ci) {
        if (FreecamModule.INSTANCE.active()) ci.cancel();
    }
}
