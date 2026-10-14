package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.cosmetic.HandTweaksModule;

/** Hand Tweaks: per-hand tilt, a lowered or hidden offhand, and no switch dip. */
@Mixin(ItemInHandRenderer.class)
public abstract class HandTweaksMixin {
    @Shadow private net.minecraft.world.item.ItemStack mainHandItem;
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void anthrax$tweak(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HandTweaksModule m = HandTweaksModule.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (!m.isEnabled() || entity != mc.player || !context.firstPerson()) return;
        boolean offhand = stack == mc.player.getOffhandItem() && stack != mc.player.getMainHandItem();
        if (offhand && m.hideOffhand()) { ci.cancel(); return; }
        if (offhand) pose.translate(0, -m.offLower(), 0);
        float[] r = offhand ? m.off() : m.main();
        if (r[0] != 0) pose.mulPose(Axis.XP.rotationDegrees(r[0]));
        if (r[1] != 0) pose.mulPose(Axis.YP.rotationDegrees(r[1]));
        if (r[2] != 0) pose.mulPose(Axis.ZP.rotationDegrees(r[2]));
    }

    /** Weapons: knives play their own slash, so the vanilla sword swing of the main arm is skipped. */
    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void anthrax$knifeSwing(float attack, PoseStack pose, int invert, net.minecraft.world.entity.HumanoidArm arm, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && arm == mc.player.getMainArm() && unblonded.anthrax.modules.cosmetic.WeaponsModule.INSTANCE.ownsSwing(mc.player)) ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void anthrax$noEquip(CallbackInfo ci) {
        // Weapons plays its own draw animation for knives, so the vanilla dip would hide it
        var mc = Minecraft.getInstance();
        if (unblonded.anthrax.modules.cosmetic.WeaponsModule.INSTANCE.ownsDraw(mc.player)) {
            // show the knife at once instead of dipping the old item out first
            mainHandItem = mc.player.getMainHandItem();
            mainHandHeight = oMainHandHeight = 1f;
        }
        if (!HandTweaksModule.INSTANCE.noEquip()) return;
        mainHandHeight = oMainHandHeight = 1f;
        offHandHeight = oOffHandHeight = 1f;
    }
}
