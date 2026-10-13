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

/** Places blocks under you as you walk. */
public final class ScaffoldModule extends FeatureModule {
    public static final ScaffoldModule INSTANCE = new ScaffoldModule();
    private final Setting.Bool autoSwitch = setting(Setting.Bool.standalone("Switch to blocks", true), "Picks a block from your hotbar if you are not holding one, then switches back.");
    private final Setting.Bool tower = setting(Setting.Bool.standalone("Tower with Space", true), "Holding Space while standing still pillars straight up.");
    private final Setting.Bool airPlace = setting(Setting.Bool.standalone("Place in the air", false), "Places even with nothing next to it (only works on servers that allow it).");

    private ScaffoldModule() { super("Scaffold", "Places blocks under you as you walk", "World", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ());
        // look a little ahead so fast walking does not outrun the placement
        Vec3 v = p.getDeltaMovement();
        BlockPos ahead = BlockPos.containing(p.getX() + v.x * 2, p.getY() - 0.5, p.getZ() + v.z * 2);
        BlockPos spot = mc.level.getBlockState(below).canBeReplaced() ? below : mc.level.getBlockState(ahead).canBeReplaced() ? ahead : null;
        if (tower.get() && mc.options.keyJump.isDown() && !Moves.moving(mc) && p.onGround() && spot == null) p.jumpFromGround();
        if (spot == null) return;
        int slot = Moves.blockSlot(p);
        if (slot < 0 || (!autoSwitch.get() && slot != p.getInventory().getSelectedSlot())) return;
        int prev = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        Moves.place(mc, spot, airPlace.get());
        if (autoSwitch.get()) p.getInventory().setSelectedSlot(prev);
    }
}
