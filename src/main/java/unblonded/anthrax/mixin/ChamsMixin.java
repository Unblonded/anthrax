package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.ChamsModule;

/** Chams: the body model uses the see-through flat type, tinted with the chams colour. */
@Mixin(LivingEntityRenderer.class)
public abstract class ChamsMixin {
    @Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("RETURN"), cancellable = true)
    private void anthrax$chamsType(LivingEntityRenderState state, boolean visible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (ChamsModule.INSTANCE.applies(state)) cir.setReturnValue(ChamsModule.INSTANCE.renderType());
    }

    @Inject(method = "getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I", at = @At("RETURN"), cancellable = true)
    private void anthrax$chamsTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (ChamsModule.INSTANCE.applies(state)) cir.setReturnValue(ChamsModule.INSTANCE.tint(state));
    }
}
