package unblonded.anthrax.mixin;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.HandRenderModule;

@Mixin(ItemInHandRenderer.class)
public abstract class HandRenderMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void anthrax$handPose(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                  PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HandRenderModule module = HandRenderModule.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || entity != mc.player || context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
        pose.scale(module.scale(), module.scale(), module.scale());
        pose.translate(module.x(), module.y(), module.z());
        if (module.swordBlocking() && mc.options.keyUse.isDown() && stack.is(ItemTags.SWORDS)) {
            pose.translate(0, .05, 0);
            pose.mulPose(Axis.XP.rotationDegrees(75));
            pose.mulPose(Axis.YP.rotationDegrees(165));
            pose.mulPose(Axis.ZP.rotationDegrees(-95));
        }
    }

}
