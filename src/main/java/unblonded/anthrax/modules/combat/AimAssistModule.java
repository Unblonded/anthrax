package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Softly steers the view toward the best visible player inside the configured cone. The target is
 * chosen every game tick; the rotation itself is applied every frame with interpolated positions,
 * so tracking stays smooth at any frame rate.
 */
public final class AimAssistModule extends FeatureModule {
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 6.8, 1, 20, .1));
    private final Setting.Num fov = setting(Setting.Num.standalone("Field of view", 128.2, 1, 180, .1));
    private final Setting.Num smoothing = setting(Setting.Num.standalone("Smoothness", 1, .1, 10, .1), "Higher values ease onto the target more gently.");
    private final Setting.Num minSpeed = setting(Setting.Num.standalone("Min speed", 143, 1, 360, 1));
    private final Setting.Num maxSpeed = setting(Setting.Num.standalone("Max speed", 146, 1, 360, 1));
    private final Setting.Mode aimPoint = setting(Setting.Mode.standalone("Aim point", "Head", "Head", "Chest", "Feet"), "Which part of the target's body the aim settles on.");
    private final Setting.Bool visibility = setting(Setting.Bool.standalone("Visibility check", true), "Ignore targets behind walls.");
    private final Setting.Bool weaponOnly = setting(Setting.Bool.standalone("Only holding weapon", false));
    private final Setting.Bool ignoreInvisible = setting(Setting.Bool.standalone("Ignore invisible", true));
    private Player target;

    public AimAssistModule() { super("Aim Assist", "Smoothly track the nearest visible player", "Combat", false); }

    @Override public void tick(Minecraft client) {
        target = null;
        LocalPlayer self = client.player;
        if (self == null || client.level == null || client.gui.screen() != null) return;
        if (weaponOnly.get() && !self.getMainHandItem().is(ItemTags.SWORDS) && !self.getMainHandItem().is(ItemTags.AXES)) return;
        double best = Double.MAX_VALUE, rangeSq = range.get() * range.get();
        for (Player candidate : client.level.players()) {
            if (candidate == self || candidate.isRemoved() || !candidate.isAlive() || candidate.isSpectator()
                    || (ignoreInvisible.get() && candidate.isInvisible())
                    || unblonded.anthrax.modules.client.FriendsModule.protects(candidate)) continue;
            double distanceSq = self.distanceToSqr(candidate);
            if (distanceSq > rangeSq || (visibility.get() && !self.hasLineOfSight(candidate))) continue;
            float[] rotation = rotationTo(self, aimPos(candidate, 1f), 1f);
            float yawError = Math.abs(Mth.wrapDegrees(rotation[0] - self.getYRot()));
            float pitchError = Math.abs(rotation[1] - self.getXRot());
            if (yawError > fov.get() || pitchError > fov.get()) continue;
            double score = Math.sqrt(yawError * yawError + pitchError * pitchError) * 3 + distanceSq;
            if (score < best) { best = score; target = candidate; }
        }
    }

    @Override public void frame(Minecraft client, float dt) {
        LocalPlayer self = client.player;
        Player aimed = target;
        if (self == null || aimed == null || aimed.isRemoved() || !aimed.isAlive() || client.gui.screen() != null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float[] rotation = rotationTo(self, aimPos(aimed, partial), partial);
        float yawDiff = Mth.wrapDegrees(rotation[0] - self.getYRot());
        float pitchDiff = rotation[1] - self.getXRot();
        float angular = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        if (angular < .05f) return;
        // Proportional speed (faster when far off target), clamped to the configured band.
        float speed = Mth.clamp(angular * 6f, (float) minSpeed.get(), (float) Math.max(minSpeed.get(), maxSpeed.get()));
        float step = Math.min(angular, speed * dt / (float) smoothing.get());
        float factor = step / angular;
        // turn() updates both current and previous rotation, so the camera does not jitter between frames.
        self.turn(yawDiff * factor / 0.15f, pitchDiff * factor / 0.15f);
    }

    private Vec3 aimPos(Player target, float partial) {
        Vec3 base = target.getPosition(partial);
        double height = switch (aimPoint.get()) {
            case "Chest" -> target.getBbHeight() * 0.6;
            case "Feet" -> 0.2;
            default -> target.getEyeHeight();
        };
        return base.add(0, height, 0);
    }

    private static float[] rotationTo(LocalPlayer self, Vec3 point, float partial) {
        Vec3 eye = self.getEyePosition(partial);
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        return new float[]{(float) (Math.toDegrees(Math.atan2(dz, dx)) - 90),
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    @Override public String hudInfo() { return target == null ? null : target.getName().getString(); }
    @Override protected void onToggle(boolean enabled) { target = null; }
}
