package unblonded.anthrax.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/**
 * Harvests everything that is ready around you and replants it. Each crop is handled the way it grows: crops and
 * nether wart when fully grown; sugar cane, cactus and bamboo above the bottom block (so they regrow); melons and
 * pumpkins themselves (leaving the stem); cocoa when ripe; sweet berries by picking (right-click), not breaking.
 */
public final class AutoFarmModule extends FeatureModule {
    public static final AutoFarmModule INSTANCE = new AutoFarmModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 4, 1, 5, 1), "How far around you to harvest, in blocks (capped at your reach).");
    private final Setting.Num perTick = setting(Setting.Num.standalone("Actions per tick", 2, 1, 8, 1), "How many harvests or plantings each tick.");
    private final Setting.Bool replant = setting(Setting.Bool.standalone("Replant", true), "Plants the matching seed back: crops, nether wart and cocoa.");
    private final Setting.Bool crops = setting(Setting.Bool.standalone("Wheat, carrots, potatoes, beetroot", true), "");
    private final Setting.Bool wart = setting(Setting.Bool.standalone("Nether wart", true), "");
    private final Setting.Bool tall = setting(Setting.Bool.standalone("Sugar cane, cactus, bamboo", true), "Breaks everything above the bottom block, so it grows back.");
    private final Setting.Bool gourds = setting(Setting.Bool.standalone("Melons and pumpkins", true), "Breaks the fruit and leaves the stem.");
    private final Setting.Bool cocoa = setting(Setting.Bool.standalone("Cocoa", true), "");
    private final Setting.Bool berries = setting(Setting.Bool.standalone("Sweet berries", true), "Picks ripe bushes without breaking them.");
    private final Setting.Bool tillTip = setting(Setting.Bool.standalone("Plant empty farmland", false), "Also plants seeds from your hotbar on any empty farmland or soul sand in range.");

    /** A spot to replant once the harvest has gone through: where, which item, and the face to click. */
    private record Replant(BlockPos pos, Item seed, Direction against, long due) {}
    private final java.util.ArrayDeque<Replant> replants = new java.util.ArrayDeque<>();

    private AutoFarmModule() { super("Auto Farm", "Harvests mature crops around you while you walk through a farm, and replants them", "World", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        int budget = (int) perTick.get();
        budget -= replantDue(mc, p, budget);
        if (budget <= 0) return;
        int r = (int) range.get();
        Vec3 eye = p.getEyePosition();
        BlockPos at = p.blockPosition();
        for (BlockPos raw : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 3, r))) {
            if (budget <= 0) return;
            BlockPos pos = raw.immutable();
            if (!p.isWithinBlockInteractionRange(pos, 0.0)) continue;
            BlockState s = mc.level.getBlockState(pos);
            Block b = s.getBlock();
            if (crops.get() && b instanceof CropBlock crop && crop.isMaxAge(s)) {
                harvest(mc, p, pos);
                queue(pos, s.getCloneItemStack(mc.level, pos, false).getItem(), Direction.DOWN);
                budget--;
            } else if (wart.get() && b instanceof NetherWartBlock && s.getValue(NetherWartBlock.AGE) >= 3) {
                harvest(mc, p, pos);
                queue(pos, Items.NETHER_WART, Direction.DOWN);
                budget--;
            } else if (tall.get() && (b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b instanceof BambooStalkBlock)
                    && mc.level.getBlockState(pos.below()).is(b) && !mc.level.getBlockState(pos.below(2)).is(b)) {
                // the block above the bottom one: breaking it drops the rest and the base regrows
                harvest(mc, p, pos);
                budget--;
            } else if (gourds.get() && (b == Blocks.MELON || b == Blocks.PUMPKIN) && hasAttachedStem(mc, pos)) {
                harvest(mc, p, pos);
                budget--;
            } else if (cocoa.get() && b instanceof CocoaBlock && s.getValue(CocoaBlock.AGE) >= 2) {
                Direction facing = s.getValue(CocoaBlock.FACING);
                harvest(mc, p, pos);
                queue(pos, Items.COCOA_BEANS, facing);
                budget--;
            } else if (berries.get() && b instanceof SweetBerryBushBlock && s.getValue(SweetBerryBushBlock.AGE) >= 2) {
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                if (mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit).consumesAction()) p.swing(InteractionHand.MAIN_HAND);
                budget--;
            } else if (tillTip.get() && replant.get() && s.isAir() && plantable(mc.level.getBlockState(pos.below()))) {
                Item seed = seedFor(p, mc.level.getBlockState(pos.below()));
                if (seed != null && plant(mc, p, pos, seed, Direction.DOWN)) budget--;
            }
        }
    }

    private static void harvest(Minecraft mc, LocalPlayer p, BlockPos pos) {
        mc.gameMode.startDestroyBlock(pos, Direction.UP);
        p.swing(InteractionHand.MAIN_HAND);
    }

    private void queue(BlockPos pos, Item seed, Direction against) {
        if (replant.get() && seed != Items.AIR) replants.add(new Replant(pos, seed, against, System.currentTimeMillis() + 120));
    }

    /** Plants queued spots whose harvest has gone through; returns how many actions it used. */
    private int replantDue(Minecraft mc, LocalPlayer p, int budget) {
        int used = 0;
        long now = System.currentTimeMillis();
        while (!replants.isEmpty() && used < budget) {
            Replant rp = replants.peekFirst();
            if (rp.due > now) break;
            replants.pollFirst();
            if (now - rp.due > 3000 || !mc.level.getBlockState(rp.pos).isAir()) continue;
            if (plant(mc, p, rp.pos, rp.seed, rp.against)) used++;
        }
        return used;
    }

    /** Clicks {@code seed} onto the block on the {@code against} side of {@code pos} (the soil, or the log for cocoa). */
    private static boolean plant(Minecraft mc, LocalPlayer p, BlockPos pos, Item seed, Direction against) {
        int slot = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(seed)) { slot = i; break; }
        if (slot < 0) return false;
        BlockPos support = pos.relative(against);
        Direction face = against.getOpposite();
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5), face, support, false);
        int prev = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        boolean ok = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit).consumesAction();
        if (ok) p.swing(InteractionHand.MAIN_HAND);
        p.getInventory().setSelectedSlot(prev);
        return ok;
    }

    private static boolean plantable(BlockState soil) { return soil.is(Blocks.FARMLAND) || soil.is(Blocks.SOUL_SAND); }

    /** A hotbar seed that grows on this soil: nether wart on soul sand, crop seeds on farmland. */
    private static Item seedFor(LocalPlayer p, BlockState soil) {
        Item[] options = soil.is(Blocks.SOUL_SAND) ? new Item[]{Items.NETHER_WART}
                : new Item[]{Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.TORCHFLOWER_SEEDS};
        for (Item it : options) for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(it)) return it;
        return null;
    }

    /** Only fruit grown from a stem counts, so a melon or pumpkin someone placed as decoration is left alone. */
    private static boolean hasAttachedStem(Minecraft mc, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = mc.level.getBlockState(pos.relative(d));
            if ((s.is(Blocks.ATTACHED_MELON_STEM) || s.is(Blocks.ATTACHED_PUMPKIN_STEM))
                    && s.getValue(net.minecraft.world.level.block.AttachedStemBlock.FACING) == d.getOpposite()) return true;
        }
        return false;
    }

    @Override protected void onToggle(boolean enabled) { replants.clear(); }
}
