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

/** Predicts where an enemy's ender pearl will land and teleports there first. */
public final class PearlTrapModule extends FeatureModule {
    public static final PearlTrapModule INSTANCE = new PearlTrapModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Max distance", 30, 5, 45, 1), "Only go for landing spots this close to you.");
    private final Setting.Num step = setting(Setting.Num.standalone("Step size", 8, 2, 9.5, 0.5), "Blocks per movement packet. Vanilla allows up to 10.");
    private final Setting.Bool hit = setting(Setting.Bool.standalone("Attack on arrival", true), "Hits the pearler when they land next to you.");
    private final java.util.Set<Integer> handled = new java.util.HashSet<>();
    private Player waitingFor;
    private int waitTicks;

    private PearlTrapModule() { super("Pearl Trap", "Predicts where an enemy's ender pearl will land and teleports there first. Servers with stricter movement checks may pull you back", "HVH", false); }

    @Override public void tick(Minecraft mc) {
        if (!ServerSafety.ready(mc)) return;
        LocalPlayer self = mc.player;
        if (waitingFor != null) {
            if (--waitTicks <= 0 || !waitingFor.isAlive()) waitingFor = null;
            else if (hit.get() && ServerSafety.canReach(self, waitingFor) && CombatUtil.cooldown(self) > 0.9f) { CombatUtil.attack(mc, waitingFor); waitingFor = null; }
        }
        java.util.Set<Integer> live = new java.util.HashSet<>();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl pearl)) continue;
            live.add(pearl.getId());
            if (handled.contains(pearl.getId())) continue;
            handled.add(pearl.getId());
            if (!(pearl.getOwner() instanceof Player owner) || owner == self || unblonded.anthrax.modules.client.FriendsModule.isFriend(owner)) continue;
            Vec3 land = Hvh.pearlLanding(mc, pearl.position(), pearl.getDeltaMovement());
            if (land == null || land.distanceTo(self.position()) > range.get()) continue;
            Vec3 spot = standSpot(mc, land);
            if (spot == null || !Hvh.clearPath(mc, self.position(), spot)) { Notifications.info("Pearl Trap", owner.getName().getString() + " pearled, no clear path"); continue; }
            Hvh.stepTo(mc, self.position(), spot, step.get());
            self.setPos(spot);
            self.setDeltaMovement(Vec3.ZERO);
            waitingFor = owner;
            waitTicks = 40;
            Notifications.success("Pearl Trap", "Waiting where " + owner.getName().getString() + " lands");
        }
        handled.retainAll(live);
    }

    /** A free spot beside the landing point, so we do not stand exactly where they arrive. */
    private static Vec3 standSpot(Minecraft mc, Vec3 land) {
        BlockPos base = BlockPos.containing(land.x, land.y + 0.1, land.z);
        for (Direction d : Hvh.SIDES) {
            BlockPos p = base.relative(d);
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos feet = p.above(dy);
                if (mc.level.getBlockState(feet).getCollisionShape(mc.level, feet).isEmpty()
                        && mc.level.getBlockState(feet.above()).getCollisionShape(mc.level, feet.above()).isEmpty()
                        && !mc.level.getBlockState(feet.below()).getCollisionShape(mc.level, feet.below()).isEmpty())
                    return Vec3.atBottomCenterOf(feet);
            }
        }
        return null;
    }
}
