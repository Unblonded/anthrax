package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import unblonded.anthrax.modules.cosmetic.WeaponsModule;

/** Weapons: in third person, a held sword is drawn as the chosen knife. */
@Mixin(ItemInHandLayer.class)
public abstract class WeaponLayerMixin {
    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void anthrax$knife(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline,
                               ArmedEntityRenderState state, ItemStackRenderState sameItem, ItemStack stack, HumanoidArm arm,
                               PoseStack samePose, SubmitNodeCollector sameCollector, int sameLight) {
        if (!WeaponsModule.INSTANCE.thirdPerson(state, stack, arm, pose, collector, light)) item.submit(pose, collector, light, overlay, outline);
    }
}
