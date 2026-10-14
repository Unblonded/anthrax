package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.cosmetic.CrystalChamsModule;

/** Crystal Chams: tint or see-through render type, model scale and spin speed for end crystals. */
@Mixin(EndCrystalRenderer.class)
public abstract class CrystalChamsMixin {
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V", at = @At("TAIL"))
    private void anthrax$spin(EndCrystal crystal, EndCrystalRenderState state, float partial, CallbackInfo ci) {
        if (CrystalChamsModule.INSTANCE.isEnabled()) state.ageInTicks *= CrystalChamsModule.INSTANCE.spin();
    }

    @Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void anthrax$scale(PoseStack pose, float x, float y, float z) {
        float s = CrystalChamsModule.INSTANCE.scale();
        pose.scale(x * s, y * s, z * s);
    }

    @Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void anthrax$chams(SubmitNodeCollector collector, Model<? super Object> model, Object state, PoseStack pose, Identifier texture,
                               int light, int overlay, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        CrystalChamsModule m = CrystalChamsModule.INSTANCE;
        if (m.recolour()) collector.submitModel(model, state, pose, m.renderType(), light, overlay, m.tint(), null, outline, crumbling);
        else collector.submitModel(model, state, pose, texture, light, overlay, outline, crumbling);
    }
}
