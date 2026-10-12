package unblonded.anthrax.modules.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import unblonded.anthrax.modules.world.WaypointsModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Singleplayer test sites for the detection modules (Base Finder, Storage ESP/Scan, Hole ESP, Amethyst ESP,
 * Seed-Ray mined-ore evidence, Dig Safety). Blocks are written straight into the integrated server's level,
 * so cheats do not need to be on. Sites sit on a 3x3 grid of chunk-centred slots 48 blocks apart around the
 * player, each with a waypoint.
 */
final class TestStructures {
    private TestStructures() {}

    private static final int SPACING = 48, HALF = 12, CLEAR_HEIGHT = 16;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private record Site(String key, String label, int gx, int gz, int half, Consumer<Builder> build) {}

    private static final List<Site> SITES = List.of(
            new Site("stash", "Test stash", -1, -1, HALF, TestStructures::stash),
            new Site("base", "Test base", 0, -1, HALF, TestStructures::base),
            new Site("farm", "Test farm", 1, -1, HALF, TestStructures::farm),
            new Site("redstone", "Test redstone", -1, 0, HALF, TestStructures::redstone),
            new Site("holes", "Test holes", 0, 0, HALF, TestStructures::holes),
            new Site("portal", "Test portals", 1, 0, HALF, TestStructures::portals),
            new Site("raid", "Test raided base", -1, 1, 22, TestStructures::raid),
            new Site("amethyst", "Test geode", 0, 1, HALF, TestStructures::geode),
            new Site("mine", "Test branch mine", 1, 1, HALF, TestStructures::mine));

    static String names() {
        StringBuilder sb = new StringBuilder("all, clear");
        for (Site s : SITES) sb.append(", ").append(s.key);
        return sb.toString();
    }

    /** Returns an error message, or null once the build has been queued on the server thread. */
    static String run(String which) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return "Test structures only work in singleplayer";
        String key = which.toLowerCase(Locale.ROOT);
        boolean clear = key.equals("clear");
        List<Site> chosen = new ArrayList<>();
        for (Site s : SITES) if (clear || key.equals("all") || s.key.equals(key)) chosen.add(s);
        if (chosen.isEmpty()) return "Unknown structure \"" + which + "\". Options: " + names();

        // chunk-centred grid so every site's evidence stays inside its own chunks
        int cx = (mc.player.getBlockX() >> 4 << 4) + 8, cz = (mc.player.getBlockZ() >> 4 << 4) + 8;
        int y = mc.player.getBlockY();
        var dimension = mc.player.level().dimension();
        server.execute(() -> {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) return;
            for (Site s : chosen) {
                Builder b = new Builder(level, cx + s.gx * SPACING, y, cz + s.gz * SPACING);
                b.prepare(s.half);
                if (!clear) s.build.accept(b);
            }
        });
        WaypointsModule wp = WaypointsModule.INSTANCE;
        for (Site s : chosen) {
            if (clear) wp.remove(s.label);
            else wp.add(s.label, cx + s.gx * SPACING, s.key.equals("mine") ? MINE_Y : y, cz + s.gz * SPACING);
        }
        return null;
    }

    // ------------------------------------------------------------------ sites

    /** Shulkers, ender chests and loot chests: stash in Base Finder, Storage ESP, and Storage Scan searches. */
    private static void stash(Builder b) {
        Block[] shulkers = {Blocks.SHULKER_BOX, Blocks.DYED_SHULKER_BOX.pick(DyeColor.PURPLE), Blocks.DYED_SHULKER_BOX.pick(DyeColor.BLACK), Blocks.DYED_SHULKER_BOX.pick(DyeColor.CYAN),
                Blocks.DYED_SHULKER_BOX.pick(DyeColor.RED), Blocks.DYED_SHULKER_BOX.pick(DyeColor.WHITE)};
        Item[][] loot = {
                {Items.DIAMOND, Items.DIAMOND_BLOCK, Items.NETHERITE_INGOT},
                {Items.TOTEM_OF_UNDYING, Items.ENCHANTED_GOLDEN_APPLE, Items.END_CRYSTAL},
                {Items.ELYTRA, Items.FIREWORK_ROCKET, Items.ENDER_PEARL},
                {Items.OBSIDIAN, Items.RESPAWN_ANCHOR, Items.GLOWSTONE},
                {Items.EXPERIENCE_BOTTLE, Items.GOLDEN_CARROT, Items.SHULKER_SHELL}};
        // a buried vault: obsidian box with shulker rows inside
        b.box(-6, 0, -6, 6, 4, 6, Blocks.OBSIDIAN.defaultBlockState(), true);
        int i = 0;
        for (int x = -4; x <= 4; x += 2)
            for (int z = -4; z <= 4; z += 2) {
                Block block = (x + z) % 4 == 0 ? shulkers[i % shulkers.length] : Blocks.CHEST;
                BlockState state = block == Blocks.CHEST
                        ? Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                        : block.defaultBlockState();
                b.set(x, 0, z, state);
                b.fill(x, 0, z, loot[i % loot.length], 9 + i % 9);
                b.set(x, 1, z, Blocks.SHULKER_BOX.defaultBlockState());
                b.fill(x, 1, z, loot[(i + 2) % loot.length], 27);
                i++;
            }
        for (int x = -5; x <= 5; x += 10) {
            b.set(x, 0, 0, Blocks.ENDER_CHEST.defaultBlockState());
            b.set(x, 0, 3, Blocks.BARREL.defaultBlockState());
            b.fill(x, 0, 3, loot[0], 12);
            b.set(x, 0, -3, Blocks.TRAPPED_CHEST.defaultBlockState());
            b.fill(x, 0, -3, loot[1], 5);
        }
        b.set(0, 5, 0, Blocks.TORCH.defaultBlockState());
    }

    /** A lived-in house: beds, workstations, beacon, enchanting setup, lights, glass and concrete. */
    private static void base(Builder b) {
        BlockState wall = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState(), glass = Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
        b.box(-6, -1, -6, 6, -1, 6, Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
        b.box(-6, 0, -6, 6, 4, 6, wall, true);
        b.box(-6, 5, -6, 6, 5, 6, Blocks.DARK_OAK_PLANKS.defaultBlockState(), false);
        for (int a = -4; a <= 4; a += 2)
            for (int yy = 1; yy <= 3; yy++) {
                b.set(a, yy, -6, glass); b.set(a, yy, 6, glass); b.set(-6, yy, a, glass); b.set(6, yy, a, glass);
            }
        b.set(0, 0, 6, Blocks.AIR.defaultBlockState()); b.set(0, 1, 6, Blocks.AIR.defaultBlockState());
        for (int x = -5; x <= -2; x++) b.bed(x, 0, -5, Direction.SOUTH, Blocks.BED.pick(DyeColor.RED));
        b.set(-5, 0, 0, Blocks.CRAFTING_TABLE.defaultBlockState());
        for (int z = 1; z <= 4; z++) {
            b.set(-5, 0, z, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                    .setValue(BlockStateProperties.LIT, z % 2 == 0));
            b.set(-5, 1, z, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            b.fill(-5, 1, z, new Item[]{Items.IRON_INGOT, Items.COOKED_BEEF, Items.COAL}, 10);
        }
        b.set(5, 0, -5, Blocks.ANVIL.defaultBlockState());
        b.set(5, 0, -3, Blocks.BREWING_STAND.defaultBlockState());
        b.set(5, 0, -1, Blocks.LODESTONE.defaultBlockState());
        b.set(5, 0, 1, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES, 4));
        b.set(5, 0, 3, Blocks.JUKEBOX.defaultBlockState());
        b.set(5, 0, 5, Blocks.ENDER_CHEST.defaultBlockState());
        // enchanting corner
        b.set(2, 0, 2, Blocks.ENCHANTING_TABLE.defaultBlockState());
        for (int d = 0; d <= 4; d++) {
            b.set(0 + d, 0, 0, Blocks.BOOKSHELF.defaultBlockState());
            b.set(0, 0, d, Blocks.BOOKSHELF.defaultBlockState());
        }
        for (int x = -4; x <= 4; x += 4) for (int z = -3; z <= 4; z += 7) b.set(x, 0, z, Blocks.LANTERN.defaultBlockState());
        b.box(-3, 0, -2, -1, 0, -1, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState(), false);
        b.set(3, 0, 5, Blocks.POTTED_RED_TULIP.defaultBlockState());
        // beacon on the roof
        b.box(-1, 6, -1, 1, 6, 1, Blocks.IRON_BLOCK.defaultBlockState(), false);
        b.set(0, 7, 0, Blocks.BEACON.defaultBlockState());
        b.set(4, 6, 4, Blocks.DIAMOND_BLOCK.defaultBlockState());
        b.set(-4, 6, 4, Blocks.EMERALD_BLOCK.defaultBlockState());
        // torch path to the door
        for (int z = 7; z <= 11; z += 2) { b.set(-1, 0, z, Blocks.TORCH.defaultBlockState()); b.set(1, 0, z, Blocks.TORCH.defaultBlockState()); }
        for (int z = 7; z <= 11; z++) b.set(0, -1, z, Blocks.DIRT_PATH.defaultBlockState());
    }

    /** Irrigated crop field with ripe wheat, carrots and potatoes, attached stems, composters and hay. */
    private static void farm(Builder b) {
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
        Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES};
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 2; z++) {
                if (x % 4 == 0 && z % 4 == 0) { b.set(x, -1, z, Blocks.WATER.defaultBlockState()); continue; }
                b.set(x, -1, z, farmland);
                Block crop = crops[Math.floorMod(x + 8, 9) / 3];
                b.set(x, 0, z, crop.defaultBlockState().setValue(BlockStateProperties.AGE_7, (x + z) % 3 == 0 ? 3 : 7));
            }
        // pumpkin and melon rows with stems attached
        for (int x = -8; x <= 8; x += 2) {
            b.set(x, -1, 5, farmland);
            boolean pumpkin = x < 0;
            b.set(x, 0, 5, (pumpkin ? Blocks.ATTACHED_PUMPKIN_STEM : Blocks.ATTACHED_MELON_STEM).defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            b.set(x, 0, 6, (pumpkin ? Blocks.PUMPKIN : Blocks.MELON).defaultBlockState());
        }
        for (int x = -8; x <= 8; x += 4) {
            b.set(x, 0, 9, Blocks.COMPOSTER.defaultBlockState().setValue(BlockStateProperties.LEVEL_COMPOSTER, 8));
            b.set(x + 1, 0, 9, Blocks.HAY_BLOCK.defaultBlockState());
            b.set(x + 1, 1, 9, Blocks.HAY_BLOCK.defaultBlockState());
        }
        b.set(10, 0, 0, Blocks.CHEST.defaultBlockState());
        b.fill(10, 0, 0, new Item[]{Items.WHEAT, Items.CARROT, Items.BONE_MEAL}, 15);
    }

    /** Hopper sorter line, piston door, observer clock and powered lamps. */
    private static void redstone(Builder b) {
        BlockState dust = Blocks.REDSTONE_WIRE.defaultBlockState();
        b.box(-10, -1, -10, 10, -1, 10, Blocks.SMOOTH_STONE.defaultBlockState(), false);
        // hopper chain into chests
        for (int x = -8; x <= 8; x++) {
            b.set(x, 1, -6, Blocks.HOPPER.defaultBlockState().setValue(BlockStateProperties.FACING_HOPPER, Direction.EAST));
            if (x % 2 == 0) {
                b.set(x, 0, -6, Blocks.CHEST.defaultBlockState());
                b.set(x, 1, -7, Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
                b.set(x, 1, -8, Blocks.REPEATER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            }
        }
        b.set(9, 1, -6, Blocks.DROPPER.defaultBlockState());
        b.set(-9, 1, -6, Blocks.DISPENSER.defaultBlockState());
        // piston wall
        for (int x = -6; x <= 6; x++) {
            b.set(x, 0, 0, (x % 2 == 0 ? Blocks.STICKY_PISTON : Blocks.PISTON).defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.UP));
            b.set(x, 1, 0, Blocks.SLIME_BLOCK.defaultBlockState());
            b.set(x, 0, 1, dust);
            b.set(x, 0, -1, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        }
        b.set(-7, 0, 1, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.POWERED, true));
        // lamp wall
        for (int x = -6; x <= 6; x += 2) {
            b.set(x, 0, 5, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true));
            b.set(x, 1, 5, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true));
            b.set(x + 1, 0, 5, Blocks.REDSTONE_TORCH.defaultBlockState());
        }
        for (int x = -6; x <= 6; x++) b.set(x, 0, 7, dust);
        b.set(0, 0, 9, Blocks.CRAFTER.defaultBlockState());
        b.set(2, 0, 9, Blocks.TARGET.defaultBlockState());
        b.set(4, 0, 9, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        b.set(-2, 0, 9, Blocks.TNT.defaultBlockState());
        b.set(-4, 0, 9, Blocks.HONEY_BLOCK.defaultBlockState());
    }

    /** Hole ESP (bedrock, obsidian and mixed holes) plus a marked Dig Safety drop over lava. */
    private static void holes(Builder b) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState(), obsidian = Blocks.OBSIDIAN.defaultBlockState();
        hole(b, -6, -4, bedrock, bedrock);
        hole(b, -2, -4, obsidian, obsidian);
        hole(b, 2, -4, bedrock, obsidian);
        hole(b, 6, -4, obsidian, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        // Dig Safety: stand on the gold block and look down. 1 stone, a 3-block drop, then lava.
        BlockState stone = Blocks.STONE.defaultBlockState();
        b.box(2, -8, 4, 6, -2, 8, stone, false);
        b.set(4, -1, 6, Blocks.GOLD_BLOCK.defaultBlockState());
        b.box(4, -5, 6, 4, -3, 6, Blocks.AIR.defaultBlockState(), false);
        b.set(4, -6, 6, Blocks.LAVA.defaultBlockState());
        // and a water pocket next to it
        b.set(-4, -1, 6, Blocks.LAPIS_BLOCK.defaultBlockState());
        b.box(-6, -8, 4, -2, -2, 8, stone, false);
        b.box(-4, -4, 6, -4, -3, 6, Blocks.WATER.defaultBlockState(), false);
    }

    private static void hole(Builder b, int x, int z, BlockState floor, BlockState sides) {
        b.set(x, -1, z, floor);
        b.set(x + 1, 0, z, sides); b.set(x - 1, 0, z, sides);
        b.set(x, 0, z + 1, floor); b.set(x, 0, z - 1, sides);
    }

    /** Two lit nether portals. */
    private static void portals(Builder b) {
        portal(b, -6, 0);
        portal(b, 3, 4);
        b.set(0, 0, -6, Blocks.CHEST.defaultBlockState());
        b.fill(0, 0, -6, new Item[]{Items.FLINT_AND_STEEL, Items.OBSIDIAN}, 4);
    }

    private static void portal(Builder b, int x0, int z) {
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        for (int x = x0; x <= x0 + 3; x++) { b.set(x, 0, z, obsidian); b.set(x, 4, z, obsidian); }
        for (int yy = 1; yy <= 3; yy++) { b.set(x0, yy, z, obsidian); b.set(x0 + 3, yy, z, obsidian); }
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X);
        for (int x = x0 + 1; x <= x0 + 2; x++) for (int yy = 1; yy <= 3; yy++) b.set(x, yy, z, portal);
    }

    /** A blown-up base: explosion craters in bare dirt, a lava cast up at build height, wrecked storage. */
    private static void raid(Builder b) {
        BlockState dirt = Blocks.DIRT.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState();
        b.box(-HALF, -8, -HALF, HALF, -2, HALF, dirt, false);
        b.box(-HALF, -1, -HALF, HALF, -1, HALF, grass, false);
        // crater bowl centred in the chunk: raw dirt at the bottom, grass rim around it
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 6.2) continue;
                int depth = (int) Math.round(5 * (1 - (d / 6.8) * (d / 6.8)));
                if (depth <= 0) continue;
                for (int yy = -1; yy > -1 - depth; yy--) b.set(dx, yy, dz, Blocks.AIR.defaultBlockState());
                b.set(dx, -1 - depth, dz, (dx + dz) % 3 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : dirt);
            }
        // wrecked bunker in the next chunk over
        Random r = new Random(7);
        for (int x = 9; x <= 20; x++)
            for (int z = -6; z <= 6; z++)
                for (int yy = 0; yy <= 3; yy++) {
                    boolean edge = x == 9 || x == 20 || z == -6 || z == 6;
                    if (edge && r.nextFloat() < .7f) b.set(x, yy, z, Blocks.OBSIDIAN.defaultBlockState());
                }
        for (int z = -4; z <= 4; z += 2) {
            b.set(14, 0, z, Blocks.CHEST.defaultBlockState());
            b.fill(14, 0, z, new Item[]{Items.COBBLESTONE, Items.ROTTEN_FLESH}, 3);
        }
        b.bed(17, 0, 0, Direction.NORTH, Blocks.BED.pick(DyeColor.BLUE));
        b.set(12, 0, 3, Blocks.TNT.defaultBlockState());
        // lava cast basin up at build height (flowing lava above y=48 is what raid scoring looks for)
        int top = Math.max(8, 50 - b.y);
        b.box(-3, top, -3, 3, top, 3, Blocks.COBBLESTONE.defaultBlockState(), false);
        for (int a = -3; a <= 3; a++) {
            b.set(a, top + 1, -3, Blocks.COBBLESTONE.defaultBlockState()); b.set(a, top + 1, 3, Blocks.COBBLESTONE.defaultBlockState());
            b.set(-3, top + 1, a, Blocks.COBBLESTONE.defaultBlockState()); b.set(3, top + 1, a, Blocks.COBBLESTONE.defaultBlockState());
        }
        b.set(0, top + 1, 0, Blocks.LAVA.defaultBlockState());
        b.set(0, top - 1, 0, Blocks.STONE.defaultBlockState());
    }

    /** Amethyst geode with mostly full-grown clusters (looks like players spent hours nearby). */
    private static void geode(Builder b) {
        int cy = 6;
        Random r = new Random(42);
        List<BlockPos> budding = new ArrayList<>();
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = -6; dy <= 6; dy++)
                for (int dz = -6; dz <= 6; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    BlockState s;
                    if (d <= 3.2) s = Blocks.AIR.defaultBlockState();
                    else if (d <= 4.2) {
                        boolean bud = r.nextFloat() < .22f;
                        s = (bud ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK).defaultBlockState();
                        if (bud) budding.add(new BlockPos(dx, cy + dy, dz));
                    } else if (d <= 5.2) s = Blocks.CALCITE.defaultBlockState();
                    else if (d <= 6.1) s = Blocks.SMOOTH_BASALT.defaultBlockState();
                    else continue;
                    b.set(dx, cy + dy, dz, s);
                }
        Block[] stages = {Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD,
                Blocks.AMETHYST_CLUSTER, Blocks.AMETHYST_CLUSTER, Blocks.AMETHYST_CLUSTER};
        for (BlockPos p : budding)
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                double dist = Math.sqrt(q.getX() * q.getX() + (q.getY() - cy) * (q.getY() - cy) + q.getZ() * q.getZ());
                if (dist > 3.2) continue;
                b.set(q.getX(), q.getY(), q.getZ(), stages[r.nextInt(stages.length)].defaultBlockState()
                        .setValue(BlockStateProperties.FACING, d));
            }
        // a way in
        b.box(0, cy - 1, 3, 0, cy, 7, Blocks.AIR.defaultBlockState(), false);
    }

    static final int MINE_Y = -54;

    /**
     * Branch mine carved into the natural deepslate below the slot. With the world seed known, Base Finder's
     * mined-ore check sees the ores the seed predicts in these tunnels missing. A ladder shaft leads down.
     */
    private static void mine(Builder b) {
        int floor = MINE_Y - b.y;
        if (floor > -12) return;   // needs real terrain well above the deepslate layer
        // main corridor along x, 2 high
        for (int x = -24; x <= 24; x++) { b.carve(x, floor, 0); b.carve(x, floor + 1, 0); }
        // side branches every 3 blocks, 14 long each way
        for (int x = -24; x <= 24; x += 3)
            for (int z = 1; z <= 14; z++) {
                b.carve(x, floor, z); b.carve(x, floor + 1, z);
                b.carve(x, floor, -z); b.carve(x, floor + 1, -z);
            }
        // ladder shaft from the surface
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        for (int yy = -1; yy >= floor; yy--) {
            b.set(0, yy, -1, Blocks.COBBLESTONE.defaultBlockState());
            b.set(0, yy, 0, ladder);
        }
        for (int x = -24; x <= 24; x += 9) b.set(x, floor, 1, Blocks.TORCH.defaultBlockState());
    }

    // ------------------------------------------------------------------ block writer

    private static final class Builder {
        final ServerLevel level;
        final int x, y, z;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Builder(ServerLevel level, int x, int y, int z) { this.level = level; this.x = x; this.y = y; this.z = z; }

        /** Flatten the slot: air above, a grass floor, and solid ground under it. */
        void prepare(int half) {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++) {
                    for (int dy = 0; dy <= CLEAR_HEIGHT; dy++) setIfDifferent(dx, dy, dz, air);
                    set(dx, -1, dz, Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int dy = -2; dy >= -4; dy--)
                        if (level.getBlockState(at(dx, dy, dz)).canBeReplaced()) set(dx, dy, dz, Blocks.DIRT.defaultBlockState());
                }
        }

        BlockPos at(int dx, int dy, int dz) { return pos.set(x + dx, y + dy, z + dz); }

        void set(int dx, int dy, int dz, BlockState state) {
            BlockPos p = at(dx, dy, dz);
            if (!level.isInWorldBounds(p)) return;
            level.setBlock(p, state, FLAGS);
        }

        private void setIfDifferent(int dx, int dy, int dz, BlockState state) {
            if (level.getBlockState(at(dx, dy, dz)) != state) set(dx, dy, dz, state);
        }

        /** Dig a block out of natural rock: leaves fluids and other air alone so the tunnel shape stays honest. */
        void carve(int dx, int dy, int dz) {
            BlockState s = level.getBlockState(at(dx, dy, dz));
            if (!s.isAir() && s.getFluidState().is(Fluids.EMPTY) && !s.is(Blocks.BEDROCK)) set(dx, dy, dz, Blocks.AIR.defaultBlockState());
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state, boolean hollow) {
            for (int dx = x0; dx <= x1; dx++)
                for (int dy = y0; dy <= y1; dy++)
                    for (int dz = z0; dz <= z1; dz++) {
                        boolean shell = dx == x0 || dx == x1 || dy == y0 || dy == y1 || dz == z0 || dz == z1;
                        if (hollow && !shell) continue;
                        set(dx, dy, dz, state);
                    }
        }

        void bed(int dx, int dy, int dz, Direction facing, Block bed) {
            BlockState foot = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                    .setValue(BlockStateProperties.BED_PART, BedPart.FOOT);
            set(dx, dy, dz, foot);
            set(dx + facing.getStepX(), dy, dz + facing.getStepZ(), foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
        }

        /** Put a few stacks into the container at this spot (spread over its slots). */
        void fill(int dx, int dy, int dz, Item[] items, int stacks) {
            if (!(level.getBlockEntity(at(dx, dy, dz)) instanceof Container c)) return;
            int size = c.getContainerSize();
            for (int i = 0; i < Math.min(stacks, size); i++) {
                Item item = items[i % items.length];
                c.setItem((i * 7) % size, new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), 1 + (i * 13) % 64)));
            }
            c.setChanged();
        }
    }
}
