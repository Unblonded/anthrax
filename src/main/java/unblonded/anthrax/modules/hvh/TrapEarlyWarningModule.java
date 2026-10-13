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

/** Warns while blocks are placed around you, marks the remaining exit, and can run an escape. */
public final class TrapEarlyWarningModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final TrapEarlyWarningModule INSTANCE = new TrapEarlyWarningModule();
    private final Setting.Num threshold = setting(Setting.Num.standalone("Warn at blocks", 2, 1, 8, 1), "Warn once this many blocks have appeared around you in a short time.");
    private final Setting.Mode escape = setting(Setting.Mode.standalone("Escape", "None", "None", "Walk out", "Pearl out", "Mine out"),
            "What to do when you are nearly trapped. Walk out heads for the open exit, Pearl out throws a pearl through it, Mine out breaks a cage block.");
    private final Setting.Bool showExits = setting(Setting.Bool.standalone("Mark exits", true), "Outlines open exits in green.");
    private final Setting.Bool sound = setting(Setting.Bool.standalone("Sound", true), "Plays a warning sound.");
    private final java.util.Map<BlockPos, Boolean> last = new java.util.HashMap<>();
    private final java.util.ArrayDeque<Long> events = new java.util.ArrayDeque<>();
    private final List<BlockPos> exits = new ArrayList<>();
    private long lastWarn, ownActionAt;
    private BlockPos lastFeet;

    private TrapEarlyWarningModule() { super("Trap Early Warning", "Warns while blocks are being placed around you, marks the remaining exit, and can run an escape action if you enable one", "HVH", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) return;
        if (mc.options.keyUse.isDown()) ownActionAt = System.currentTimeMillis();   // our own placements do not count
        BlockPos feet = self.blockPosition();
        if (!feet.equals(lastFeet)) { last.clear(); lastFeet = feet; }
        long now = System.currentTimeMillis();
        for (BlockPos pos : watched(feet)) {
            boolean solid = !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
            Boolean was = last.put(pos.immutable(), solid);
            if (was != null && !was && solid && now - ownActionAt > 400) events.add(now);
        }
        while (!events.isEmpty() && now - events.peekFirst() > 3000) events.pollFirst();
        exits.clear();
        for (Direction d : Hvh.SIDES) {
            BlockPos f = feet.relative(d), h = f.above();
            if (mc.level.getBlockState(f).getCollisionShape(mc.level, f).isEmpty() && mc.level.getBlockState(h).getCollisionShape(mc.level, h).isEmpty()) exits.add(f);
        }
        BlockPos up = feet.above(2);
        if (mc.level.getBlockState(up).getCollisionShape(mc.level, up).isEmpty()) exits.add(up);
        if (events.size() < threshold.get() || now - lastWarn < 2500) return;
        lastWarn = now;
        Notifications.warn("Trap warning", events.size() + " blocks placed around you, " + exits.size() + (exits.size() == 1 ? " exit" : " exits") + " left");
        if (sound.get()) self.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 0.6f);
        runEscape(mc, self, feet);
    }

    private void runEscape(Minecraft mc, LocalPlayer self, BlockPos feet) {
        switch (escape.get()) {
            case "Walk out" -> {
                BlockPos exit = exits.stream().filter(e -> e.getY() == feet.getY()).findFirst().orElse(null);
                if (exit == null) return;
                Vec3 dir = Vec3.atBottomCenterOf(exit).subtract(self.position()).multiply(1, 0, 1).normalize().scale(0.25);
                self.setDeltaMovement(dir.x, self.getDeltaMovement().y, dir.z);
            }
            case "Pearl out" -> {
                if (exits.isEmpty()) return;
                int slot = Hvh.slot(self, s -> s.is(Items.ENDER_PEARL));
                if (slot < 0) return;
                Vec3 aim = Vec3.atCenterOf(exits.getFirst()).add(Vec3.atCenterOf(exits.getFirst()).subtract(self.getEyePosition()).scale(6));
                float[] rot = CombatUtil.rotationTo(self, aim, 1f);
                float py = self.getYRot(), pp = self.getXRot();
                int prev = self.getInventory().getSelectedSlot();
                self.getInventory().setSelectedSlot(slot);
                self.setYRot(rot[0]); self.setXRot(rot[1]);
                mc.gameMode.useItem(self, InteractionHand.MAIN_HAND);
                self.setYRot(py); self.setXRot(pp);
                self.getInventory().setSelectedSlot(prev);
            }
            case "Mine out" -> {
                for (Direction d : Hvh.SIDES) {
                    BlockPos f = feet.relative(d);
                    if (!mc.level.getBlockState(f).isAir() && mc.level.getBlockState(f).getDestroySpeed(mc.level, f) >= 0) {
                        mc.gameMode.startDestroyBlock(f, d.getOpposite());
                        self.swing(InteractionHand.MAIN_HAND);
                        return;
                    }
                }
            }
            default -> { }
        }
    }

    private static List<BlockPos> watched(BlockPos feet) {
        List<BlockPos> out = new ArrayList<>();
        for (Direction d : Hvh.SIDES) { out.add(feet.relative(d)); out.add(feet.above().relative(d)); }
        out.add(feet.above(2));
        return out;
    }

    @Override public boolean active() { return isEnabled() && showExits.get() && !events.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        for (BlockPos e : exits)
            net.minecraft.gizmos.Gizmos.cuboid(e, net.minecraft.gizmos.GizmoStyle.strokeAndFill(WorldOverlays.argb(0xE0, 0x55FF88), 2f, WorldOverlays.argb(0x30, 0x55FF88))).setAlwaysOnTop();
    }
}
