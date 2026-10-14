package unblonded.anthrax.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.cosmetic.StormEffectsModule;

/** Storm Effects: the client world reads a stormier rain and thunder level; the stored weather is unchanged. */
@Mixin(Level.class)
public abstract class StormMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void anthrax$rain(float partial, CallbackInfoReturnable<Float> cir) {
        if (StormEffectsModule.INSTANCE.isEnabled() && (Object) this instanceof ClientLevel)
            cir.setReturnValue(StormEffectsModule.INSTANCE.rain(cir.getReturnValue()));
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void anthrax$thunder(float partial, CallbackInfoReturnable<Float> cir) {
        if (StormEffectsModule.INSTANCE.isEnabled() && (Object) this instanceof ClientLevel)
            cir.setReturnValue(StormEffectsModule.INSTANCE.thunder(cir.getReturnValue()));
    }
}
