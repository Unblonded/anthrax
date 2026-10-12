package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.NametagsModule;

/** LivingEntityRenderer overrides shouldShowName, so it needs its own hook. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingNametagHideMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void anthrax$hideVanilla(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (NametagsModule.INSTANCE.replaces(entity)) cir.setReturnValue(false);
    }
}
