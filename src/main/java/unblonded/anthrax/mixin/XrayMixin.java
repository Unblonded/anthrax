package unblonded.anthrax.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.XrayModule;

/**
 * Xray: hidden blocks have no model and occlude nothing, so the chosen blocks show from every side. Chunk
 * meshing (vanilla and Sodium) reads exactly these, then the module rebuilds the chunks.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class XrayMixin {
    private BlockState anthrax$self() { return (BlockState) (Object) this; }

    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void anthrax$hideModel(CallbackInfoReturnable<RenderShape> cir) {
        if (XrayModule.hides(anthrax$self())) cir.setReturnValue(RenderShape.INVISIBLE);
    }

    @Inject(method = "canOcclude", at = @At("HEAD"), cancellable = true)
    private void anthrax$noOcclude(CallbackInfoReturnable<Boolean> cir) {
        if (XrayModule.hides(anthrax$self())) cir.setReturnValue(false);
    }

    @Inject(method = "isSolidRender", at = @At("HEAD"), cancellable = true)
    private void anthrax$notSolid(CallbackInfoReturnable<Boolean> cir) {
        if (XrayModule.hides(anthrax$self())) cir.setReturnValue(false);
    }

    /**
     * Ambient occlusion darkens a corner for every solid block around it. Hidden blocks still counted, so ores
     * deep in rock came out dim; they now cast no shade (vanilla and Sodium both read these two).
     */
    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void anthrax$noShade(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (XrayModule.hides(anthrax$self())) cir.setReturnValue(1f);
    }

    @Inject(method = "isViewBlocking", at = @At("HEAD"), cancellable = true)
    private void anthrax$notViewBlocking(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (XrayModule.hides(anthrax$self())) cir.setReturnValue(false);
    }

    @Inject(method = "getFaceOcclusionShape", at = @At("HEAD"), cancellable = true)
    private void anthrax$noFaceOcclusion(Direction dir, CallbackInfoReturnable<VoxelShape> cir) {
        if (XrayModule.hides(anthrax$self())) cir.setReturnValue(Shapes.empty());
    }
}
