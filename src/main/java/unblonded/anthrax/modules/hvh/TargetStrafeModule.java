package unblonded.anthrax.modules.hvh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.combat.CombatUtil;
import unblonded.anthrax.modules.combat.ServerSafety;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.List;

/** Circles or hops around the Kill Aura / TP Aura target, favouring angles outside their view. */
public final class TargetStrafeModule extends FeatureModule {
    public static final TargetStrafeModule INSTANCE = new TargetStrafeModule();
    private final Setting.Num radius = setting(Setting.Num.standalone("Radius", 2.5, 1, 5, 0.25), "Distance to keep from the target.");
    private final Setting.Num engage = setting(Setting.Num.standalone("Engage range", 6, 0, 16, 0.5),
            "While Kill Aura or TP Aura has no target yet, move in on the nearest enemy within this range so the aura can reach them. 0 waits for the aura.");
    private final Setting.Bool hop = setting(Setting.Bool.standalone("Hop", true), "Jump while circling (harder to hit, works with crits).");
    private final Setting.Bool behind = setting(Setting.Bool.standalone("Prefer behind them", true), "Circles toward the side they are not looking at.");
    private final Setting.Bool needSpace = setting(Setting.Bool.standalone("Only while holding Space", false), "Strafe only while the jump key is held.");
    private final Setting.Bool voidCheck = setting(Setting.Bool.standalone("Avoid edges", true), "Turns around before walking off a drop.");
    private int direction = 1;

    private TargetStrafeModule() { super("Target Strafe", "Circles or hops around the Kill Aura or TP Aura target, favouring angles outside their view. Works with Speed", "HVH", false); }

    /** True while it is steering, so Speed leaves the direction to us. */
    public boolean steering() { return isEnabled() && target() != null; }

    private net.minecraft.world.entity.LivingEntity target() {
        boolean ka = unblonded.anthrax.modules.combat.KillAuraModule.INSTANCE.isEnabled(), tp = TpAuraModule.INSTANCE.isEnabled();
        if (!ka && !tp) return null;
        var t = ka ? unblonded.anthrax.modules.combat.KillAuraModule.INSTANCE.target() : TpAuraModule.INSTANCE.target();
        if (t != null) return t;
        // the aura only targets inside its own (short) range; close in on the nearest enemy until it does
        Minecraft mc = Minecraft.getInstance();
        return engage.get() > 0 && mc.player != null && mc.level != null ? Hvh.nearestEnemy(mc, engage.get()) : null;
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null || mc.gui.screen() != null) return;
        var t = target();
        if (t == null || (needSpace.get() && !mc.options.keyJump.isDown()) || self.isInWater() || self.isFallFlying()) return;
        Vec3 toMe = self.position().subtract(t.position()).multiply(1, 0, 1);
        if (toMe.lengthSqr() < 1e-4) toMe = new Vec3(1, 0, 0);
        double angle = Math.atan2(toMe.z, toMe.x);
        if (behind.get()) {
            // their look direction; circle the way that takes us out of view
            double look = Math.toRadians(t.getYRot() + 90);
            double rel = Math.atan2(Math.sin(angle - look), Math.cos(angle - look));
            if (Math.abs(rel) < Math.PI * 0.9) direction = rel > 0 ? 1 : -1;
        }
        if (self.horizontalCollision) direction = -direction;
        double speed = 0.2873 * (unblonded.anthrax.modules.movement.SpeedModule.INSTANCE.isEnabled() ? unblonded.anthrax.modules.movement.SpeedModule.INSTANCE.multiplier() : 1);
        double step = speed / Math.max(0.5, radius.get());
        double next = angle + direction * step;
        Vec3 goal = t.position().add(Math.cos(next) * radius.get(), 0, Math.sin(next) * radius.get());
        Vec3 dir = goal.subtract(self.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-6) return;
        dir = dir.normalize().scale(speed);
        if (voidCheck.get() && mc.level.noCollision(self, self.getBoundingBox().move(dir.x * 3, -2.5, dir.z * 3))) {
            direction = -direction;
            return;
        }
        if (hop.get() && self.onGround()) self.jumpFromGround();
        self.setDeltaMovement(dir.x, self.getDeltaMovement().y, dir.z);
    }
}
