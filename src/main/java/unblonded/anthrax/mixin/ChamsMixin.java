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

    /** While this entity's layers are submitted, armour and held items take the chams look as well. */
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void anthrax$chamsLayersOn(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                                       net.minecraft.client.renderer.state.level.CameraRenderState camera, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ChamsModule m = ChamsModule.INSTANCE;
        boolean on = m.applies(state) && (m.armour() || m.heldItems());
        unblonded.anthrax.render.ChamsContext.tint = on ? m.tint(state) : 0;
        unblonded.anthrax.render.ChamsContext.armour = on && m.armour();
        unblonded.anthrax.render.ChamsContext.items = on && m.heldItems();
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
    private void anthrax$chamsLayersOff(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                                        net.minecraft.client.renderer.state.level.CameraRenderState camera, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        unblonded.anthrax.render.ChamsContext.tint = 0;
    }

    @Inject(method = "getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I", at = @At("RETURN"), cancellable = true)
    private void anthrax$chamsTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (ChamsModule.INSTANCE.applies(state)) cir.setReturnValue(ChamsModule.INSTANCE.tint(state));
    }
}
