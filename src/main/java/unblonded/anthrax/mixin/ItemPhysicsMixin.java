package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import unblonded.anthrax.render.ItemPhysicsState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.cosmetic.ItemPhysicsModule;

/** Item Physics: no bob or spin on the ground, and flat items laid on their side. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void anthrax$ground(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
        ((ItemPhysicsState) state).anthrax$setOnGround(entity.onGround());
    }

    private static boolean anthrax$still(ItemEntityRenderState state) {
        return ItemPhysicsModule.INSTANCE.isEnabled() && (((ItemPhysicsState) state).anthrax$onGround() || !ItemPhysicsModule.INSTANCE.spinInAir());
    }

    /** The bob is sin(...) * 0.1 + 0.1, so -1 removes it. */
    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float anthrax$noBob(double v, ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        return ItemPhysicsModule.INSTANCE.isEnabled() ? -1f : Mth.sin(v);
    }

    /** A fixed turn per item (from its random bob offset) instead of spinning; tumbling while in the air. */
    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;getSpin(FF)F"))
    private float anthrax$noSpin(float age, float bobOffset, ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!ItemPhysicsModule.INSTANCE.isEnabled()) return ItemEntity.getSpin(age, bobOffset);
        return anthrax$still(state) ? bobOffset * 2.3f : ItemEntity.getSpin(age, bobOffset) * 2f;
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"))
    private void anthrax$layFlat(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!ItemPhysicsModule.INSTANCE.isEnabled()) return;
        AABB box = state.item.getModelBoundingBox();
        float lift = -((float) box.minY) + 0.0625f;   // what vanilla already moved up
        boolean flat = box.getZsize() <= 0.0625;
        if (ItemPhysicsModule.INSTANCE.flat() && flat && anthrax$still(state)) {
            float centreY = (float) (box.minY + box.maxY) / 2f;
            pose.translate(0, -lift + (float) box.getZsize() / 2f + 0.01f, 0);   // down to the ground
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.translate(0, -centreY, 0);
        } else if (anthrax$still(state)) {
            pose.translate(0, -0.0625f, 0);   // sit on the ground instead of hovering
        }
    }
}
