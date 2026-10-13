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

/** Removes the pause between blocks and can speed up breaking a little. */
public final class SpeedMineModule extends FeatureModule {
    public static final SpeedMineModule INSTANCE = new SpeedMineModule();
    private final Setting.Bool noDelay = setting(Setting.Bool.standalone("No break delay", true), "Removes the 5-tick pause after each block.");
    private final Setting.Num finishAt = setting(Setting.Num.standalone("Finish at (%)", 100, 60, 100, 5), "Breaks the block once progress reaches this. Servers reject blocks finished much too early.");

    private SpeedMineModule() { super("Speed Mine", "Mines faster by removing the delay between blocks and finishing each block early", "World", false); }

    @Override public void tick(Minecraft mc) {
        if (mc.gameMode == null) return;
        var gm = (unblonded.anthrax.mixin.GameModeAccessor) mc.gameMode;
        if (noDelay.get() && gm.anthrax$getDestroyDelay() > 0) gm.anthrax$setDestroyDelay(0);
        if (finishAt.get() < 100 && mc.gameMode.isDestroying() && gm.anthrax$getDestroyProgress() >= finishAt.get() / 100f)
            gm.anthrax$setDestroyProgress(1f);
    }
}
