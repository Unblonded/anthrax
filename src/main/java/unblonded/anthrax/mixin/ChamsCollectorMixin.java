package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.ChamsModule;
import unblonded.anthrax.render.ChamsContext;

import java.util.List;

/**
 * Chams for armour and held items: while a chams entity is being submitted (ChamsContext), its armour models and
 * item models are resubmitted in the see-through chams type and colour; glint passes are dropped.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class ChamsCollectorMixin {
    @Inject(method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("HEAD"), cancellable = true)
    private <S> void anthrax$chamsModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay, int tint,
                                        TextureAtlasSprite sprite, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
        if (ChamsContext.tint == 0 || type.isOutline()) return;
        RenderType chams = ChamsModule.INSTANCE.renderType();
        if (type == chams) return;
        String name = ((RenderTypeNameAccessor) type).anthrax$name();
        boolean armour = name.startsWith("armor");
        if (armour ? !ChamsContext.armour : !ChamsContext.items) return;
        ci.cancel();
        if (name.contains("glint")) return;   // the flat colour already covers it
        ((SubmitNodeCollection) (Object) this).submitModel(model, state, pose, chams, light, OverlayTexture.NO_OVERLAY, ChamsContext.tint, null, outline, crumbling);
    }

    @org.spongepowered.asm.mixin.Unique private static boolean anthrax$resubmitting;

    /** Held items: the same quads, resubmitted with the chams type as their material and the chams colour as the tint. */
    @Inject(method = "submitItem", at = @At("HEAD"), cancellable = true)
    private void anthrax$chamsItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline, int[] tints, List<BakedQuad> quads,
                                   ItemStackRenderState.FoilType foil, CallbackInfo ci) {
        if (anthrax$resubmitting || ChamsContext.tint == 0 || !ChamsContext.items || outline != 0) return;
        ci.cancel();
        RenderType chams = ChamsModule.INSTANCE.renderType();
        List<BakedQuad> recoloured = new java.util.ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            var m = q.materialInfo();
            recoloured.add(new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(), q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(),
                    q.direction(), new BakedQuad.MaterialInfo(m.sprite(), m.layer(), chams, 0, false, 15)));
        }
        anthrax$resubmitting = true;
        try {
            ((SubmitNodeCollection) (Object) this).submitItem(pose, context, light, OverlayTexture.NO_OVERLAY, 0, new int[]{ChamsContext.tint}, recoloured, ItemStackRenderState.FoilType.NONE);
        } finally {
            anthrax$resubmitting = false;
        }
    }
}
