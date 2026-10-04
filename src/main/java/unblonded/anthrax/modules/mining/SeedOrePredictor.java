package unblonded.anthrax.modules.mining;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Port of packet-edit 1.21.4's OreSimulator placement sequence. */
final class SeedOrePredictor {
    private static final double EPSILON = 1e-10;
    private static final HolderGetter.Provider WORLDGEN_REGISTRIES = VanillaRegistries.createLookup();

    private record OreData(PlacedFeature feature, int step, int index, IntProvider count,
                           HeightProvider height, float rarity, float discardOnAir,
                           int size, boolean scattered, String name, int color) {}

    private SeedOrePredictor() {}

    static List<SeedRayModule.Marker> predict(ClientLevel world, LevelHeightAccessor height,
                                               long seed, int centerX, int centerZ, int radius, String dimension,
                                               Map<String, Integer> enabled, boolean deepslate) {
        boolean nether = dimension.endsWith("the_nether");
        boolean overworld = dimension.endsWith("overworld");
        if (!nether && !overworld) return List.of();

        var noiseKey = nether ? NoiseGeneratorSettings.NETHER : NoiseGeneratorSettings.OVERWORLD;
        var registries = WORLDGEN_REGISTRIES;
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(noiseKey);
        var biomePresetKey = nether ? MultiNoiseBiomeSourceParameterLists.NETHER : MultiNoiseBiomeSourceParameterLists.OVERWORLD;
        var biomePreset = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(biomePresetKey);
        MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(biomePreset);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
        RandomState randomState = RandomState.create(registries, noiseKey, seed);
        WorldGenerationContext heightContext = new WorldGenerationContext(generator, height);

        List<FeatureSorter.StepFeatureData> featureSteps = FeatureSorter.buildFeaturesPerStep(
                new ArrayList<>(biomes.possibleBiomes()), b -> b.value().getGenerationSettings().features(), true);
        List<OreData> ores = collectOres(featureSteps, enabled);
        Map<BlockPos, Integer> hits = new LinkedHashMap<>();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));

        for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
            for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
                int dx = chunkX - centerX, dz = chunkZ - centerZ;
                if (dx * dx + dz * dz > radius * radius) continue;
                // This simulator intentionally reads the real client block states,
                // just like packet-edit. Don't treat unloaded chunks as empty terrain.
                if (!world.hasChunk(chunkX, chunkZ)) continue;

                int blockX = chunkX << 4;
                int blockZ = chunkZ << 4;
                long populationSeed = setPopulationSeed(random, seed, blockX, blockZ);
                for (OreData ore : ores) {
                    // ChunkRandom.setDecoratorSeed(populationSeed, index, step).
                    random.setSeed(populationSeed + ore.index + 10000L * ore.step);
                    int count = ore.count == null ? 1 : ore.count.sample(random);
                    for (int n = 0; n < count; n++) {
                        if (ore.rarity != 1.0F && random.nextFloat() >= 1.0F / ore.rarity) continue;
                        int x = random.nextInt(16) + blockX;
                        int z = random.nextInt(16) + blockZ;
                        int y = ore.height.sample(random, heightContext);
                        BlockPos origin = new BlockPos(x, y, z);
                        if (ore.scattered) {
                            generateHidden(world, random, origin, ore, hits, deepslate);
                        } else {
                            generateNormal(world, random, origin, ore, generator, randomState, height, hits, deepslate);
                        }
                    }
                }
            }
        }
        return toMarkers(hits);
    }

    private static long setPopulationSeed(WorldgenRandom random, long worldSeed, int blockX, int blockZ) {
        random.setSeed(worldSeed);
        long xMultiplier = random.nextLong() | 1L;
        long zMultiplier = random.nextLong() | 1L;
        long populationSeed = ((long) blockX * xMultiplier + (long) blockZ * zMultiplier) ^ worldSeed;
        random.setSeed(populationSeed);
        return populationSeed;
    }

    private static List<OreData> collectOres(List<FeatureSorter.StepFeatureData> steps, Map<String, Integer> enabled) {
        List<OreData> ores = new ArrayList<>();
        for (int step = 0; step < steps.size(); step++) {
            FeatureSorter.StepFeatureData stepData = steps.get(step);
            for (PlacedFeature placed : stepData.features()) {
                int index = stepData.indexMapping().applyAsInt(placed);
                if (index < 0) continue;
                var configured = placed.feature().value();
                if (!(configured.config() instanceof OreConfiguration config)) continue;
                if (configured.feature() != Feature.ORE && configured.feature() != Feature.SCATTERED_ORE) continue;

                String name = null;
                for (OreConfiguration.TargetBlockState target : config.targetStates) {
                    name = SeedRayModule.oreName(target.state.getBlock());
                    if (name != null) break;
                }
                Integer color = name == null ? null : enabled.get(name);
                if (color == null) continue;

                IntProvider count = null;
                HeightProvider oreHeight = null;
                float rarity = 1.0F;
                for (var modifier : placed.placement()) {
                    if (modifier instanceof CountPlacement placement) count = field(placement, "count", IntProvider.class);
                    else if (modifier instanceof HeightRangePlacement placement) oreHeight = field(placement, "height", HeightProvider.class);
                    else if (modifier instanceof RarityFilter placement) rarity = field(placement, "chance", Integer.class).floatValue();
                }
                if (oreHeight == null) continue;
                ores.add(new OreData(placed, step, index, count, oreHeight, rarity,
                        config.discardChanceOnAirExposure, config.size,
                        configured.feature() == Feature.SCATTERED_ORE, name, color));
            }
        }
        return ores;
    }

    private static void generateNormal(ClientLevel world, WorldgenRandom random, BlockPos origin, OreData ore,
                                       ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height,
                                       Map<BlockPos, Integer> hits, boolean deepslate) {
        float angle = random.nextFloat() * (float) Math.PI;
        float spread = (float) ore.size / 8.0F;
        int padding = (int) Math.ceil(((float) ore.size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double startX = origin.getX() + Math.sin(angle) * spread;
        double endX = origin.getX() - Math.sin(angle) * spread;
        double startZ = origin.getZ() + Math.cos(angle) * spread;
        double endZ = origin.getZ() - Math.cos(angle) * spread;
        double startY = origin.getY() + random.nextInt(3) - 2;
        double endY = origin.getY() + random.nextInt(3) - 2;
        int minX = origin.getX() - (int) Math.ceil(spread) - padding;
        int minY = origin.getY() - 2 - padding;
        int minZ = origin.getZ() - (int) Math.ceil(spread) - padding;
        int width = 2 * ((int) Math.ceil(spread) + padding);
        int heightSpan = 2 * (2 + padding);

        for (int x = minX; x <= minX + width; x++) {
            for (int z = minZ; z <= minZ + width; z++) {
                int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (minY <= topY) {
                    generateVeinPart(world, random, ore, hits, deepslate,
                            startX, endX, startZ, endZ, startY, endY,
                            minX, minY, minZ, width, heightSpan);
                    return;
                }
            }
        }
    }

    private static void generateVeinPart(ClientLevel world, WorldgenRandom random, OreData ore,
                                         Map<BlockPos, Integer> hits, boolean deepslate,
                                         double startX, double endX, double startZ, double endZ,
                                         double startY, double endY, int minX, int minY, int minZ,
                                         int width, int heightSpan) {
        int size = ore.size;
        BitSet visited = new BitSet(width * heightSpan * width);
        double[] spheres = new double[size * 4];
        for (int n = 0; n < size; n++) {
            float t = (float) n / size;
            double x = lerp(t, startX, endX);
            double y = lerp(t, startY, endY);
            double z = lerp(t, startZ, endZ);
            double scale = random.nextDouble() * size / 16.0D;
            // Match 1.21.4 MathHelper.sin(float) exactly; using Math.sin(double)
            // changes edge voxels in the generated vein spheres.
            double sphereRadius = ((double) (Mth.sin(3.1415927F * t) + 1.0F) * scale + 1.0D) / 2.0D;
            spheres[n * 4] = x;
            spheres[n * 4 + 1] = y;
            spheres[n * 4 + 2] = z;
            spheres[n * 4 + 3] = sphereRadius;
        }

        for (int n = 0; n < size - 1; n++) {
            if (spheres[n * 4 + 3] <= 0.0D) continue;
            for (int m = n + 1; m < size; m++) {
                if (spheres[m * 4 + 3] <= 0.0D) continue;
                double dx = spheres[n * 4] - spheres[m * 4];
                double dy = spheres[n * 4 + 1] - spheres[m * 4 + 1];
                double dz = spheres[n * 4 + 2] - spheres[m * 4 + 2];
                double dr = spheres[n * 4 + 3] - spheres[m * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) spheres[(dr > 0.0D ? m : n) * 4 + 3] = -1.0D;
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = 0; n < size; n++) {
            double radius = spheres[n * 4 + 3];
            if (radius < 0.0D) continue;
            double cx = spheres[n * 4], cy = spheres[n * 4 + 1], cz = spheres[n * 4 + 2];
            int x0 = Math.max((int) Math.floor(cx - radius), minX);
            int y0 = Math.max((int) Math.floor(cy - radius), minY);
            int z0 = Math.max((int) Math.floor(cz - radius), minZ);
            int x1 = Math.max((int) Math.floor(cx + radius), x0);
            int y1 = Math.max((int) Math.floor(cy + radius), y0);
            int z1 = Math.max((int) Math.floor(cz + radius), z0);
            for (int x = x0; x <= x1; x++) {
                double dx = (x + 0.5D - cx) / radius;
                if (dx * dx >= 1.0D) continue;
                for (int y = y0; y <= y1; y++) {
                    double dy = (y + 0.5D - cy) / radius;
                    if (dx * dx + dy * dy >= 1.0D) continue;
                    for (int z = z0; z <= z1; z++) {
                        double dz = (z + 0.5D - cz) / radius;
                        if (dx * dx + dy * dy + dz * dz >= 1.0D + EPSILON) continue;
                        int index = x - minX + (y - minY) * width + (z - minZ) * width * heightSpan;
                        if (visited.get(index)) continue;
                        visited.set(index);
                        pos.set(x, y, z);
                        if (y < world.getMinY() || y >= world.getMaxY() || !opaque(world, pos)) continue;
                        if (!shouldPlace(world, pos, ore.discardOnAir, random)) continue;
                        addHit(world, pos, ore, hits, deepslate);
                    }
                }
            }
        }
    }

    private static void generateHidden(ClientLevel world, WorldgenRandom random, BlockPos origin, OreData ore,
                                       Map<BlockPos, Integer> hits, boolean deepslate) {
        int count = random.nextInt(ore.size + 1);
        for (int j = 0; j < count; j++) {
            int spread = Math.min(j, 7);
            int x = randomCoord(random, spread) + origin.getX();
            int y = randomCoord(random, spread) + origin.getY();
            int z = randomCoord(random, spread) + origin.getZ();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
            if (opaque(world, pos) && shouldPlace(world, pos, 1.0F, random))
                addHit(world, pos, ore, hits, deepslate);
        }
    }

    private static boolean shouldPlace(ClientLevel world, BlockPos pos, float discardOnAir, WorldgenRandom random) {
        if (discardOnAir == 0.0F || (discardOnAir != 1.0F && random.nextFloat() >= discardOnAir)) return true;
        for (var direction : net.minecraft.core.Direction.values()) {
            if (!opaque(world, pos.relative(direction)) && discardOnAir != 1.0F) return false;
        }
        return true;
    }

    private static boolean opaque(ClientLevel world, BlockPos pos) {
        return world.getBlockState(pos).isSolidRender();
    }

    private static void addHit(ClientLevel world, BlockPos pos, OreData ore, Map<BlockPos, Integer> hits, boolean deepslate) {
        if (!deepslate && world.getBlockState(pos).getBlock() == Blocks.DEEPSLATE) return;
        hits.put(pos.immutable(), ore.color);
    }

    private static int randomCoord(WorldgenRandom random, int spread) {
        return Math.round((random.nextFloat() - random.nextFloat()) * spread);
    }

    private static List<SeedRayModule.Marker> toMarkers(Map<BlockPos, Integer> hits) {
        return hits.entrySet().stream().map(e -> new SeedRayModule.Marker(e.getKey(), e.getValue())).toList();
    }

    private static double lerp(double delta, double start, double end) { return start + delta * (end - start); }

    private static <T> T field(Object instance, String name, Class<T> type) {
        try {
            Field field = null;
            for (Class<?> parent = instance.getClass(); parent != null && field == null; parent = parent.getSuperclass()) {
                try { field = parent.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
            }
            if (field == null) throw new NoSuchFieldException(name);
            field.setAccessible(true);
            return type.cast(field.get(instance));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read vanilla placement setting " + name, exception);
        }
    }
}
