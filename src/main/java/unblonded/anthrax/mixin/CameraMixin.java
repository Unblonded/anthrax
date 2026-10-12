package unblonded.anthrax.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.FreecamModule;
import unblonded.anthrax.modules.visual.ZoomModule;

/** Zoom divides the field of view; freecam replaces the camera position and rotation. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void anthrax$zoom(float partial, CallbackInfoReturnable<Float> cir) {
        float factor = ZoomModule.INSTANCE.factor();
        if (factor > 1.001f) cir.setReturnValue(cir.getReturnValueF() / factor);
    }

    /**
     * Occlusion culling walks outward from the camera's section; from a detached camera (often inside
     * blocks, or far from where the player's chunk data was traced) it hides whole areas. Vanilla
     * turns it off for spectators inside blocks; freecam turns it off entirely.
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void anthrax$freecamCulling(CameraRenderState state, float partial, CallbackInfo ci) {
        if (FreecamModule.INSTANCE.active()) state.smartCull = false;
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void anthrax$freecam(DeltaTracker deltaTracker, CallbackInfo ci) {
        FreecamModule freecam = FreecamModule.INSTANCE;
        if (!freecam.active()) return;
        float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
        detached = true;
        setPosition(freecam.position(partial));
        setRotation(freecam.yaw(), freecam.pitch());
    }
}
