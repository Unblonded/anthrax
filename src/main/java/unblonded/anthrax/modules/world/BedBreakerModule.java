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

/** Breaks beds (and dragon eggs) within reach. */
public final class BedBreakerModule extends FeatureModule {
    public static final BedBreakerModule INSTANCE = new BedBreakerModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 4.5, 1, 5, 0.5), "Reach for finding beds.");
    private final Setting.Bool eggs = setting(Setting.Bool.standalone("Dragon eggs", true), "Also breaks dragon eggs.");
    private BlockPos target;

    private BedBreakerModule() { super("Bed Breaker", "Breaks beds and dragon eggs around you", "World", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) return;
        Vec3 eye = p.getEyePosition();
        if (target == null || !wanted(mc.level.getBlockState(target)) || eye.distanceToSqr(Vec3.atCenterOf(target)) > range.get() * range.get()) {
            target = null;
            int r = (int) Math.ceil(range.get());
            BlockPos at = BlockPos.containing(eye);
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
                if (wanted(mc.level.getBlockState(pos)) && eye.distanceToSqr(Vec3.atCenterOf(pos)) <= range.get() * range.get()) { target = pos.immutable(); break; }
            }
            if (target == null) return;
            mc.gameMode.startDestroyBlock(target, Direction.UP);
        } else mc.gameMode.continueDestroyBlock(target, Direction.UP);
        p.swing(InteractionHand.MAIN_HAND);
    }

    private boolean wanted(BlockState s) {
        return s.getBlock() instanceof net.minecraft.world.level.block.BedBlock || (eggs.get() && s.is(net.minecraft.world.level.block.Blocks.DRAGON_EGG));
    }
}
