package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Scales the knockback you take from hits and explosions (0% = none). */
public final class VelocityModule extends FeatureModule {
    public static final VelocityModule INSTANCE = new VelocityModule();

    private final Setting.Num horizontal = setting(Setting.Num.standalone("Horizontal %", 0, 0, 100, 5), "How much sideways knockback you still take (0 = none).");
    private final Setting.Num vertical = setting(Setting.Num.standalone("Vertical %", 0, 0, 100, 5), "How much upward knockback you still take (0 = none).");
    private final Setting.Bool explosions = setting(Setting.Bool.standalone("Explosions", true));
    private final Setting.Bool inWater = setting(Setting.Bool.standalone("While in water", true));

    private VelocityModule() {
        super("Velocity", "Reduce or cancel the knockback you take from hits and explosions", "Combat", false);
    }

    private boolean applies() {
        var p = Minecraft.getInstance().player;
        return isEnabled() && p != null && (inWater.get() || !p.isInWater());
    }

    /** Knockback the server sent for a hit; returns the motion to apply. */
    public Vec3 scaleHit(Vec3 motion) {
        if (!applies()) return motion;
        var p = Minecraft.getInstance().player;
        Vec3 current = p.getDeltaMovement();
        double h = horizontal.get() / 100.0, v = vertical.get() / 100.0;
        // keep our own movement for the parts we cancel, so walking is not interrupted
        return new Vec3(current.x + (motion.x - current.x) * h, current.y + (motion.y - current.y) * v, current.z + (motion.z - current.z) * h);
    }

    /** The part of an explosion push to take back off after vanilla applied it. */
    public Vec3 explosionRefund(Vec3 push) {
        if (!applies() || !explosions.get()) return Vec3.ZERO;
        double h = 1 - horizontal.get() / 100.0, v = 1 - vertical.get() / 100.0;
        return new Vec3(push.x * h, push.y * v, push.z * h);
    }

    @Override public String hudInfo() { return Math.round(horizontal.get()) + "% " + Math.round(vertical.get()) + "%"; }
}
