package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import unblonded.anthrax.modules.Setting;

/**
 * Changes how your character walks, as you see it in third person: a swagger, a zombie shuffle, a stiff robot,
 * a ninja run and more. Client-side only. Applied by PlayerAnimMixin after the vanilla walk is set up.
 */
public final class WalkStyleModule extends CosmeticModule {
    public static final WalkStyleModule INSTANCE = new WalkStyleModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Swagger", "Swagger", "Zombie", "Robot", "Ninja", "Proud", "Sneaky", "Floaty"),
            "Ninja leans in with arms swept back when you sprint, Floaty keeps your legs still as if hovering.");
    private final Setting.Num amount = setting(Setting.Num.standalone("Strength", 1.0, 0.2, 2.0, 0.1), "How exaggerated the style is.");
    private final Setting.Bool everyone = setting(Setting.Bool.standalone("Everyone", false), "Apply the style to every player you see, not just you.");

    private WalkStyleModule() { super("Walk Style", "Change your walking animation: swagger, zombie, robot, ninja and more. Only you see it"); }

    public void pose(PlayerModel m, AvatarRenderState s) {
        if (!isEnabled() || s.isFallFlying || s.isVisuallySwimming || s.isPassenger) return;
        Minecraft mc = Minecraft.getInstance();
        if (!everyone.get() && (mc.player == null || s.id != mc.player.getId())) return;
        float k = (float) (double) amount.get();
        float pos = s.walkAnimationPos, spd = Math.min(1, s.walkAnimationSpeed);
        float swing = Mth.cos(pos * 0.6662f);
        boolean useRight = s.rightArmPose != null && s.rightArmPose != net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY && s.rightArmPose != net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        boolean useLeft = s.leftArmPose != null && s.leftArmPose != net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY && s.leftArmPose != net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        switch (style.get()) {
            case "Swagger" -> {
                if (!useRight) { m.rightArm.xRot *= 1 + 0.6f * k; m.rightArm.zRot += 0.18f * k * spd; }
                if (!useLeft) { m.leftArm.xRot *= 1 + 0.6f * k; m.leftArm.zRot -= 0.18f * k * spd; }
                m.body.yRot += 0.12f * k * swing * spd;
                m.head.zRot += 0.06f * k * swing * spd;
            }
            case "Zombie" -> {
                if (!useRight) { m.rightArm.xRot = -Mth.HALF_PI + 0.08f * swing * spd; m.rightArm.yRot = -0.1f; }
                if (!useLeft) { m.leftArm.xRot = -Mth.HALF_PI - 0.08f * swing * spd; m.leftArm.yRot = 0.1f; }
                m.rightLeg.xRot *= 0.5f;
                m.leftLeg.xRot *= 0.5f;
                m.head.xRot += 0.15f * k;
            }
            case "Robot" -> {
                float step = Math.signum(swing) * 0.5f * spd * k;   // snaps between poses
                if (!useRight) m.rightArm.xRot = -step;
                if (!useLeft) m.leftArm.xRot = step;
                m.rightLeg.xRot = step * 0.8f;
                m.leftLeg.xRot = -step * 0.8f;
                m.head.yRot = Math.round(m.head.yRot / 0.3f) * 0.3f;
            }
            case "Ninja" -> {
                if (spd > 0.6f) {
                    if (!useRight) { m.rightArm.xRot = 1.25f * k; m.rightArm.zRot = 0.15f; }
                    if (!useLeft) { m.leftArm.xRot = 1.25f * k; m.leftArm.zRot = -0.15f; }
                    m.head.xRot -= 0.25f;
                }
            }
            case "Proud" -> {
                if (!useRight) { m.rightArm.xRot *= 0.4f; m.rightArm.zRot += 0.12f; }
                if (!useLeft) { m.leftArm.xRot *= 0.4f; m.leftArm.zRot -= 0.12f; }
                m.head.xRot -= 0.25f * k;
            }
            case "Sneaky" -> {
                if (!useRight) { m.rightArm.xRot = -0.6f * k + 0.2f * swing * spd; m.rightArm.zRot = 0.3f; }
                if (!useLeft) { m.leftArm.xRot = -0.6f * k - 0.2f * swing * spd; m.leftArm.zRot = -0.3f; }
                m.rightLeg.xRot *= 0.6f;
                m.leftLeg.xRot *= 0.6f;
            }
            case "Floaty" -> {
                m.rightLeg.xRot = 0.08f * Mth.sin(s.ageInTicks * 0.1f);
                m.leftLeg.xRot = -0.08f * Mth.sin(s.ageInTicks * 0.1f);
                if (!useRight) m.rightArm.zRot += 0.25f * k;
                if (!useLeft) m.leftArm.zRot -= 0.25f * k;
            }
            default -> {}
        }
    }
}
