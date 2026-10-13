package unblonded.anthrax.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;
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

    /**
     * The section graph rebuild runs on a background thread and reads smartCull from this same state object
     * whenever it gets to it. extractRenderState first copies the global option (true) in and only clears it
     * later, so a rebuild landing in between traced the whole graph with occlusion on from inside the rock and
     * kept it. Feed false in at the source while freecam is active, so it is never true even for a moment.
     */
    @Redirect(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;smartCull:Z", opcode = Opcodes.GETFIELD))
    private boolean anthrax$freecamSmartCull(Minecraft minecraft) {
        return minecraft.smartCull && !FreecamModule.INSTANCE.active();
    }

    /**
     * Freecam pose goes in right after the camera lines up with the player and before update() builds the
     * view matrix and the culling frustum from it. Setting it at the end of update() left the frustum
     * (which Sodium's viewport is built from too) aimed from the player's eyes instead of the camera.
     */
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void anthrax$freecam(DeltaTracker deltaTracker, CallbackInfo ci) {
        FreecamModule freecam = FreecamModule.INSTANCE;
        var freelook = unblonded.anthrax.modules.visual.FreelookModule.INSTANCE;
        if (!freecam.active() && freelook.active()) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            Vec3 eye = player.getEyePosition(deltaTracker.getGameTimeDeltaPartialTick(true));
            if (freelook.thirdPerson()) detached = true;
            setPosition(freelook.position(eye));
            setRotation(freelook.yaw(), freelook.pitch());
            return;
        }
        if (!freecam.active()) return;
        float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
        detached = true;
        setPosition(freecam.position(partial));
        setRotation(freecam.yaw(), freecam.pitch());
    }
}
