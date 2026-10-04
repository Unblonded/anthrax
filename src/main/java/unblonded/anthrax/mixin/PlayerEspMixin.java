package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.PlayerEspModule;

@Mixin(AvatarRenderer.class)
public abstract class PlayerEspMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void anthrax$outlineOtherPlayers(Avatar entity, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!PlayerEspModule.INSTANCE.isEnabled() || client.player == null || !(entity instanceof Player player)) return;
        if (player.getId() != client.player.getId()) state.outlineColor = PlayerEspModule.INSTANCE.outlineColor();
    }
}
