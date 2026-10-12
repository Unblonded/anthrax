package unblonded.anthrax.mixin;

import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.TimeChangerModule;

/** Sky, sun and light all read time of day from the client clock, so overriding it here is purely visual. */
@Mixin(ClientClockManager.class)
public abstract class ClientClockMixin {
    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
    private void anthrax$timeChanger(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        Long real = cir.getReturnValue();
        if (real != null && TimeChangerModule.INSTANCE.isEnabled())
            cir.setReturnValue(TimeChangerModule.INSTANCE.override(real));
    }
}
