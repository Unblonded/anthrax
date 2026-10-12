package unblonded.anthrax.modules.combat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Repeats crystal placement and attack at the nearest valid base while holding crystals. */
public final class CrystalSpamModule extends FeatureModule {
    private final Setting.Num radius = setting(Setting.Num.standalone("Search radius", 5, 1, 6, 1), "How far around you to look for obsidian or bedrock to place on (always kept within reach).");
    private final Setting.Num breakDelay = setting(Setting.Num.standalone("Break delay (ms)", 10, 1, 1000, 1), "Pause before breaking each placed crystal.");
    private long nextAction;
    private BlockPos pendingBase;
    private boolean waitingForCrystal;
    private LocalPlayer owner;
    private ClientLevel world;
    private long placementExpiresAt;

    public CrystalSpamModule() {
        super("Crystal Spam", "Repeatedly place and attack crystals from the held crystal stack", "Combat", false);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft client) {
        if (!isEnabled()) return;
        if (client.player == null || client.level == null || client.gameMode == null) { reset(); return; }
        if (owner != null && (client.player != owner || client.level != world)) { reset(); return; }
        if (ServerSafety.paused()) return;
        long now = System.currentTimeMillis();
        if (now < nextAction || !client.player.getMainHandItem().is(Items.END_CRYSTAL)) return;
        if (waitingForCrystal) {
            if (now >= placementExpiresAt) {
                pendingBase = null;
                waitingForCrystal = false;
                nextAction = now;
            } else {
                EndCrystal found = client.level.getEntitiesOfClass(EndCrystal.class, new AABB(pendingBase).inflate(1, 2, 1))
                        .stream().findFirst().orElse(null);
                if (found != null && ServerSafety.canReach(client.player, found)) {
                    client.gameMode.attack(client.player, found);
                    client.player.swing(InteractionHand.MAIN_HAND);
                    waitingForCrystal = false;
                    nextAction = now + (long)breakDelay.get();
                    return;
                }
                nextAction = now + 25L;
                return;
            }
        }
        BlockPos base = findClosestBase(client);
        if (base == null) return;
        BlockHitResult hit = ServerSafety.click(base, Direction.UP);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        client.player.swing(InteractionHand.MAIN_HAND);
        pendingBase = base;
        waitingForCrystal = true;
        owner = client.player;
        world = client.level;
        placementExpiresAt = now + 1500L;
        nextAction = now + Math.max(1, (long)breakDelay.get() / 2);
    }

    private BlockPos findClosestBase(Minecraft client) {
        BlockPos origin = client.player.blockPosition();
        int r = (int) radius.get();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x=-r; x<=r; x++) for (int y=-r; y<=r; y++) for (int z=-r; z<=r; z++) {
            BlockPos pos = origin.offset(x,y,z);
            var state = client.level.getBlockState(pos);
            if ((!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK))
                    || !client.level.getBlockState(pos.above()).isAir()
                    || !client.level.getBlockState(pos.above(2)).isAir()
                    || !ServerSafety.canReach(client.player, pos)) continue;
            double distance = client.player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance) { best = pos.immutable(); bestDistance = distance; }
        }
        return best;
    }

    private void reset() { pendingBase = null; waitingForCrystal = false; nextAction = 0; placementExpiresAt = 0; owner = null; world = null; }
    @Override protected void onToggle(boolean enabled) { if (!enabled) reset(); }
}
