package unblonded.anthrax.modules.combat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Packet Edit's attack-triggered crystal placement and detonation flow. */
public final class AutoCrystalModule extends FeatureModule {
    private final Setting.Num attackDelay = setting(Setting.Num.standalone("Attack time (ms)", 20, 5, 500, 5), "Pause between placing the crystal and detonating it.");
    private final Setting.Num placeDelay = setting(Setting.Num.standalone("Place time (ms)", 20, 5, 500, 5), "Pause between your weapon hit and placing the crystal.");
    private Player target;
    private Player owner;
    private net.minecraft.client.multiplayer.ClientLevel targetLevel;
    private BlockPos targetPos;
    private long phaseAt;
    private int originalSlot = -1;
    private int phase;

    public AutoCrystalModule() {
        super("Auto Crystal", "Place and detonate a crystal after a weapon hit", "Combat", false);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!isEnabled() || !level.isClientSide() || hand != InteractionHand.MAIN_HAND
                    || !(entity instanceof Player victim) || !weapon(player)) return InteractionResult.PASS;
            target = victim;
            owner = player;
            targetLevel = (net.minecraft.client.multiplayer.ClientLevel) level;
            targetPos = null;
            phase = 0;
            phaseAt = System.currentTimeMillis();
            originalSlot = player.getInventory().getSelectedSlot();
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tickAutoCrystal);
    }

    private void tickAutoCrystal(Minecraft client) {
        if (!isEnabled() || target == null) return;
        if (client.player == null || client.player != owner || client.level == null || client.gameMode == null || client.level != targetLevel) { reset(client); return; }
        if (ServerSafety.paused()) return;
        long now = System.currentTimeMillis();
        if (target.isRemoved() || !target.isAlive() || client.player.distanceToSqr(target) > 144) { reset(client); return; }
        if (phase == 0 && now - phaseAt >= (long) placeDelay.get()) {
            targetPos = findBase(client, target.blockPosition());
            if (targetPos == null) { reset(client); return; }
            int crystalSlot = findItem(client, Items.END_CRYSTAL);
            if (crystalSlot < 0) { reset(client); return; }
            client.player.getInventory().setSelectedSlot(crystalSlot);
            BlockHitResult hit = ServerSafety.click(targetPos, Direction.UP);
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
            client.player.swing(InteractionHand.MAIN_HAND);
            phase = 1;
            phaseAt = now;
        } else if (phase == 1 && now - phaseAt >= (long) attackDelay.get()) {
            EndCrystal crystal = client.level.getEntitiesOfClass(EndCrystal.class, new AABB(targetPos).inflate(2))
                    .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(target))).orElse(null);
            if (crystal != null && ServerSafety.canReach(client.player, crystal)) {
                client.player.getInventory().setSelectedSlot(originalSlot);
                client.gameMode.attack(client.player, crystal);
                client.player.swing(InteractionHand.MAIN_HAND);
            }
            reset(client);
        }
    }

    private static BlockPos findBase(Minecraft client, BlockPos center) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x=-4; x<=4; x++) for (int y=-2; y<=1; y++) for (int z=-4; z<=4; z++) {
            BlockPos p = center.offset(x,y,z);
            var state = client.level.getBlockState(p);
            if ((!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK))
                    || !client.level.getBlockState(p.above()).isAir()
                    || !client.level.getBlockState(p.above(2)).isAir()
                    || !ServerSafety.canReach(client.player, p)) continue;
            double distance = client.player.distanceToSqr(Vec3.atCenterOf(p));
            if (distance < bestDist) { best = p.immutable(); bestDist = distance; }
        }
        return best;
    }

    private static boolean weapon(Player player) {
        return player.getMainHandItem().is(ItemTags.SWORDS) || player.getMainHandItem().is(ItemTags.AXES);
    }

    private static int findItem(Minecraft client, net.minecraft.world.item.Item item) {
        for (int i=0; i<9; i++) if (client.player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private void reset(Minecraft client) {
        if (originalSlot >= 0 && owner != null && client.player == owner)
            owner.getInventory().setSelectedSlot(originalSlot);
        target = null; owner = null; targetLevel = null; targetPos = null; originalSlot = -1; phase = 0;
    }
    @Override protected void onToggle(boolean enabled) {
        if (!enabled) reset(Minecraft.getInstance());
    }
}
