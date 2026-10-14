package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.cosmetic.HandTweaksModule;

/** Hand Tweaks: how long your own arm swing animation lasts (client animation only). */
@Mixin(LivingEntity.class)
public abstract class SwingSpeedMixin {
    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void anthrax$swing(CallbackInfoReturnable<Integer> cir) {
        float k = HandTweaksModule.INSTANCE.swing();
        if (k != 1f && (Object) this == Minecraft.getInstance().player) cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValue() * k)));
    }
}
