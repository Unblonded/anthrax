package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.combat.VelocityModule;

/**
 * Knockback scaling. Packets first arrive on the network thread and are re-run on the client thread;
 * only the client-thread pass touches the player.
 */
@Mixin(ClientPacketListener.class)
public abstract class VelocityMixin {
    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void anthrax$hitKnockback(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null || packet.id() != mc.player.getId() || !VelocityModule.INSTANCE.isEnabled()) return;
        mc.player.setDeltaMovement(VelocityModule.INSTANCE.scaleHit(packet.movement()));
        ci.cancel();
    }

    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void anthrax$explosionKnockback(ClientboundExplodePacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null || packet.playerKnockback().isEmpty()) return;
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().subtract(VelocityModule.INSTANCE.explosionRefund(packet.playerKnockback().get())));
    }
}
