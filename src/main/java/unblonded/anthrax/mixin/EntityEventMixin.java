package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.combat.TotemPops;

@Mixin(ClientPacketListener.class)
public abstract class EntityEventMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void anthrax$totemPops(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // The handler first runs on the network thread only to re-schedule itself; act on the client thread.
        if (!mc.isSameThread() || mc.level == null || packet.getEventId() != TotemPops.TOTEM_EVENT) return;
        Entity entity = packet.getEntity(mc.level);
        if (entity != null) TotemPops.onEntityEvent(entity, packet.getEventId());
    }
}
