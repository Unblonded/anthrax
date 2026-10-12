package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.Comparator;

/** After you place a crystal, smoothly turns onto it and breaks it. */
public final class CrystalAuraModule extends FeatureModule {
    public static final CrystalAuraModule INSTANCE = new CrystalAuraModule();
    private static final int MAX_STEPS = 20;     // max steps before force finish
    private static final double REACH_SQ = 4.5 * 4.5;

    private final Setting.Num smoothing = setting(Setting.Num.standalone("Smoothing", 0.18, 0.05, 0.8, 0.01));
    private final Setting.Num stepMs = setting(Setting.Num.standalone("Step interval", 2, 1, 20, 1));
    private final Setting.Num placementDelay = setting(Setting.Num.standalone("Placement delay", 2, 0, 10, 1));

    private static final int WAIT_TIMEOUT_TICKS = 20;
    private boolean waitingForCrystal = false;
    private int ticksUntilTarget = 0;
    private int waitedTicks = 0;
    private EndCrystal pendingCrystal = null;

    private float targetYaw;
    private float targetPitch;
    private boolean rotating = false;
    private int stepCount = 0;

    private long lastRotationStep;

    private CrystalAuraModule() {
        super("Crystal Aura", "Turn onto and break crystals right after you place them", "Combat", false);
    }

    public void onCrystalPlace() {
        if (!isEnabled()) return;
        waitingForCrystal = true;
        waitedTicks = 0;
        ticksUntilTarget = (int) placementDelay.get();
    }

    @Override public void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            clearPending();
            return;
        }

        if (waitingForCrystal && !rotating) {
            // The server never confirmed a crystal (e.g. placement failed): stop waiting.
            if (++waitedTicks > WAIT_TIMEOUT_TICKS + placementDelay.get()) { clearPending(); return; }
            if (ticksUntilTarget > 0) {
                ticksUntilTarget--;
            } else {
                client.player.level().getEntitiesOfClass(
                            EndCrystal.class,
                            client.player.getBoundingBox().inflate(8),
                            e -> e.isAlive() && e.distanceToSqr(client.player.getEyePosition()) <= REACH_SQ
                                    && ServerSafety.canReach(client.player, e)
                    ).stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(client.player)))
                    .ifPresent(crystal -> {
                        computeTarget(client.player, crystal);
                        pendingCrystal = crystal;
                        stepCount = 0;
                        rotating = true;
                        waitingForCrystal = false;
                    });
            }
        }

    }

    /** Rotation runs per frame so the configured millisecond step interval is actually honoured. */
    @Override public void frame(Minecraft client, float dt) {
        if (!rotating || client.player == null || client.gameMode == null) return;
        EndCrystal crystal = pendingCrystal;
        if (crystal == null || crystal.isRemoved()) { clearPending(); return; }
        long now = System.currentTimeMillis();
        if (now - lastRotationStep < (long) stepMs.get()) return;
        lastRotationStep = now;

        Player player = client.player;
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();
        float yawDiff = wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float blend = Math.max(0.01f, Math.min(1f, (float) smoothing.get()));
        float newYaw = currentYaw + yawDiff * blend;
        float newPitch = Math.max(-90f, Math.min(90f, currentPitch + pitchDiff * blend));
        setRotation(player, newYaw, newPitch);
        stepCount++;

        boolean close = Math.abs(wrapDegrees(targetYaw - newYaw)) < 0.5f
                && Math.abs(targetPitch - newPitch) < 0.5f;
        if ((isLookingAtCrystal(player, crystal) || close || stepCount >= MAX_STEPS) && !ServerSafety.paused()) {
            setRotation(player, targetYaw, targetPitch);
            client.gameMode.attack(player, crystal);
            player.swing(InteractionHand.MAIN_HAND);
            clearPending();
        }
    }

    /** Turns by the delta to (yaw, pitch); turn() also moves the previous rotation, avoiding render jitter. */
    private void setRotation(Player player, float yaw, float pitch) {
        float yawDelta = wrapDegrees(yaw - player.getYRot());
        float pitchDelta = Math.max(-90f, Math.min(90f, pitch)) - player.getXRot();
        player.turn(yawDelta / 0.15f, pitchDelta / 0.15f);
    }

    private void clearPending() {
        waitingForCrystal = false;
        ticksUntilTarget = 0;
        waitedTicks = 0;
        pendingCrystal = null;
        rotating = false;
        stepCount = 0;
    }

    private void computeTarget(Player player, EndCrystal target) {
        var bb = target.getBoundingBox().deflate(0.4);
        var originalBb = target.getBoundingBox();

        double eyeX = player.getX();
        double eyeY = player.getEyeY();
        double eyeZ = player.getZ();

        double targetX = (eyeX < bb.minX) ? bb.minX : (eyeX > bb.maxX) ? bb.maxX : (bb.minX + bb.maxX) / 2.0;
        double targetZ = (eyeZ < bb.minZ) ? bb.minZ : (eyeZ > bb.maxZ) ? bb.maxZ : (bb.minZ + bb.maxZ) / 2.0;
        double targetY = originalBb.minY + 0.05; // bottom of real hitbox + tiny inset

        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
        targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
    }

    private float wrapDegrees(float degrees) {
        degrees %= 360f;
        if (degrees >= 180f) degrees -= 360f;
        if (degrees < -180f) degrees += 360f;
        return degrees;
    }

    private boolean isLookingAtCrystal(Player player, EndCrystal crystal) {
        var bb = crystal.getBoundingBox().deflate(0.4);

        double yaw = Math.toRadians(player.getYRot() + 90);
        double pitch = Math.toRadians(-player.getXRot());

        // ray direction
        double dirX = Math.cos(pitch) * Math.cos(yaw);
        double dirY = Math.sin(pitch);
        double dirZ = Math.cos(pitch) * Math.sin(yaw);

        double eyeX = player.getX();
        double eyeY = player.getEyeY();
        double eyeZ = player.getZ();

        // ray-aabb intersection (slab method)
        double tMin = 0, tMax = 6.0; // max reach

        double[] origins = {eyeX, eyeY, eyeZ};
        double[] dirs = {dirX, dirY, dirZ};
        double[] mins = {bb.minX, bb.minY, bb.minZ};
        double[] maxs = {bb.maxX, bb.maxY, bb.maxZ};

        for (int i = 0; i < 3; i++) {
            if (Math.abs(dirs[i]) < 1e-8) {
                if (origins[i] < mins[i] || origins[i] > maxs[i]) return false;
            } else {
                double t1 = (mins[i] - origins[i]) / dirs[i];
                double t2 = (maxs[i] - origins[i]) / dirs[i];
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return false;
            }
        }
        return true;
    }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled) clearPending();
    }
}
