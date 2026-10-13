package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * While you draw a bow, hold a loaded crossbow, or charge a trident, aims at the nearest target in front of you,
 * leading moving targets and allowing for the drop over distance (solved by simulating the projectile).
 */
public final class BowAimbotModule extends FeatureModule {
    public static final BowAimbotModule INSTANCE = new BowAimbotModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 60, 10, 120, 5), "Furthest target to aim at.");
    private final Setting.Num fov = setting(Setting.Num.standalone("Field of view", 60, 10, 180, 5), "Only targets within this angle of where you look.");
    private final Setting.Num turn = setting(Setting.Num.standalone("Turn speed", 720, 90, 3600, 30), "Degrees per second the aim turns. High values snap.");
    private final Setting.Bool players = setting(Setting.Bool.standalone("Players", true), "");
    private final Setting.Bool hostiles = setting(Setting.Bool.standalone("Hostile mobs", true), "");
    private final Setting.Bool animals = setting(Setting.Bool.standalone("Animals", false), "");
    private final Setting.Bool lead = setting(Setting.Bool.standalone("Lead moving targets", true), "Aim where the target will be when the shot arrives.");
    private LivingEntity target;
    private float wantYaw, wantPitch;
    private boolean aiming;

    private BowAimbotModule() { super("Bow Aimbot", "Aims your bow, crossbow or trident at the nearest target, leading it and allowing for the drop over distance", "Combat", false); }

    /** Launch speed of the held weapon right now, or 0 if it is not ready to shoot. */
    private static double speed(LocalPlayer p) {
        ItemStack s = p.getUseItem().isEmpty() ? p.getMainHandItem() : p.getUseItem();
        if (s.getItem() instanceof BowItem && p.isUsingItem()) {
            float power = BowItem.getPowerForTime(p.getTicksUsingItem());
            return power < 0.1f ? 0 : power * 3.0;
        }
        if (s.getItem() instanceof TridentItem && p.isUsingItem()) return 2.5;
        if (s.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(s)) return 3.15;
        return 0;
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        aiming = false;
        if (p == null || mc.level == null || mc.gui.screen() != null) { target = null; return; }
        double v = speed(p);
        if (v <= 0) { target = null; return; }
        target = pick(mc, p);
        if (target == null) return;
        float[] rot = solve(mc, p, target, v);
        if (rot == null) return;
        wantYaw = rot[0]; wantPitch = rot[1];
        aiming = true;
        CombatUtil.markTarget(target);
    }

    @Override public void frame(Minecraft mc, float dt) {
        LocalPlayer p = mc.player;
        if (!aiming || p == null) return;
        float step = (float) (turn.get() * dt);
        float dy = net.minecraft.util.Mth.wrapDegrees(wantYaw - p.getYRot()), dp = wantPitch - p.getXRot();
        p.setYRot(p.getYRot() + net.minecraft.util.Mth.clamp(dy, -step, step));
        p.setXRot(net.minecraft.util.Mth.clamp(p.getXRot() + net.minecraft.util.Mth.clamp(dp, -step, step), -90, 90));
    }

    private LivingEntity pick(Minecraft mc, LocalPlayer p) {
        LivingEntity best = null;
        double bestAngle = fov.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!CombatUtil.attackable(p, e, players.get(), hostiles.get(), animals.get(), false)) continue;
            if (p.distanceTo(e) > range.get() || !p.hasLineOfSight(e)) continue;
            double a = CombatUtil.angleTo(p, e.getBoundingBox().getCenter());
            if (a < bestAngle) { bestAngle = a; best = (LivingEntity) e; }
        }
        return best;
    }

    /**
     * Yaw and pitch that land the projectile on the target, found by simulating the shot (gravity 0.05, drag 0.99
     * for arrows; 0.05/0.99 for tridents too) and bisecting on pitch. Leading uses the target's velocity times
     * the flight time, iterated twice.
     */
    private float[] solve(Minecraft mc, LocalPlayer p, LivingEntity t, double v) {
        Vec3 eye = p.getEyePosition();
        Vec3 aim = t.getBoundingBox().getCenter().add(0, t.getBbHeight() * 0.15, 0);
        Vec3 vel = t.getDeltaMovement();
        float pitch = 0;
        int flight = 0;
        for (int iter = 0; iter < 3; iter++) {
            Vec3 goal = lead.get() ? aim.add(vel.x * flight, 0, vel.z * flight) : aim;
            double dx = goal.x - eye.x, dz = goal.z - eye.z, horiz = Math.sqrt(dx * dx + dz * dz);
            double dy = goal.y - eye.y;
            // bisection on pitch (in degrees, negative = up) for the arc that passes through the goal
            double lo = -89, hi = 89;
            Double found = null;
            for (int k = 0; k < 40; k++) {
                double mid = (lo + hi) / 2;
                double[] r = simulate(v, Math.toRadians(-mid), horiz);
                if (r == null) { hi = mid; continue; }   // never reached that far: aim higher (more negative)
                if (r[0] > dy) lo = mid; else hi = mid;  // passed above the goal: aim lower
                found = mid;
                flight = (int) r[1];
            }
            if (found == null) return null;
            pitch = (float) (double) found;
        }
        Vec3 goal = lead.get() ? aim.add(vel.x * flight, 0, vel.z * flight) : aim;
        float yaw = (float) Math.toDegrees(Math.atan2(goal.z - eye.z, goal.x - eye.x)) - 90f;
        return new float[]{yaw, pitch};
    }

    /** Height reached (relative to launch) when the projectile has covered {@code dist} horizontally, and the ticks it took. */
    private static double[] simulate(double v, double elevation, double dist) {
        double vx = Math.cos(elevation) * v, vy = Math.sin(elevation) * v, x = 0, y = 0;
        for (int t = 1; t < 300; t++) {
            x += vx; y += vy;
            vx *= 0.99; vy = vy * 0.99 - 0.05;
            if (x >= dist) return new double[]{y, t};
            if (y < -128) return null;
        }
        return null;
    }

    @Override public String hudInfo() { return target == null ? null : target.getName().getString(); }
}
