package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Softly steers view toward the closest visible player inside the configured cone. */
public final class AimAssistModule extends FeatureModule {
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 6.8, 1, 20, .1));
    private final Setting.Num fov = setting(Setting.Num.standalone("Field of view", 128.2, 1, 180, .1));
    private final Setting.Num smoothing = setting(Setting.Num.standalone("Smoothness", 1, .1, 10, .1));
    private final Setting.Num minSpeed = setting(Setting.Num.standalone("Min speed", 143, 1, 360, 1));
    private final Setting.Num maxSpeed = setting(Setting.Num.standalone("Max speed", 146, 1, 360, 1));
    private final Setting.Bool visibility = setting(Setting.Bool.standalone("Visibility check", true));
    private final Setting.Num updateRate = setting(Setting.Num.standalone("Update rate (ms)", 10, 1, 100, 1));
    private long lastUpdate;
    public AimAssistModule() { super("Aim Assist", "Smoothly track the nearest visible player", "Combat", false); }

    @Override public void tick(Minecraft client) {
        if (client.player == null || client.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastUpdate < (long) updateRate.get()) return;
        float deltaTime = Math.min(.1f, Math.max(.001f, (now - lastUpdate) / 1000f));
        lastUpdate = now;
        Player target = null;
        double best = Double.MAX_VALUE;
        double rangeSquared = range.get() * range.get();
        for (Player candidate : client.level.players()) {
            if (candidate == client.player || candidate.isRemoved() || !candidate.isAlive()
                    || client.player.distanceToSqr(candidate) > rangeSquared
                    || (visibility.get() && !client.player.hasLineOfSight(candidate))) continue;
            double distance = client.player.distanceToSqr(candidate);
            float[] rotation = rotation(client, candidate);
            float yawError = Math.abs(wrap(rotation[0] - client.player.getYRot()));
            float pitchError = Math.abs(wrap(rotation[1] - client.player.getXRot()));
            double score = Math.sqrt(yawError*yawError + pitchError*pitchError) * 3 + distance;
            if (score < best) { best = score; target = candidate; }
        }
        if (target == null) return;
        float[] rotation = rotation(client, target);
        float yaw = rotation[0], pitch = rotation[1];
        float yawDiff = wrap(yaw - client.player.getYRot());
        float pitchDiff = pitch - client.player.getXRot();
        if (Math.abs(yawDiff) > fov.get() || Math.abs(pitchDiff) > fov.get()) return;
        float angularDelta = (float)Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        if (angularDelta < .001f) return;
        float speed = Math.max((float)minSpeed.get(), Math.min((float)maxSpeed.get(), angularDelta / 5f));
        float movement = Math.min(angularDelta, speed * deltaTime) / angularDelta / (float)smoothing.get();
        float factor = Math.min(1f, movement);
        client.player.setYRot(client.player.getYRot() + yawDiff * factor);
        client.player.setXRot(client.player.getXRot() + pitchDiff * factor);
    }

    private static float wrap(float angle) { angle %= 360; if (angle >= 180) angle -= 360; if (angle < -180) angle += 360; return angle; }

    private static float[] rotation(Minecraft client, Player target) {
        double dx = target.getX() - client.player.getX();
        double dz = target.getZ() - client.player.getZ();
        double dy = target.getEyeY() - client.player.getEyeY();
        return new float[]{(float)(Math.atan2(dz, dx) * 180 / Math.PI) - 90,
                (float)(-Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)) * 180 / Math.PI)};
    }
}
