package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.combat.ServerSafety;

/** Notes every server-side position correction (rubber-band or teleport) so automation can back off. */
@Mixin(ClientPacketListener.class)
public abstract class ServerTeleportMixin {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void anthrax$serverMovedUs(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        // packets arrive on the network thread first and are re-run on the client thread; count that pass
        if (Minecraft.getInstance().isSameThread()) ServerSafety.onServerTeleport();
    }
}
