package unblonded.anthrax.modules.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Player Dig Safety: when digging straight down you mine the block under your feet and land on the
 * one below it. That landing block is checked (plus a short look further down for drops and fluids).
 */
public final class AirUnderCheckModule extends FeatureModule {
    public static final AirUnderCheckModule INSTANCE = new AirUnderCheckModule();

    public enum Status { SAFE, DROP, LAVA, WATER }

    private final Setting.Bool showStatus = setting(Setting.Bool.standalone("Show status in-game", true));
    private final Setting.Bool alert = setting(Setting.Bool.standalone("Alert on danger", true));
    private final Setting.Num depth = setting(Setting.Num.standalone("Scan depth", 4, 1, 12, 1));
    private volatile Status status = Status.SAFE;
    private volatile int playerY;
    private volatile int dropDistance;

    private AirUnderCheckModule() {
        super("Player Dig Safety", "Warn before digging down into a drop, lava or water", "Mining", false);
    }

    @Override public void tick(Minecraft client) {
        if (client.player == null || client.level == null) return;
        BlockPos feet = client.player.blockPosition();
        playerY = feet.getY();
        Status next = Status.SAFE;
        int drop = 0;
        // Start at the landing block (two below the feet) and look a few blocks further down.
        for (int i = 2; i <= 1 + (int) depth.get(); i++) {
            BlockPos pos = feet.below(i);
            BlockState state = client.level.getBlockState(pos);
            FluidState fluid = state.getFluidState();
            if (fluid.is(FluidTags.LAVA)) { next = Status.LAVA; break; }
            if (fluid.is(FluidTags.WATER)) { next = Status.WATER; break; }
            if (!state.getCollisionShape(client.level, pos).isEmpty()) break;
            drop++;
            next = Status.DROP;
        }
        if (next != Status.SAFE && next != status && alert.get()) {
            Notifications.warn("Dig Safety", switch (next) {
                case LAVA -> "Lava below - do not dig down";
                case WATER -> "Water below you";
                default -> "Drop of " + drop + (drop == 1 ? " block" : " blocks") + " below";
            });
        }
        status = next;
        dropDistance = drop;
    }

    public boolean showStatus() { return isEnabled() && showStatus.get(); }
    public Status status() { return status; }
    public int playerY() { return playerY; }
    public int dropDistance() { return dropDistance; }

    @Override public String hudInfo() { return status == Status.SAFE ? "Safe" : status.name(); }
}
