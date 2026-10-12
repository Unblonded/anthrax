package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.FreecamModule;
import unblonded.anthrax.modules.visual.ZoomModule;

/** Mouse look: steers the freecam when active, and is slowed down while zoomed for precise aim. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void anthrax$freecamLook(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player || !FreecamModule.INSTANCE.active()) return;
        FreecamModule.INSTANCE.turn(yRot, xRot);
        ci.cancel();
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double anthrax$zoomYaw(double yRot) {
        return (Object) this == Minecraft.getInstance().player ? yRot * ZoomModule.INSTANCE.sensitivity() : yRot;
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double anthrax$zoomPitch(double xRot) {
        return (Object) this == Minecraft.getInstance().player ? xRot * ZoomModule.INSTANCE.sensitivity() : xRot;
    }
}
