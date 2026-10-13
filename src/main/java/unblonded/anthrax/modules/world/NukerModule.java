package unblonded.anthrax.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Breaks blocks around you: instantly in creative, one at a time in survival. */
public final class NukerModule extends FeatureModule {
    public static final NukerModule INSTANCE = new NukerModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 4, 1, 5, 0.5), "Radius around your eyes, in blocks.");
    private final Setting.Bool keepFloor = setting(Setting.Bool.standalone("Keep floor", true), "Never breaks blocks below your feet.");
    private final Setting.Mode filter = setting(Setting.Mode.standalone("Blocks", "All", "All", "Same as looked at"), "Break everything, or only blocks matching the one under your crosshair when you turned it on.");
    private BlockPos target;
    private net.minecraft.world.level.block.Block only;

    private NukerModule() { super("Nuker", "Breaks blocks around you. Instant in creative; mines one at a time in survival", "World", false); }

    @Override protected void onToggle(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        target = null;
        only = enabled && mc.level != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? mc.level.getBlockState(hit.getBlockPos()).getBlock() : null;
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        Vec3 eye = p.getEyePosition();
        if (target != null && valid(mc, p, target, eye)) {
            mc.gameMode.continueDestroyBlock(target, Direction.UP);
            p.swing(InteractionHand.MAIN_HAND);
            return;
        }
        target = null;
        int r = (int) Math.ceil(range.get());
        BlockPos at = BlockPos.containing(eye);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
            if (!valid(mc, p, pos, eye)) continue;
            if (p.isCreative()) {
                mc.gameMode.startDestroyBlock(pos.immutable(), Direction.UP);
                if (++broken >= 16) break;
                continue;
            }
            double d = eye.distanceToSqr(Vec3.atCenterOf(pos));
            if (d < bestD) { bestD = d; best = pos.immutable(); }
        }
        if (broken > 0) { p.swing(InteractionHand.MAIN_HAND); return; }
        if (best == null) return;
        target = best;
        mc.gameMode.startDestroyBlock(best, Direction.UP);
        p.swing(InteractionHand.MAIN_HAND);
    }

    private boolean valid(Minecraft mc, LocalPlayer p, BlockPos pos, Vec3 eye) {
        BlockState s = mc.level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(mc.level, pos) < 0) return false;
        if (keepFloor.get() && pos.getY() < p.getBlockY()) return false;
        if (only != null && s.getBlock() != only) return false;
        return eye.distanceToSqr(Vec3.atCenterOf(pos)) <= range.get() * range.get();
    }
}
