package unblonded.anthrax.modules.mining;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Exact re-run of vanilla ore generation (26.2) from the world seed.
 *
 * <p>Per chunk, vanilla seeds a decoration random from the world seed and chunk origin, then for every
 * placed feature of every decoration step reseeds it with (decoration seed + feature index + 10000 *
 * step) and runs the feature's placement modifiers (count, in-square, height range, rarity, biome
 * filter) followed by the ore feature itself. This class does the same with vanilla's own modifier
 * code, and a line-by-line port of {@code OreFeature} / {@code ScatteredOreFeature}. The big copper and
 * iron veins, which come from terrain noise rather than features, are evaluated by {@link OreVeinSim}.
 *
 * <p>Ore placement depends on what the blocks were at generation time (an ore only replaces its target
 * stone, and the random draws for the air-exposure check only happen when that test passes), so that
 * state is rebuilt from the loaded chunks; see {@link Run#genState}. Fake ores from anti-xray are turned
 * back into stone the same way, so prediction works through it.
 */
final class SeedOrePredictor {
    private static final HolderGetter.Provider REGISTRIES = VanillaRegistries.createLookup();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** One ore feature in vanilla feature order. */
    private record OreData(PlacedFeature feature, int step, int index, OreConfiguration config, boolean scattered) {}

    /** A predicted ore block: where it is and what vanilla put there. */
    record Hit(BlockPos pos, BlockState state) {}

    /** Generator, feature list and ore base states for one dimension (they never change). */
    private record Setup(NoiseBasedChunkGenerator generator, List<OreData> ores, Map<Block, BlockState> productBase,
                         Map<ResourceKey<Biome>, Holder<Biome>> biomes) {}

    private static final Map<String, Setup> SETUPS = new HashMap<>();
    private static long veinSeed;
    private static OreVeinSim veinSim;

    private static synchronized OreVeinSim veins(long seed) {
        if (veinSim == null || veinSeed != seed) { veinSim = new OreVeinSim(REGISTRIES, seed); veinSeed = seed; }
        return veinSim;
    }

    private SeedOrePredictor() {}

    // ------------------------------------------------------------------ snapshot
    /**
     * Immutable copy of the loaded chunks (blocks and biomes), taken on the client thread so the
     * prediction can run on a worker without racing chunk updates.
     */
    static final class Snapshot {
        final Map<Long, LevelChunkSection[]> chunks = new HashMap<>();
        final int minY, height, minSectionY;
        final BiomeManager biomes;
        final boolean nether;

        Snapshot(ClientLevel level, int centerX, int centerZ, int radius) {
            minY = level.getMinY();
            height = level.getHeight();
            minSectionY = level.getMinSectionY();
            nether = level.dimension().identifier().getPath().equals("the_nether");
            for (int cx = centerX - radius - 1; cx <= centerX + radius + 1; cx++)
                for (int cz = centerZ - radius - 1; cz <= centerZ + radius + 1; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    LevelChunkSection[] live = chunk.getSections(), copy = new LevelChunkSection[live.length];
                    for (int i = 0; i < live.length; i++) if (live[i] != null) copy[i] = live[i].copy();
                    chunks.put(ChunkPos.pack(cx, cz), copy);
                }
            biomes = level.getBiomeManager().withDifferentSource(this::noiseBiome);
        }

        boolean has(int cx, int cz) { return chunks.containsKey(ChunkPos.pack(cx, cz)); }

        /** True when the chunk and all 8 neighbours are loaded (veins cross chunk borders). */
        boolean hasArea(int cx, int cz) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (!has(cx + dx, cz + dz)) return false;
            return true;
        }

        BlockState get(int x, int y, int z) {
            if (y < minY || y >= minY + height) return AIR;
            LevelChunkSection[] sections = chunks.get(ChunkPos.pack(x >> 4, z >> 4));
            if (sections == null) return AIR;
            LevelChunkSection s = sections[(y >> 4) - minSectionY];
            return s == null ? AIR : s.getBlockState(x & 15, y & 15, z & 15);
        }

        private Holder<Biome> noiseBiome(int qx, int qy, int qz) {
            int minQ = QuartPos.fromBlock(minY), maxQ = minQ + QuartPos.fromBlock(height) - 1;
            qy = Mth.clamp(qy, minQ, maxQ);
            LevelChunkSection[] sections = chunks.get(ChunkPos.pack(QuartPos.toSection(qx), QuartPos.toSection(qz)));
            if (sections == null) {
                // outside the snapshot: use any loaded column so the biome zoom never fails
                for (LevelChunkSection[] any : chunks.values()) { sections = any; break; }
            }
            LevelChunkSection s = sections[QuartPos.toSection(qy) - minSectionY];
            return s.getNoiseBiome(qx & 3, qy & 3, qz & 3);
        }
    }

    // ------------------------------------------------------------------ setup
    private static synchronized Setup setup(boolean nether) {
        return SETUPS.computeIfAbsent(nether ? "nether" : "overworld", k -> {
            var noiseKey = nether ? NoiseGeneratorSettings.NETHER : NoiseGeneratorSettings.OVERWORLD;
            var settings = REGISTRIES.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(noiseKey);
            var presetKey = nether ? MultiNoiseBiomeSourceParameterLists.NETHER : MultiNoiseBiomeSourceParameterLists.OVERWORLD;
            var preset = REGISTRIES.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(presetKey);
            MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(preset);
            NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(source, settings);
            // Same call vanilla makes in the ChunkGenerator constructor, so feature indices match the server.
            List<FeatureSorter.StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(
                    List.copyOf(source.possibleBiomes()), b -> b.value().getGenerationSettings().features(), true);
            List<OreData> ores = new ArrayList<>();
            Map<Block, BlockState> productBase = new IdentityHashMap<>();
            for (int step = 0; step < steps.size(); step++) {
                List<PlacedFeature> features = steps.get(step).features();
                for (int index = 0; index < features.size(); index++) {
                    PlacedFeature placed = features.get(index);
                    var configured = placed.feature().value();
                    if (configured.feature() != Feature.ORE && configured.feature() != Feature.SCATTERED_ORE) continue;
                    OreConfiguration config = (OreConfiguration) configured.config();
                    ores.add(new OreData(placed, step, index, config, configured.feature() == Feature.SCATTERED_ORE));
                    for (OreConfiguration.TargetBlockState t : config.targetStates) {
                        Block product = t.state.getBlock();
                        if (SeedRayModule.oreName(product) == null && !product.getDescriptionId().contains("infested")) continue;
                        BlockState base = baseFor(t);
                        if (base != null) productBase.putIfAbsent(product, base);
                    }
                }
            }
            return new Setup(generator, ores, productBase, new java.util.concurrent.ConcurrentHashMap<>());
        });
    }

    /** A plain block the target rule accepts: what an ore block was before the ore replaced it. */
    private static BlockState baseFor(OreConfiguration.TargetBlockState target) {
        RandomSource probe = RandomSource.create(0);
        for (Block b : new Block[]{Blocks.STONE, Blocks.DEEPSLATE, Blocks.NETHERRACK, Blocks.TUFF, Blocks.GRANITE,
                Blocks.DIORITE, Blocks.ANDESITE, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.END_STONE})
            if (target.target.test(b.defaultBlockState(), probe)) return b.defaultBlockState();
        return null;
    }

    // ------------------------------------------------------------------ prediction
    /**
     * Ores of every type placed in the given chunks (chunks without all 8 neighbours loaded are skipped).
     *
     * <p>Decoration order between neighbouring chunks matters at their borders: if the neighbour was
     * decorated first, its ores were already there. Pass 1 simulates every chunk on its own; where two
     * chunks' ore veins claim the same block, the block in the world shows which chunk went first. Later
     * passes re-simulate with the earlier neighbours' ores in place.
     */
    static List<Hit> predict(Snapshot snap, long seed, List<int[]> chunks) {
        Setup setup = setup(snap.nether);
        Run run = new Run(snap, setup, snap.nether ? null : veins(seed), seed);
        List<int[]> todo = new ArrayList<>();
        for (int[] c : chunks) if (snap.hasArea(c[0], c[1])) todo.add(c);

        Map<Long, Long2ObjectOpenHashMap<BlockState>> placed = new HashMap<>();
        for (int[] c : todo) placed.put(ChunkPos.pack(c[0], c[1]), run.simulate(c[0], c[1], List.of()));
        // Anti-xray: if the world plainly disagrees with the prediction, the server is sending fake blocks.
        // Re-run treating every buried block anti-xray could have faked as plain stone, and skip the
        // ordering passes (they read ores from the world, which can no longer be trusted).
        boolean antiXray = agreement(snap, placed) < 0.6;
        lastAntiXray = antiXray;
        if (antiXray) {
            run.antiXray = true;
            for (int[] c : todo) placed.put(ChunkPos.pack(c[0], c[1]), run.simulate(c[0], c[1], List.of()));
        }
        Map<Long, List<Long>> earlier = Map.of();
        for (int pass = 0; pass < 2 && !antiXray; pass++) {
            earlier = order(snap, placed);
            if (earlier.isEmpty()) break;
            Map<Long, Long2ObjectOpenHashMap<BlockState>> next = new HashMap<>();
            for (int[] c : todo) {
                long key = ChunkPos.pack(c[0], c[1]);
                List<Long2ObjectOpenHashMap<BlockState>> before = new ArrayList<>();
                for (long n : earlier.getOrDefault(key, List.of())) before.add(placed.get(n));
                next.put(key, before.isEmpty() ? placed.get(key) : run.simulate(c[0], c[1], before));
            }
            placed = next;
        }
        if (!antiXray) earlier = refineOrder(run, snap, todo, placed, earlier);

        // Merge the chunks' ores. Stone blobs (granite, tuff...) never hide an ore: ores replace them, and
        // they cannot replace ores. When two chunks put different ores on one block, the chunk decorated
        // first wins (ores never replace each other).
        Map<BlockPos, Hit> hits = new LinkedHashMap<>();
        if (run.veins != null) for (int[] c : todo) run.veinOres(c[0], c[1], hits);
        Map<BlockPos, Long> owner = new HashMap<>();
        for (int[] c : todo) {
            long key = ChunkPos.pack(c[0], c[1]);
            for (var e : placed.get(key).long2ObjectEntrySet()) {
                if (SeedRayModule.oreName(e.getValue().getBlock()) == null) continue;
                BlockPos p = BlockPos.of(e.getLongKey());
                // cave decoration later covered this ore with moss, clay, dripstone...: it is gone
                if (LATE_REPLACED.contains(snap.get(p.getX(), p.getY(), p.getZ()).getBlock())) continue;
                Long other = owner.get(p);
                Hit existing = hits.get(p);
                if (existing != null && (other == null || !earlier.getOrDefault(other, List.of()).contains(key))) continue;
                hits.put(p, new Hit(p, e.getValue()));
                owner.put(p, key);
            }
        }
        return new ArrayList<>(hits.values());
    }

    /** Whether the last prediction ran in anti-xray mode. */
    static volatile boolean lastAntiXray;

    /** Fraction of predicted ores that are in the world as predicted (1 with nothing to compare). */
    private static double agreement(Snapshot snap, Map<Long, Long2ObjectOpenHashMap<BlockState>> placed) {
        int total = 0, same = 0;
        for (Long2ObjectOpenHashMap<BlockState> m : placed.values())
            for (var e : m.long2ObjectEntrySet()) {
                Block b = e.getValue().getBlock();
                if (SeedRayModule.oreName(b) == null) continue;
                BlockPos p = BlockPos.of(e.getLongKey());
                total++;
                if (snap.get(p.getX(), p.getY(), p.getZ()).getBlock() == b) same++;
            }
        return total < 40 ? 1 : same / (double) total;
    }

    /**
     * Blocks anti-xray plugins commonly fake inside solid rock (Paper's hidden and replacement block
     * lists): ores are handled through {@code productBase}; these are the rest.
     */
    private static final Set<Block> PLAIN = Set.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.NETHERRACK);
    private static final Set<Block> FAKEABLE = Set.of(Blocks.MAGMA_BLOCK, Blocks.GLOWSTONE, Blocks.BONE_BLOCK,
            Blocks.NETHER_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.OAK_PLANKS,
            Blocks.MOSSY_COBBLESTONE, Blocks.OBSIDIAN, Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.CLAY,
            Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.SPAWNER);

    /**
     * Neighbours whose veins touch the same blocks with the same ore leave no visible clue about order,
     * yet order still changes the random sequence (the ore test fails on an ore that is already there).
     * For those, try the neighbour as decorated-first and keep it if the chunk's predicted ores match the
     * world better.
     */
    private static Map<Long, List<Long>> refineOrder(Run run, Snapshot snap, List<int[]> todo,
                                                     Map<Long, Long2ObjectOpenHashMap<BlockState>> placed, Map<Long, List<Long>> earlier) {
        Map<Long, List<Long>> result = new HashMap<>();
        earlier.forEach((k, v) -> result.put(k, new ArrayList<>(v)));
        for (int[] c : todo) {
            long key = ChunkPos.pack(c[0], c[1]);
            Long2ObjectOpenHashMap<BlockState> own = placed.get(key);
            List<Long> order = result.computeIfAbsent(key, k -> new ArrayList<>());
            int best = score(snap, own);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    long n = ChunkPos.pack(c[0] + dx, c[1] + dz);
                    Long2ObjectOpenHashMap<BlockState> other = placed.get(n);
                    if (n == key || other == null || order.contains(n) || result.getOrDefault(n, List.of()).contains(key)) continue;
                    boolean touches = false;
                    for (var e : own.long2ObjectEntrySet()) {
                        BlockState o = other.get(e.getLongKey());
                        if (o != null && SeedRayModule.oreName(o.getBlock()) != null) { touches = true; break; }
                    }
                    if (!touches) continue;
                    List<Long2ObjectOpenHashMap<BlockState>> before = new ArrayList<>();
                    for (long e : order) before.add(placed.get(e));
                    before.add(other);
                    Long2ObjectOpenHashMap<BlockState> trial = run.simulate(c[0], c[1], before);
                    int s = score(snap, trial);
                    if (s > best) { best = s; own = trial; order.add(n); placed.put(key, trial); }
                }
        }
        return result;
    }

    /** How well a chunk's predicted ores match the loaded world (+1 per ore found, -1 per ore that is not there). */
    private static int score(Snapshot snap, Long2ObjectOpenHashMap<BlockState> placed) {
        int s = 0;
        for (var e : placed.long2ObjectEntrySet()) {
            Block b = e.getValue().getBlock();
            if (SeedRayModule.oreName(b) == null) continue;
            BlockPos p = BlockPos.of(e.getLongKey());
            s += snap.get(p.getX(), p.getY(), p.getZ()).getBlock() == b ? 1 : -1;
        }
        return s;
    }

    /**
     * For every chunk, the neighbours that were decorated before it. Evidence: a block both
     * chunks' veins wanted with different ores; the ore that is there now was placed first (ores never
     * replace each other).
     */
    private static Map<Long, List<Long>> order(Snapshot snap, Map<Long, Long2ObjectOpenHashMap<BlockState>> placed) {
        Map<Long, List<Long>> earlier = new HashMap<>();
        for (var entry : placed.entrySet()) {
            long a = entry.getKey();
            int ax = ChunkPos.getX(a), az = ChunkPos.getZ(a);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    long b = ChunkPos.pack(ax + dx, az + dz);
                    Long2ObjectOpenHashMap<BlockState> mapB = placed.get(b);
                    if (mapB == null) continue;
                    int bFirst = 0, aFirst = 0;
                    for (var e : entry.getValue().long2ObjectEntrySet()) {
                        BlockState sa = e.getValue(), sb = mapB.get(e.getLongKey());
                        if (sb == null || sa.getBlock() == sb.getBlock()) continue;
                        if (SeedRayModule.oreName(sa.getBlock()) == null || SeedRayModule.oreName(sb.getBlock()) == null) continue;
                        BlockPos p = BlockPos.of(e.getLongKey());
                        Block now = snap.get(p.getX(), p.getY(), p.getZ()).getBlock();
                        if (now == sb.getBlock()) bFirst++;
                        else if (now == sa.getBlock()) aFirst++;
                    }
                    if (bFirst > aFirst) earlier.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
                }
        }
        return earlier;
    }

    /** Blocks that cave decoration lays over stone after the ore step (lush caves, dripstone caves, deep dark). */
    private static final Set<Block> LATE_REPLACED = Set.of(Blocks.MOSS_BLOCK, Blocks.CLAY, Blocks.DRIPSTONE_BLOCK,
            Blocks.ROOTED_DIRT, Blocks.SCULK);

    /** Blocks that vegetation and top-layer features add after the ore step (they were not in the heightmap yet). */
    private static final Set<Block> LATER_ON_TOP = Set.of(Blocks.CACTUS, Blocks.PUMPKIN, Blocks.MELON,
            Blocks.BAMBOO, Blocks.BIG_DRIPLEAF, Blocks.BIG_DRIPLEAF_STEM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK,
            Blocks.MUSHROOM_STEM, Blocks.MANGROVE_ROOTS, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.COCOA, Blocks.BEE_NEST, Blocks.AZALEA,
            Blocks.FLOWERING_AZALEA, Blocks.CREAKING_HEART);

    private static boolean placedLater(Snapshot snap, BlockState s, int x, int y, int z) {
        if (s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.LOGS)) return true;   // trees
        if (s.getBlock() == Blocks.ICE) return !snap.get(x, y - 1, z).getFluidState().isEmpty();   // frozen water, not peak ice
        return LATER_ON_TOP.contains(s.getBlock());
    }

    /** One prediction: the snapshot plus everything derived from the seed. */
    private static final class Run {
        final Snapshot snap;
        final Setup setup;
        final OreVeinSim veins;
        final long seed;
        final WorldGenLevel level;
        final WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        final Map<Long, Integer> floor = new HashMap<>();
        Long2ObjectOpenHashMap<BlockState> placed;
        List<Long2ObjectOpenHashMap<BlockState>> neighbours;
        boolean antiXray;

        Run(Snapshot snap, Setup setup, OreVeinSim veins, long seed) {
            this.snap = snap;
            this.setup = setup;
            this.veins = veins;
            this.seed = seed;
            this.level = proxyLevel(snap, setup);
        }

        /** Runs every ore feature of one chunk, in vanilla order, and returns what it placed. */
        Long2ObjectOpenHashMap<BlockState> simulate(int cx, int cz, List<Long2ObjectOpenHashMap<BlockState>> earlierNeighbours) {
            placed = new Long2ObjectOpenHashMap<>();
            neighbours = earlierNeighbours;
            BlockPos origin = new BlockPos(cx << 4, snap.minSectionY << 4, cz << 4);
            long decorationSeed = random.setDecorationSeed(seed, origin.getX(), origin.getZ());
            for (OreData ore : setup.ores) {
                random.setFeatureSeed(decorationSeed, ore.index, ore.step);
                PlacementContext context = new PlacementContext(level, setup.generator, Optional.of(ore.feature));
                Stream<BlockPos> positions = Stream.of(origin);
                for (PlacementModifier modifier : ore.feature.placement())
                    positions = positions.flatMap(p -> modifier.getPositions(context, random, p));
                positions.forEach(p -> {
                    if (ore.scattered) scattered(p, ore.config);
                    else normal(p, ore.config);
                });
            }
            return placed;
        }

        /** Ore blocks of the terrain veins in one chunk (they exist before any feature runs). */
        void veinOres(int cx, int cz, Map<BlockPos, Hit> hits) {
            for (int y = -60; y <= 50; y++)
                for (int x = cx << 4; x < (cx << 4) + 16; x++)
                    for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                        BlockState now = snap.get(x, y, z);
                        if (now.isAir() || !now.getFluidState().isEmpty()) continue;
                        BlockState v = veins.at(x, y, z);
                        if (v != null && SeedRayModule.oreName(v.getBlock()) != null) {
                            BlockPos p = new BlockPos(x, y, z);
                            hits.put(p, new Hit(p, v));
                        }
                    }
        }

        /**
         * The block as it was when this feature ran. Ores this chunk placed earlier, and ores of
         * neighbours decorated earlier, were there. Any other ore block was either part of a terrain vein
         * (there from the start), placed later, or faked by anti-xray (both: still the stone it replaced).
         * Moss, clay, dripstone, roots and sculk deep underground were laid over stone or deepslate later.
         */
        BlockState genState(int x, int y, int z) {
            long key = BlockPos.asLong(x, y, z);
            BlockState own = placed.get(key);
            if (own != null) return own;
            for (Long2ObjectOpenHashMap<BlockState> n : neighbours) {
                BlockState s = n.get(key);
                if (s != null) return s;
            }
            BlockState now = snap.get(x, y, z);
            // anti-xray only fakes blocks you cannot see, i.e. blocks not touching air
            // (and hides ores, raw ore blocks and deepslate variants behind plain stone, so that is rebuilt too)
            if (antiXray && (FAKEABLE.contains(now.getBlock()) || setup.productBase.containsKey(now.getBlock()) || PLAIN.contains(now.getBlock()))
                    && !exposed(x, y, z))
                return plainRock(x, y, z);
            BlockState base = setup.productBase.get(now.getBlock());
            if (base != null) {
                BlockState vein = veins == null ? null : veins.at(x, y, z);
                return vein != null ? vein : base;
            }
            if (veins != null && LATE_REPLACED.contains(now.getBlock()) && y < oceanFloor(x, z) - 4) {
                BlockState vein = veins.at(x, y, z);
                if (vein != null) return vein;
                return (veins.deepslate(x, y, z) ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
            }
            return now;
        }

        private boolean exposed(int x, int y, int z) {
            for (Direction d : Direction.values()) {
                BlockState n = snap.get(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                if (n.isAir() || !n.getFluidState().isEmpty()) return true;
            }
            return false;
        }

        /** What terrain was here before features: a terrain vein block, deepslate or stone, or netherrack. */
        private BlockState plainRock(int x, int y, int z) {
            if (snap.nether) return Blocks.NETHERRACK.defaultBlockState();
            BlockState vein = veins.at(x, y, z);
            if (vein != null) return vein;
            return (veins.deepslate(x, y, z) ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
        }

        /** OCEAN_FLOOR_WG at decoration time: the top terrain block, ignoring fluids and anything placed later. */
        int oceanFloor(int x, int z) {
            return floor.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> {
                for (int y = snap.minY + snap.height - 1; y >= snap.minY; y--) {
                    BlockState s = snap.get(x, y, z);
                    if (s.isAir() || !s.getFluidState().isEmpty() || !s.blocksMotion() || placedLater(snap, s, x, y, z)) continue;
                    return y + 1;   // heightmaps store the first free y above the block
                }
                return snap.minY;
            });
        }

        // ---- OreFeature.place / doPlace (26.2)
        void normal(BlockPos origin, OreConfiguration config) {
            float angle = random.nextFloat() * (float) Math.PI;
            float spread = (float) config.size / 8.0F;
            int padding = Mth.ceil(((float) config.size / 16.0F * 2.0F + 1.0F) / 2.0F);
            double x0 = origin.getX() + Math.sin(angle) * spread, x1 = origin.getX() - Math.sin(angle) * spread;
            double z0 = origin.getZ() + Math.cos(angle) * spread, z1 = origin.getZ() - Math.cos(angle) * spread;
            double y0 = origin.getY() + random.nextInt(3) - 2, y1 = origin.getY() + random.nextInt(3) - 2;
            int minX = origin.getX() - Mth.ceil(spread) - padding;
            int minY = origin.getY() - 2 - padding;
            int minZ = origin.getZ() - Mth.ceil(spread) - padding;
            int width = 2 * (Mth.ceil(spread) + padding);
            int heightSpan = 2 * (2 + padding);
            for (int x = minX; x <= minX + width; x++)
                for (int z = minZ; z <= minZ + width; z++)
                    if (minY <= oceanFloor(x, z)) {
                        doPlace(config, x0, x1, z0, z1, y0, y1, minX, minY, minZ, width, heightSpan);
                        return;
                    }
        }

        void doPlace(OreConfiguration config, double x0, double x1, double z0, double z1, double y0, double y1,
                     int minX, int minY, int minZ, int width, int heightSpan) {
            int size = config.size;
            BitSet visited = new BitSet(width * heightSpan * width);
            double[] s = new double[size * 4];
            for (int i = 0; i < size; i++) {
                float t = (float) i / (float) size;
                s[i * 4] = Mth.lerp((double) t, x0, x1);
                s[i * 4 + 1] = Mth.lerp((double) t, y0, y1);
                s[i * 4 + 2] = Mth.lerp((double) t, z0, z1);
                double scale = random.nextDouble() * (double) size / 16.0;
                s[i * 4 + 3] = ((double) (Mth.sin((float) Math.PI * t) + 1.0F) * scale + 1.0) / 2.0;
            }
            for (int i = 0; i < size - 1; i++) {
                if (s[i * 4 + 3] <= 0.0) continue;
                for (int j = i + 1; j < size; j++) {
                    if (s[j * 4 + 3] <= 0.0) continue;
                    double dx = s[i * 4] - s[j * 4], dy = s[i * 4 + 1] - s[j * 4 + 1], dz = s[i * 4 + 2] - s[j * 4 + 2];
                    double dr = s[i * 4 + 3] - s[j * 4 + 3];
                    if (dr * dr > dx * dx + dy * dy + dz * dz) {
                        if (dr > 0.0) s[j * 4 + 3] = -1.0; else s[i * 4 + 3] = -1.0;
                    }
                }
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < size; i++) {
                double r = s[i * 4 + 3];
                if (r < 0.0) continue;
                double cx = s[i * 4], cy = s[i * 4 + 1], cz = s[i * 4 + 2];
                int ax = Math.max(Mth.floor(cx - r), minX), ay = Math.max(Mth.floor(cy - r), minY), az = Math.max(Mth.floor(cz - r), minZ);
                int bx = Math.max(Mth.floor(cx + r), ax), by = Math.max(Mth.floor(cy + r), ay), bz = Math.max(Mth.floor(cz + r), az);
                for (int x = ax; x <= bx; x++) {
                    double dx = ((double) x + 0.5 - cx) / r;
                    if (dx * dx >= 1.0) continue;
                    for (int y = ay; y <= by; y++) {
                        double dy = ((double) y + 0.5 - cy) / r;
                        if (dx * dx + dy * dy >= 1.0) continue;
                        for (int z = az; z <= bz; z++) {
                            double dz = ((double) z + 0.5 - cz) / r;
                            if (dx * dx + dy * dy + dz * dz >= 1.0 || y < snap.minY || y >= snap.minY + snap.height) continue;
                            int bit = x - minX + (y - minY) * width + (z - minZ) * width * heightSpan;
                            if (visited.get(bit)) continue;
                            visited.set(bit);
                            pos.set(x, y, z);
                            BlockState state = genState(x, y, z);
                            for (OreConfiguration.TargetBlockState target : config.targetStates) {
                                if (canPlaceOre(state, config, target, pos)) {
                                    placed.put(pos.asLong(), target.state);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- ScatteredOreFeature.place (26.2)
        void scattered(BlockPos origin, OreConfiguration config) {
            int count = random.nextInt(config.size + 1);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int j = 0; j < count; j++) {
                int spread = Math.min(j, 7);
                int dx = Math.round((random.nextFloat() - random.nextFloat()) * (float) spread);
                int dy = Math.round((random.nextFloat() - random.nextFloat()) * (float) spread);
                int dz = Math.round((random.nextFloat() - random.nextFloat()) * (float) spread);
                pos.setWithOffset(origin, dx, dy, dz);
                BlockState state = genState(pos.getX(), pos.getY(), pos.getZ());
                for (OreConfiguration.TargetBlockState target : config.targetStates) {
                    if (canPlaceOre(state, config, target, pos)) {
                        placed.put(pos.asLong(), target.state);
                        break;
                    }
                }
            }
        }

        // ---- OreFeature.canPlaceOre / shouldSkipAirCheck / isAdjacentToAir
        boolean canPlaceOre(BlockState state, OreConfiguration config, OreConfiguration.TargetBlockState target, BlockPos pos) {
            if (!target.target.test(state, random)) return false;
            float chance = config.discardChanceOnAirExposure;
            if (chance <= 0.0F || (chance < 1.0F && random.nextFloat() >= chance)) return true;
            for (Direction d : Direction.values())
                if (genState(pos.getX() + d.getStepX(), pos.getY() + d.getStepY(), pos.getZ() + d.getStepZ()).isAir()) return false;
            return true;
        }
    }

    /**
     * The modifiers only ask the level for its height range and for biomes (biome filter). Biomes come
     * from the client's synced biome data, mapped to vanilla's own biome definitions (the synced copies
     * carry no feature lists).
     */
    private static WorldGenLevel proxyLevel(Snapshot snap, Setup setup) {
        return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMinY" -> snap.minY;
                    case "getHeight" -> {
                        if (args == null || args.length == 0) yield snap.height;
                        throw new UnsupportedOperationException("getHeight(" + args.length + ")");
                    }
                    case "getMaxY" -> snap.minY + snap.height - 1;
                    case "getMinSectionY" -> snap.minSectionY;
                    case "getBiome" -> {
                        Holder<Biome> client = snap.biomes.getBiome((BlockPos) args[0]);
                        Holder<Biome> vanilla = client.unwrapKey().map(key -> setup.biomes.computeIfAbsent(key,
                                k2 -> REGISTRIES.lookupOrThrow(Registries.BIOME).get(k2).orElse(null))).orElse(null);
                        if (vanilla == null) throw new IllegalStateException("Unknown biome " + client);
                        yield vanilla;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "SeedRayLevel";
                    default -> throw new UnsupportedOperationException("Seed-Ray level: " + method.getName());
                });
    }
}
