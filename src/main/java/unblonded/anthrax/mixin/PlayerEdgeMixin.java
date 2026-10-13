package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.movement.SafeWalkModule;

/** Safe Walk: the local player keeps to block edges the way sneaking does. */
@Mixin(Player.class)
public abstract class PlayerEdgeMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void anthrax$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == Minecraft.getInstance().player && SafeWalkModule.INSTANCE.holdEdges()) cir.setReturnValue(true);
    }
}
