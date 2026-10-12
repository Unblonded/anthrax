package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.Safe;
import unblonded.anthrax.modules.world.NewChunksModule;

/** Feeds server block changes to New Chunks (flowing liquid right after a chunk arrives = freshly generated). */
@Mixin(ClientPacketListener.class)
public abstract class NewChunksMixin {
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void anthrax$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        NewChunksModule m = NewChunksModule.INSTANCE;
        if (!m.isEnabled() || !Minecraft.getInstance().isSameThread()) return;
        Safe.run(m, () -> m.onBlockUpdate(packet.getPos(), packet.getBlockState()));
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
    private void anthrax$sectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        NewChunksModule m = NewChunksModule.INSTANCE;
        if (!m.isEnabled() || !Minecraft.getInstance().isSameThread()) return;
        Safe.run(m, () -> packet.runUpdates(m::onBlockUpdate));
    }
}
