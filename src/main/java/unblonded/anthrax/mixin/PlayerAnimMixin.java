package unblonded.anthrax.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.cosmetic.EmotesModule;
import unblonded.anthrax.modules.cosmetic.WalkStyleModule;

/** Walk Style and Emotes: adjust the player pose after vanilla has set it up. */
@Mixin(PlayerModel.class)
public abstract class PlayerAnimMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void anthrax$pose(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel self = (PlayerModel) (Object) this;
        WalkStyleModule.INSTANCE.pose(self, state);
        EmotesModule.INSTANCE.pose(self, state);
    }
}
