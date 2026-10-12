package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.NametagsModule;

/** Hides vanilla name tags for entities that get an Anthrax nametag instead. */
@Mixin(EntityRenderer.class)
public abstract class NametagHideMixin {
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void anthrax$hideVanilla(Entity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (NametagsModule.INSTANCE.replaces(entity)) cir.setReturnValue(false);
    }
}
