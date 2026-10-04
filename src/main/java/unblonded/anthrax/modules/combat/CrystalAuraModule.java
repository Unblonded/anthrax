package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;

public class CrystalAuraModule {
    public static final CrystalAuraModule INSTANCE = new CrystalAuraModule();

    private boolean waitingForCrystal = false;
    private int ticksUntilTarget = 0;
    private int placementDelay = 2;
    private EndCrystal pendingCrystal = null;

    private int stepMs = 2;
    private static final int MAX_STEPS = 20;     // max steps before force finish
    private float smoothing = 0.18f; // exponential decay factor

    private float targetYaw;
    private float targetPitch;
    private boolean rotating = false;
    private int stepCount = 0;

    private long lastRotationStep;

    public CrystalAuraModule() {}

    public void onCrystalPlace() {
        if (!enabled) return;
        waitingForCrystal = true;
        ticksUntilTarget = placementDelay;
    }

    public void onTick(Minecraft client) {
        if (!enabled) return;
        if (client.player == null || client.level == null || client.gameMode == null) {
            clearPending();
            return;
        }

        if (waitingForCrystal && !rotating) {
            if (ticksUntilTarget > 0) {
                ticksUntilTarget--;
            } else {
                client.player.level().getEntitiesOfClass(
                            EndCrystal.class,
                            client.player.getBoundingBox().inflate(8),
                            e -> true
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

        if (!rotating) return;
        EndCrystal crystal = pendingCrystal;
        if (crystal == null || crystal.isRemoved()) { clearPending(); return; }
        long now = System.currentTimeMillis();
        if (now - lastRotationStep < stepMs) return;
        lastRotationStep = now;

        Player player = client.player;
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();
        float yawDiff = wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float blend = Math.max(0.01f, Math.min(1f, smoothing));
        float newYaw = currentYaw + yawDiff * blend;
        float newPitch = Math.max(-90f, Math.min(90f, currentPitch + pitchDiff * blend));
        setRotation(player, newYaw, newPitch);
        stepCount++;

        boolean close = Math.abs(wrapDegrees(targetYaw - newYaw)) < 0.5f
                && Math.abs(targetPitch - newPitch) < 0.5f;
        if (isLookingAtCrystal(player, crystal) || close || stepCount >= MAX_STEPS) {
            setRotation(player, targetYaw, targetPitch);
            client.gameMode.attack(player, crystal);
            player.swing(InteractionHand.MAIN_HAND);
            clearPending();
        }
    }

    private static void setRotation(Player player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yHeadRot = yaw;
        player.yBodyRot = yaw;
    }

    private void clearPending() {
        waitingForCrystal = false;
        ticksUntilTarget = 0;
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

    private volatile boolean enabled = true;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) {
        enabled = v;
        if (!v) clearPending();
    }
    public float getSmoothing() { return smoothing; }
    public void setSmoothing(float v) { smoothing = v; }
    public int getStepMs() { return stepMs; }
    public void setStepMs(int v) { stepMs = Math.max(1, v); }
    public int getTickDelay() { return placementDelay; }
    public void setTickDelay(int v) { placementDelay = Math.max(0, v); }
}
