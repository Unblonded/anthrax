package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.Safe;
import unblonded.anthrax.modules.cosmetic.ExplosionEffectsModule;
import unblonded.anthrax.modules.cosmetic.PickupStreaksModule;

/** Cosmetics: explosions and your item pickups, read from the packets the server already sends. */
@Mixin(ClientPacketListener.class)
public abstract class CosmeticPacketMixin {
    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void anthrax$explosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        ExplosionEffectsModule m = ExplosionEffectsModule.INSTANCE;
        if (m.isEnabled()) Safe.run(m, () -> m.onExplosion(packet.center(), packet.radius()));
    }

    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void anthrax$pickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        PickupStreaksModule m = PickupStreaksModule.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        // runs on the render thread once the packet is handed over; the first (network thread) call is skipped
        if (!m.isEnabled() || !mc.isSameThread() || mc.level == null || mc.player == null || packet.getPlayerId() != mc.player.getId()) return;
        Entity from = mc.level.getEntity(packet.getItemId());
        if (from != null) Safe.run(m, () -> m.onPickup(from, packet.getAmount()));
    }
}
