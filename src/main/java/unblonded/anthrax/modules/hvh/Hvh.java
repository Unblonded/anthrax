package unblonded.anthrax.modules.hvh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.client.FriendsModule;
import unblonded.anthrax.modules.combat.CombatUtil;
import unblonded.anthrax.modules.combat.ServerSafety;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Shared HVH helpers: enemy selection, hole checks, reach-checked block placement, and stepped teleports. */
final class Hvh {
    private Hvh() {}

    static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** Nearest enemy player (not a friend, not creative or spectator) within {@code range}, or null. */
    static Player nearestEnemy(Minecraft mc, double range) {
        LocalPlayer self = mc.player;
        Player best = null;
        double bestD = range * range;
        for (Player p : mc.level.players()) {
            if (p == self || !p.isAlive() || p.isSpectator() || p.isCreative() || FriendsModule.isFriend(p)) continue;
            double d = self.distanceToSqr(p);
            if (d <= bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /** Every enemy player within {@code range}. */
    static List<Player> enemies(Minecraft mc, double range) {
        List<Player> out = new ArrayList<>();
        for (Player p : mc.level.players())
            if (p != mc.player && p.isAlive() && !p.isSpectator() && !p.isCreative() && !FriendsModule.isFriend(p)
                    && mc.player.distanceToSqr(p) <= range * range) out.add(p);
        return out;
    }

    /** Blocks a crystal explosion cannot break (obsidian, bedrock and friends). */
    static boolean blastProof(BlockState s) {
        return s.getBlock().getExplosionResistance() >= 600 || s.is(Blocks.BEDROCK);
    }

    /** A 1x1 hole: open at feet and head, blast-proof floor and four blast-proof sides. */
    static boolean safeHole(Minecraft mc, BlockPos feet) {
        var level = mc.level;
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return false;
        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        if (!blastProof(level.getBlockState(feet.below()))) return false;
        for (Direction d : SIDES) if (!blastProof(level.getBlockState(feet.relative(d)))) return false;
        return true;
    }

    /** Hotbar slot matching {@code test}, or -1. */
    static int slot(LocalPlayer p, Predicate<ItemStack> test) { return CombatUtil.findHotbar(p, test); }

    /** An entity (other than items and orbs) occupying the block would stop a placement. */
    static boolean occupied(Minecraft mc, BlockPos pos, boolean allowPlayers) {
        for (var e : mc.level.getEntities(null, new AABB(pos))) {
            if (e instanceof ItemEntity || e instanceof ExperienceOrb) continue;
            if (allowPlayers && e instanceof Player && e != mc.player) continue;
            return true;
        }
        return false;
    }

    /** A solid neighbour to click, with the face pointing at {@code pos}; null if there is none. */
    static BlockHitResult support(Minecraft mc, BlockPos pos) {
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};
        for (Direction d : order) {
            BlockPos n = pos.relative(d);
            if (mc.level.getBlockState(n).canBeReplaced()) continue;
            return ServerSafety.click(n, d.getOpposite());
        }
        return null;
    }

    /**
     * Places the item in {@code slot} at {@code pos} if it is free, supported and in vanilla reach. Switches
     * slot for the click and back. Returns true when a placement was sent.
     */
    static boolean place(Minecraft mc, BlockPos pos, int slot, boolean intoPlayers) {
        LocalPlayer self = mc.player;
        if (slot < 0 || !mc.level.getBlockState(pos).canBeReplaced() || !ServerSafety.canReach(self, pos)) return false;
        // blocks without collision (webs) can go into anyone's space; solid ones cannot overlap an entity
        boolean solid = !(self.getInventory().getItem(slot).getItem() instanceof net.minecraft.world.item.BlockItem bi)
                || !bi.getBlock().defaultBlockState().getCollisionShape(mc.level, pos).isEmpty();
        if (solid && (new AABB(pos).intersects(self.getBoundingBox()) || occupied(mc, pos, intoPlayers))) return false;
        BlockHitResult hit = support(mc, pos);
        if (hit == null) return false;
        int prev = self.getInventory().getSelectedSlot();
        self.getInventory().setSelectedSlot(slot);
        boolean ok = mc.gameMode.useItemOn(self, InteractionHand.MAIN_HAND, hit).consumesAction();
        if (ok) self.swing(InteractionHand.MAIN_HAND);
        self.getInventory().setSelectedSlot(prev);
        return ok;
    }

    /** True when a player-sized box can travel the straight line from {@code from} to {@code to} without hitting blocks. */
    static boolean clearPath(Minecraft mc, Vec3 from, Vec3 to) {
        LocalPlayer self = mc.player;
        AABB box = self.getBoundingBox().move(from.subtract(self.position())).deflate(0.05);
        Vec3 delta = to.subtract(from);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.5));
        for (int i = 1; i <= steps; i++)
            if (!mc.level.noCollision(self, box.move(delta.scale(i / (double) steps)))) return false;
        return true;
    }

    /**
     * Sends position packets from {@code from} to {@code to} in steps no longer than {@code step} blocks (the vanilla
     * per-packet allowance is 10). Does not move the client; callers decide whether to follow.
     */
    static void stepTo(Minecraft mc, Vec3 from, Vec3 to, double step) {
        Vec3 delta = to.subtract(from);
        int n = Math.max(1, (int) Math.ceil(delta.length() / step));
        for (int i = 1; i <= n; i++) {
            Vec3 at = from.add(delta.scale(i / (double) n));
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(at, false, false));
        }
    }

    /** Where an ender pearl lands: steps vanilla pearl physics (gravity 0.03, drag 0.99) until it hits a block. */
    static Vec3 pearlLanding(Minecraft mc, Vec3 pos, Vec3 vel) {
        for (int i = 0; i < 200; i++) {
            Vec3 next = pos.add(vel);
            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(pos, next, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return hit.getLocation();
            pos = next;
            vel = vel.scale(0.99).add(0, -0.03, 0);
            if (pos.y < mc.level.getMinY() - 16) return null;
        }
        return null;
    }
}
