package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.cosmetic.CapeModule;

/** Cape: your own skin gets the client-drawn cape. */
@Mixin(AbstractClientPlayer.class)
public abstract class CapeMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void anthrax$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        if (CapeModule.INSTANCE.isEnabled() && (Object) this == Minecraft.getInstance().player)
            cir.setReturnValue(CapeModule.INSTANCE.apply(cir.getReturnValue()));
    }
}
