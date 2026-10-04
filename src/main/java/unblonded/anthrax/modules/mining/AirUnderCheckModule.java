package unblonded.anthrax.modules.mining;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Packet Edit's Player Dig Safety: checks the block two below the player and can show status. */
public final class AirUnderCheckModule extends FeatureModule {
    private final Setting.Bool showStatus = setting(Setting.Bool.standalone("Show status in-game", true));
    private volatile boolean danger;
    private volatile int playerY;

    public AirUnderCheckModule() {
        super("Player Dig Safety", "Show the block and Y-level safety status", "Mining", false);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("anthrax", "player_dig_safety"), (graphics, delta) -> {
            if (!isEnabled() || !showStatus.get() || Minecraft.getInstance().player == null) return;
            int color = danger ? 0xFFFF5555 : 0xFF55FF55;
            graphics.textRenderer().accept(8, 8, Component.literal((danger ? "DANGER" : "SAFE") + " | Y → " + playerY).withColor(color));
        });
    }

    @Override public void tick(Minecraft client) {
        if (client.player == null || client.level == null) return;
        BlockPos twoBelow = client.player.blockPosition().below(2);
        var block = client.level.getBlockState(twoBelow).getBlock();
        // The block under the player is dangerous when it is neither air nor lava.
        danger = block != Blocks.AIR && block != Blocks.LAVA;
        playerY = client.player.blockPosition().getY();
    }
}
