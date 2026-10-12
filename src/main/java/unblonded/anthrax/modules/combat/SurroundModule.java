package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Crystal PvP surround: boxes your feet in with obsidian on all four sides (and below if needed) so
 * crystals cannot be placed next to you. Re-places broken blocks; turns off when you move away.
 */
public final class SurroundModule extends FeatureModule {
    private final Setting.Num perTick = setting(Setting.Num.standalone("Blocks per tick", 2, 1, 8, 1), "How many blocks may be placed each tick.");
    private final Setting.Bool center = setting(Setting.Bool.standalone("Center on enable", true), "Walk to the middle of your block first so all four sides can be placed.");
    private final Setting.Bool floor = setting(Setting.Bool.standalone("Place floor if missing", true), "Also place blocks underneath, so the side blocks have support.");
    private final Setting.Bool ender = setting(Setting.Bool.standalone("Use ender chests too", true));
    private final Setting.Bool disableOnMove = setting(Setting.Bool.standalone("Disable when you move", true), "Turn off once you leave the block you started on.");
    private BlockPos anchor;
    /** Walking onto the block centre (a teleport there would be rubber-banded by the server). */
    private boolean centering;

    public SurroundModule() {
        super("Surround", "Box your feet in with obsidian so crystals cannot be placed next to you", "Combat", false);
    }

    @Override protected void onToggle(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        anchor = null;
        if (!enabled || mc.player == null) return;
        anchor = mc.player.blockPosition();
        centering = center.get();
    }

    /** Nudges our own motion toward the block centre at walking speed; true while still on the way. */
    private boolean walkToCenter(LocalPlayer self) {
        double dx = anchor.getX() + .5 - self.getX(), dz = anchor.getZ() + .5 - self.getZ();
        if (Math.abs(dx) < .05 && Math.abs(dz) < .05 || !self.onGround()) return false;
        double max = .12;   // under vanilla walking speed, so the server's movement check never objects
        self.setDeltaMovement(Math.max(-max, Math.min(max, dx * .6)), self.getDeltaMovement().y, Math.max(-max, Math.min(max, dz * .6)));
        return true;
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || !ServerSafety.ready(mc)) return;
        if (anchor == null) anchor = self.blockPosition();
        if (centering && !walkToCenter(self)) centering = false;
        if (!self.blockPosition().equals(anchor)) {
            if (disableOnMove.get()) { setEnabled(false); return; }
            anchor = self.blockPosition();
        }
        int slot = CombatUtil.findHotbar(self, s -> s.is(Items.OBSIDIAN) || s.is(Items.CRYING_OBSIDIAN));
        if (slot < 0 && ender.get()) slot = CombatUtil.findHotbar(self, s -> s.is(Items.ENDER_CHEST));
        if (slot < 0) { Notifications.error("Surround", "No obsidian in your hotbar"); setEnabled(false); return; }

        int budget = (int) perTick.get(), original = self.getInventory().getSelectedSlot();
        boolean switched = false;
        for (BlockPos pos : targets()) {
            if (budget <= 0) break;
            if (!mc.level.getBlockState(pos).canBeReplaced() || blocked(mc, pos) || !ServerSafety.canReach(self, pos)) continue;
            BlockHitResult hit = support(mc, pos);
            if (hit == null) continue;
            if (!switched) { self.getInventory().setSelectedSlot(slot); switched = true; }
            mc.gameMode.useItemOn(self, InteractionHand.MAIN_HAND, hit);
            self.swing(InteractionHand.MAIN_HAND);
            budget--;
        }
        if (switched) self.getInventory().setSelectedSlot(original);
    }

    private BlockPos[] targets() {
        BlockPos below = anchor.below();
        BlockPos[] sides = {anchor.north(), anchor.east(), anchor.south(), anchor.west()};
        if (!floor.get()) return sides;
        // floor blocks under each side first, so the sides have something to sit on
        return new BlockPos[]{below, sides[0].below(), sides[1].below(), sides[2].below(), sides[3].below(),
                sides[0], sides[1], sides[2], sides[3]};
    }

    /** An entity (other than items and orbs) in the way blocks placement. */
    private static boolean blocked(Minecraft mc, BlockPos pos) {
        for (var e : mc.level.getEntities(null, new AABB(pos)))
            if (!(e instanceof ItemEntity) && !(e instanceof ExperienceOrb) && e != mc.player) return true;
        return pos.equals(mc.player.blockPosition()) || new AABB(pos).intersects(mc.player.getBoundingBox());
    }

    /** A solid neighbour to click against, with the face pointing at {@code pos}. */
    private static BlockHitResult support(Minecraft mc, BlockPos pos) {
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};
        for (Direction d : order) {
            BlockPos n = pos.relative(d);
            if (mc.level.getBlockState(n).canBeReplaced()) continue;
            Direction face = d.getOpposite();
            Vec3 hit = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            return new BlockHitResult(hit, face, n, false);
        }
        return null;
    }

    @Override public String hudInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (anchor == null || mc.level == null) return null;
        int safe = 0;
        for (BlockPos p : new BlockPos[]{anchor.north(), anchor.east(), anchor.south(), anchor.west()})
            if (!mc.level.getBlockState(p).canBeReplaced()) safe++;
        return safe + "/4";
    }
}
