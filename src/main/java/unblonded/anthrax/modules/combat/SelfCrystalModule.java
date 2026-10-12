package unblonded.anthrax.modules.combat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

/** Tracks crystal placements and attacks crystals that appear at those positions. */
public final class SelfCrystalModule extends FeatureModule {
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ms)", 15, 1, 300, 1), "Pause after your crystal appears before it is broken.");
    private final Setting.Num humanity = setting(Setting.Num.standalone("Humanity (ms)", 3, 0, 100, 1), "Random extra delay added each time.");
    private final Map<BlockPos, Long> pending = new LinkedHashMap<>();
    private ClientLevel pendingLevel;
    private Player owner;

    public SelfCrystalModule() {
        super("Self Crystal", "Attack crystals after your placement registers", "Combat", false);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!isEnabled() || !level.isClientSide() || hand != InteractionHand.MAIN_HAND ||
                    !player.getItemInHand(hand).is(Items.END_CRYSTAL)) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos().relative(hit.getDirection()).immutable();
            pendingLevel = (ClientLevel) level;
            owner = player;
            long jitter = humanity.get() <= 0 ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextLong((long) humanity.get() + 1);
            pending.put(pos, System.currentTimeMillis() + (long) delay.get() + jitter);
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        if (!isEnabled() || client.player == null || client.level == null || client.gameMode == null
                || (pendingLevel != null && (client.level != pendingLevel || client.player != owner))) {
            pending.clear(); pendingLevel = null; owner = null;
            return;
        }
        if (ServerSafety.paused()) return;
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > 5000) return true;
            if (now < entry.getValue()) return false;
            BlockPos pos = entry.getKey();
            var box = new AABB(pos).inflate(1, 2, 1);
            EndCrystal crystal = client.level.getEntitiesOfClass(EndCrystal.class, box).stream().findFirst().orElse(null);
            if (crystal == null || !ServerSafety.canReach(client.player, crystal)) return false;
            client.gameMode.attack(client.player, crystal);
            client.player.swing(InteractionHand.MAIN_HAND);
            return true;
        });
        if (pending.isEmpty()) { pendingLevel = null; owner = null; }
    }
}
