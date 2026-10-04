package unblonded.anthrax.modules.combat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Charges a respawn anchor with glowstone, then triggers the normal interaction flow. */
public final class AutoAnchorModule extends FeatureModule {
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ms)", 15, 1, 50, 1));
    private final Setting.Num humanity = setting(Setting.Num.standalone("Humanity (ms)", 3, 0, 50, 1));
    private BlockPos pending;
    private int glowstoneSlot = -1;
    private int stage;
    private long chargeAt;
    private int originalSlot;
    private long expiresAt;
    private ClientLevel pendingLevel;
    private Player owner;

    public AutoAnchorModule() {
        super("Auto Anchor", "Charge a placed respawn anchor and interact with it", "Combat", false);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!isEnabled() || !level.isClientSide() || hand != InteractionHand.MAIN_HAND
                    || !player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) return InteractionResult.PASS;
            int slot = findHotbar(player);
            if (slot >= 0) {
                pending = hit.getBlockPos().relative(hit.getDirection()).immutable();
                pendingLevel = (ClientLevel) level;
                owner = player;
                glowstoneSlot = slot;
                originalSlot = player.getInventory().getSelectedSlot();
                stage = 0;
                chargeAt = 0;
                expiresAt = System.currentTimeMillis() + 5000;
            }
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pending == null) return;
            if (!isEnabled() || client.player == null || client.gameMode == null || client.level == null
                    || client.level != pendingLevel || client.player != owner
                    || System.currentTimeMillis() >= expiresAt
                    || !client.level.getBlockState(pending).is(Blocks.RESPAWN_ANCHOR)) {
                reset(client);
                return;
            }
            if (chargeAt == 0) chargeAt = scheduledTime();
            if (System.currentTimeMillis() < chargeAt) return;
            var player = client.player;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pending), Direction.UP, pending, false);
            if (stage == 0) {
                player.getInventory().setSelectedSlot(glowstoneSlot);
                client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                player.swing(InteractionHand.MAIN_HAND);
                stage = 1;
                chargeAt = scheduledTime();
            } else if (stage == 1 && client.level.getBlockState(pending).hasProperty(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE)
                    && client.level.getBlockState(pending).getValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE) > 0) {
                player.getInventory().setSelectedSlot(originalSlot);
                client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                player.swing(InteractionHand.MAIN_HAND);
                reset(client, false);
            }
        });
    }

    private long scheduledTime() {
        long jitter = humanity.get() <= 0 ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextLong((long) humanity.get() + 1);
        return System.currentTimeMillis() + (long) delay.get() + jitter;
    }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled) reset(Minecraft.getInstance());
    }

    private void reset(Minecraft client) { reset(client, true); }

    private void reset(Minecraft client, boolean restoreSlot) {
        if (restoreSlot && owner != null && client.player == owner && originalSlot >= 0)
            owner.getInventory().setSelectedSlot(originalSlot);
        pending = null;
        glowstoneSlot = -1;
        stage = 0;
        chargeAt = 0;
        expiresAt = 0;
        pendingLevel = null;
        owner = null;
        originalSlot = -1;
    }

    private static int findHotbar(net.minecraft.world.entity.player.Player player) {
        for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).is(Items.GLOWSTONE)) return i;
        return -1;
    }
}
