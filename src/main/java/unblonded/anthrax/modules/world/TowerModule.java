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

/** Pillars straight up: jumps and places a block under you at the top of each jump. */
public final class TowerModule extends FeatureModule {
    public static final TowerModule INSTANCE = new TowerModule();
    private final Setting.Bool needSpace = setting(Setting.Bool.standalone("Only while holding Space", true), "Tower only while the jump key is held.");

    private TowerModule() { super("Tower", "Pillars straight up, jumping and placing a block under you at the top of each jump", "World", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        if (needSpace.get() && !mc.options.keyJump.isDown()) return;
        int slot = Moves.blockSlot(p);
        if (slot < 0) return;
        if (p.onGround()) { p.jumpFromGround(); return; }
        BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
        if (p.getDeltaMovement().y < 0.1 && mc.level.getBlockState(under).canBeReplaced()) {
            int prev = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(slot);
            Moves.place(mc, under, false);
            p.getInventory().setSelectedSlot(prev);
        }
    }
}
