package unblonded.anthrax.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.NoBlockRotationModule;

/** No Block Rotation: one model variant everywhere (vanilla and Sodium both seed the pick from this). */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockSeedMixin {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void anthrax$fixedSeed(BlockPos pos, CallbackInfoReturnable<Long> cir) {
        if (NoBlockRotationModule.INSTANCE != null && NoBlockRotationModule.INSTANCE.isEnabled()) cir.setReturnValue(0L);
    }
}
