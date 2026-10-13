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

/** Teleports next to targets, attacks, confirms the hit, and returns. Replaces Kill Aura. */
public final class TpAuraModule extends FeatureModule {
    public static final TpAuraModule INSTANCE = new TpAuraModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 20, 4, 45, 1), "How far away a target can be.");
    private final Setting.Num step = setting(Setting.Num.standalone("Step size", 8, 2, 9.5, 0.5), "Blocks per movement packet. Vanilla allows up to 10.");
    private final Setting.Num cooldown = setting(Setting.Num.standalone("Cooldown %", 92, 10, 100, 1), "Only attack once your attack has recharged this much.");
    private final Setting.Bool back = setting(Setting.Bool.standalone("Return after hit", true), "Teleports back to where you were after each hit, so you stay out of reach.");
    private final Setting.Bool players = setting(Setting.Bool.standalone("Players", true), "");
    private final Setting.Bool hostiles = setting(Setting.Bool.standalone("Hostile mobs", false), "");
    private final Setting.Bool confirm = setting(Setting.Bool.standalone("Wait for hit confirm", true), "Waits until the target shows the hurt flash before going again.");
    private net.minecraft.world.entity.LivingEntity target;
    private int confirmWait;

    private TpAuraModule() { super("TP Aura", "Teleports next to targets, attacks, confirms the hit, and repeats. Replaces Kill Aura. Servers with stricter movement checks may pull you back", "HVH", false); }

    public net.minecraft.world.entity.LivingEntity target() { return isEnabled() ? target : null; }

    @Override protected void onToggle(boolean enabled) {
        target = null;
        if (enabled) unblonded.anthrax.modules.combat.KillAuraModule.INSTANCE.setEnabled(false);
    }

    @Override public void tick(Minecraft mc) {
        if (!ServerSafety.ready(mc)) return;
        LocalPlayer self = mc.player;
        if (confirmWait > 0) {
            confirmWait--;
            if (target != null && target.hurtTime > 0) confirmWait = 0;
            else return;
        }
        if (self.getAttackStrengthScale(0.5f) < cooldown.get() / 100f) return;
        target = pick(mc);
        if (target == null) return;
        Vec3 from = self.position();
        List<Vec3> route = route(mc, from, target);
        if (route == null) return;
        Vec3 spot = route.getLast();
        Vec3 at = from;
        for (Vec3 p : route) { Hvh.stepTo(mc, at, p, step.get()); at = p; }
        CombatUtil.attack(mc, target);
        if (route.size() > 1 || spot.distanceTo(from) > 0.5) {
            if (back.get()) {
                // retrace the same waypoints home
                for (int i = route.size() - 2; i >= -1; i--) { Vec3 p = i < 0 ? from : route.get(i); Hvh.stepTo(mc, at, p, step.get()); at = p; }
            } else self.setPos(spot);
        }
        if (confirm.get()) confirmWait = 10;
    }

    /**
     * Waypoints from {@code from} to a free spot beside the target: a straight line if it is clear, otherwise up
     * over the obstacle (up to 4 blocks), across, and back down. Tries spots all around the target, nearest first.
     * Null if none can be reached.
     */
    private List<Vec3> route(Minecraft mc, Vec3 from, net.minecraft.world.entity.LivingEntity t) {
        if (CombatUtil.reachSq(mc.player, t) <= 9) return List.of(from);
        List<Vec3> spots = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            for (double dy : new double[]{0, 1})
                spots.add(t.position().add(Math.cos(a) * 2.0, dy, Math.sin(a) * 2.0));
        }
        spots.sort(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(from)));
        for (Vec3 spot : spots) {
            var box = mc.player.getBoundingBox().move(spot.subtract(mc.player.position()));
            if (!mc.level.noCollision(mc.player, box)) continue;
            if (Hvh.clearPath(mc, from, spot)) return List.of(spot);
            for (int h = 1; h <= 4; h++) {
                Vec3 up = from.add(0, h, 0), over = new Vec3(spot.x, Math.max(from.y, spot.y) + h, spot.z);
                if (Hvh.clearPath(mc, from, up) && Hvh.clearPath(mc, up, over) && Hvh.clearPath(mc, over, spot)) return List.of(up, over, spot);
            }
        }
        return null;
    }

    private net.minecraft.world.entity.LivingEntity pick(Minecraft mc) {
        net.minecraft.world.entity.LivingEntity best = null;
        double bestD = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (!CombatUtil.attackable(mc.player, e, players.get(), hostiles.get(), false, false)) continue;
            double d = mc.player.distanceToSqr(e);
            if (d < bestD) { bestD = d; best = (net.minecraft.world.entity.LivingEntity) e; }
        }
        return best;
    }

    @Override public String hudInfo() { return target == null ? null : target.getName().getString(); }
}
