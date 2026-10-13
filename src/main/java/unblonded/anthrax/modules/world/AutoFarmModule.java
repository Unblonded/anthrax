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

/** Harvests mature crops around you, and can replant them from your hotbar. */
public final class AutoFarmModule extends FeatureModule {
    public static final AutoFarmModule INSTANCE = new AutoFarmModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 4, 1, 5, 1), "How far around you to harvest, in blocks.");
    private final Setting.Bool replant = setting(Setting.Bool.standalone("Replant", true), "Plants the matching seed back on the farmland.");
    private final Setting.Num perTick = setting(Setting.Num.standalone("Crops per tick", 2, 1, 8, 1), "How many crops to break each tick.");
    private BlockPos pendingReplant;
    private net.minecraft.world.item.Item pendingSeed;

    private AutoFarmModule() { super("Auto Farm", "Harvests mature crops around you while you walk through a farm", "World", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        if (pendingReplant != null) {
            int slot = -1;
            for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(pendingSeed)) { slot = i; break; }
            if (slot >= 0 && mc.level.getBlockState(pendingReplant).isAir()) {
                int prev = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(slot);
                Moves.place(mc, pendingReplant, false);
                p.getInventory().setSelectedSlot(prev);
            }
            pendingReplant = null;
        }
        int r = (int) range.get(), done = 0;
        BlockPos at = p.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, 1, r))) {
            BlockState s = mc.level.getBlockState(pos);
            if (!(s.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) || !crop.isMaxAge(s)) continue;
            if (p.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 4.5 * 4.5) continue;
            BlockPos fixed = pos.immutable();
            mc.gameMode.startDestroyBlock(fixed, Direction.UP);
            p.swing(InteractionHand.MAIN_HAND);
            if (replant.get() && pendingReplant == null) {
                pendingReplant = fixed;
                pendingSeed = s.getCloneItemStack(mc.level, fixed, false).getItem();
            }
            if (++done >= perTick.get()) return;
        }
    }
}
