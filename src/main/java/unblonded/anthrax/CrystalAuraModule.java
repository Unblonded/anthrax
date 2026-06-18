package unblonded.anthrax;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;

public class CrystalAuraModule {
    public static final CrystalAuraModule INSTANCE = new CrystalAuraModule();

    private volatile boolean waitingForCrystal = false;
    private volatile int tickDelay = 0;
    private volatile EndCrystal pendingCrystal = null;

    private static final int STEP_MS = 2;       // runs every 1ms (~500hz)
    private static final int MAX_STEPS = 20;     // max steps before force finish
    private static final float SMOOTHING = 0.18f; // exponential decay factor

    private volatile float targetYaw;
    private volatile float targetPitch;
    private volatile boolean rotating = false;
    private int stepCount = 0;

    public CrystalAuraModule() {
        Thread rotationThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(STEP_MS);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || !rotating) continue;

                    float currentYaw = mc.player.getYRot();
                    float currentPitch = mc.player.getXRot();

                    float yawDiff = wrapDegrees(targetYaw - currentYaw);
                    float pitchDiff = targetPitch - currentPitch;

                    // tiny natural jitter
                    float jitterYaw = (float)(Math.random() - 0.5) * 0.15f;
                    float jitterPitch = (float)(Math.random() - 0.5) * 0.08f;

                    float newYaw = currentYaw + yawDiff * SMOOTHING + jitterYaw;
                    float newPitch = currentPitch + pitchDiff * SMOOTHING + jitterPitch;

                    mc.player.yRotO = currentYaw;
                    mc.player.xRotO = currentPitch;
                    mc.player.setYRot(newYaw);
                    mc.player.setXRot(newPitch);
                    mc.player.yHeadRot = newYaw;
                    mc.player.yBodyRot = newYaw;

                    stepCount++;

                    if (pendingCrystal != null && isLookingAtCrystal(mc.player, pendingCrystal)) {
                        mc.player.setYRot(newYaw);
                        mc.player.setXRot(newPitch);
                        mc.player.yHeadRot = newYaw;
                        mc.player.yBodyRot = newYaw;
                        rotating = false;
                        stepCount = 0;
                        mc.execute(() -> {
                            mc.gameMode.attack(mc.player, pendingCrystal);
                            mc.player.swing(InteractionHand.MAIN_HAND);
                            pendingCrystal = null;
                        });
                        continue;
                    }

                    boolean yawClose = Math.abs(wrapDegrees(targetYaw - newYaw)) < 0.5f;
                    boolean pitchClose = Math.abs(targetPitch - newPitch) < 0.5f;

                    if (yawClose && pitchClose || stepCount >= MAX_STEPS) {
                        mc.player.setYRot(targetYaw);
                        mc.player.setXRot(targetPitch);
                        mc.player.yHeadRot = targetYaw;
                        mc.player.yBodyRot = targetYaw;
                        rotating = false;
                        stepCount = 0;

                        if (pendingCrystal != null) {
                            // attack must run on main thread
                            mc.execute(() -> {
                                mc.gameMode.attack(mc.player, pendingCrystal);
                                mc.player.swing(InteractionHand.MAIN_HAND);
                                pendingCrystal = null;
                            });
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        rotationThread.setName("crystal-aura-rotation");
        rotationThread.setDaemon(true); // dies with the game
        rotationThread.start();
    }

    public void onCrystalPlace() {
        waitingForCrystal = true;
        tickDelay = 2;
    }

    public void onTick(Minecraft client) {
        if (client.player == null || rotating) return;

        if (waitingForCrystal) {
            if (tickDelay > 0) {
                tickDelay--;
                return;
            }

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
    private volatile float smoothing = 0.25f;
    private volatile int stepMs = 1;

    public boolean isEnabled() { return enabled; }
    public void toggle() { enabled = !enabled; }
    public float getSmoothing() { return smoothing; }
    public void setSmoothing(float v) { smoothing = v; }
    public int getStepMs() { return stepMs; }
    public void setStepMs(int v) { stepMs = Math.max(1, v); }
    public int getTickDelay() { return tickDelay; }
    public void setTickDelay(int v) { tickDelay = v; }
}