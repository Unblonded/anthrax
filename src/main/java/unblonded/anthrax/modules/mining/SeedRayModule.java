package unblonded.anthrax.modules.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LevelHeightAccessor;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Seed-driven ore simulator using packet-edit's placement sequence and loaded chunk states. */
public final class SeedRayModule extends FeatureModule {
    public static final SeedRayModule INSTANCE = new SeedRayModule();
    private static final ExecutorService PREDICTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Anthrax seed ray predictor"); t.setDaemon(true); return t;
    });
    public record Marker(BlockPos pos, int color) {}
    private record Ore(String name, Setting.Bool enabled, int color) {}

    private final Setting.Text worldSeed = setting(Setting.Text.standalone("World seed", "", 32));
    private final Setting.Num radius = setting(Setting.Num.standalone("Prediction radius (chunks)", 4, 1, 10, 1));
    private final Setting.Mode renderMode = setting(Setting.Mode.standalone("Render mode", "Wire", "Wire", "Glow"));
    private final Setting.Bool deepslateOres = setting(Setting.Bool.standalone("Deepslate ores", true));
    private final List<Ore> ores = new ArrayList<>();
    private volatile List<Marker> markers = List.of();
    private final AtomicBoolean predicting = new AtomicBoolean();
    private final AtomicLong predictionGeneration = new AtomicLong();
    private int requestedChunkX = Integer.MIN_VALUE, requestedChunkZ = Integer.MIN_VALUE;
    private String requestedSeed = "";
    private String requestedDimension = "";
    private String requestedFilters = "";
    private String requestedChunkLayout = "";
    private String observedChunkLayout = "";
    private ClientLevel observedWorld;
    private long chunkLayoutChangedAt;
    private int requestedRadius = -1;
    private String observedPredictionKey = "";
    private ClientLevel predictionWorld;

    private SeedRayModule() {
        super("Seed-Ray", "Predict vanilla ore placements from the entered world seed", "Mining", false);
        addOre("Coal", 0xFF383838);
        addOre("Iron", 0xFFFFC18A);
        addOre("Gold", 0xFFFFD83D);
        addOre("Redstone", 0xFFFF4545);
        addOre("Diamond", 0xFF42F5E6);
        addOre("Lapis", 0xFF4169FF);
        addOre("Copper", 0xFFFF8A4C);
        addOre("Emerald", 0xFF43FF7A);
        addOre("Quartz", 0xFFFFF3E5);
        addOre("Ancient Debris", 0xFFFF6B3D);
    }

    private void addOre(String name, int color) {
        ores.add(new Ore(name, setting(Setting.Bool.standalone(name, true)), color));
    }

    @Override public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (observedWorld != null) predictionGeneration.incrementAndGet();
            markers = List.of();
            requestedChunkX = Integer.MIN_VALUE;
            observedChunkLayout = "";
            requestedChunkLayout = "";
            observedWorld = null;
            observedPredictionKey = "";
            predictionWorld = null;
            requestedChunkX = Integer.MIN_VALUE;
            return;
        }
        long seed;
        try { seed = Long.parseLong(worldSeed.get().trim()); }
        catch (NumberFormatException ignored) {
            markers = List.of();
            requestedSeed = "";
            if (!observedPredictionKey.isEmpty()) predictionGeneration.incrementAndGet();
            observedPredictionKey = "";
            return;
        }
        int chunkX = client.player.chunkPosition().x(), chunkZ = client.player.chunkPosition().z();
        int r = (int) radius.get();
        String dimension = client.level.dimension().identifier().toString();
        ClientLevel world = client.level;
        String loadedLayout = loadedChunkLayout(world, chunkX, chunkZ, r);
        long now = System.currentTimeMillis();
        if (world != observedWorld || !loadedLayout.equals(observedChunkLayout)) {
            observedWorld = world;
            observedChunkLayout = loadedLayout;
            chunkLayoutChangedAt = now;
        }
        String filters = ores.stream().map(o -> o.name + ":" + o.enabled.get()).reduce((a, b) -> a + ";" + b).orElse("")
                + ";deepslate:" + deepslateOres.get();
        String predictionKey = seed + "|" + chunkX + "|" + chunkZ + "|" + r + "|" + dimension + "|" + filters + "|" + loadedLayout;
        if (world != predictionWorld || !predictionKey.equals(observedPredictionKey)) {
            predictionWorld = world;
            observedPredictionKey = predictionKey;
            predictionGeneration.incrementAndGet();
            markers = List.of();
        }
        if (chunkX == requestedChunkX && chunkZ == requestedChunkZ && r == requestedRadius
                && dimension.equals(requestedDimension) && worldSeed.get().equals(requestedSeed)
                && filters.equals(requestedFilters) && loadedLayout.equals(requestedChunkLayout)) return;
        // Match packet-edit's chunk stability delay. The prediction reads live
        // block states, so wait for streamed chunks to finish arriving first.
        if (now - chunkLayoutChangedAt < 1000L) return;
        if (!predicting.compareAndSet(false, true)) return;

        Map<String, Integer> enabled = new HashMap<>();
        for (Ore ore : ores) if (ore.enabled.get()) enabled.put(ore.name, ore.color);
        if (enabled.isEmpty()) { markers = List.of(); predicting.set(false); return; }

        markers = List.of();
        requestedChunkX = chunkX; requestedChunkZ = chunkZ; requestedRadius = r;
        requestedDimension = dimension; requestedSeed = worldSeed.get(); requestedFilters = filters;
        requestedChunkLayout = loadedLayout;
        long generation = predictionGeneration.get();
        LevelHeightAccessor levelHeight = LevelHeightAccessor.create(world.getMinY(), world.getHeight());
        boolean deepslate = deepslateOres.get();
        PREDICTOR.execute(() -> {
            try {
                List<Marker> result = SeedOrePredictor.predict(world, levelHeight, seed, chunkX, chunkZ, r, dimension, enabled, deepslate);
                if (predictionGeneration.get() == generation && isEnabled()) markers = result;
            } catch (RuntimeException exception) {
                if (predictionGeneration.get() == generation) {
                    markers = List.of();
                    requestedChunkX = Integer.MIN_VALUE;
                    System.err.println("Anthrax Seed-Ray: prediction failed: " + exception.getMessage());
                }
            } finally { predicting.set(false); }
        });
    }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled) {
            predictionGeneration.incrementAndGet(); markers = List.of();
            requestedChunkX = Integer.MIN_VALUE;
        }
    }

    private static String loadedChunkLayout(ClientLevel world, int centerX, int centerZ, int radius) {
        StringBuilder layout = new StringBuilder((radius * 2 + 1) * (radius * 2 + 1));
        for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
            for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
                int dx = chunkX - centerX, dz = chunkZ - centerZ;
                if (dx * dx + dz * dz <= radius * radius) layout.append(world.hasChunk(chunkX, chunkZ) ? '1' : '0');
            }
        }
        return layout.toString();
    }

    public static void renderGizmos(LevelRenderer renderer) {
        SeedRayModule module = INSTANCE;
        if (!module.isEnabled() || module.markers.isEmpty()) return;
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            for (Marker marker : module.markers) {
                GizmoStyle style = module.renderMode.get().equals("Glow")
                        ? GizmoStyle.strokeAndFill(marker.color, 2.0f, (marker.color & 0x00FFFFFF) | 0x30000000)
                        : GizmoStyle.stroke(marker.color, 1.35f);
                Gizmos.cuboid(marker.pos, style).setAlwaysOnTop();
            }
        }
        renderer.addMainThreadGizmos(collector.drainGizmos());
    }

    static String oreName(Block block) {
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return "Coal";
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return "Iron";
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) return "Gold";
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return "Redstone";
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return "Diamond";
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return "Lapis";
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return "Copper";
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return "Emerald";
        if (block == Blocks.NETHER_QUARTZ_ORE) return "Quartz";
        if (block == Blocks.ANCIENT_DEBRIS) return "Ancient Debris";
        return null;
    }

    static boolean isDeepslate(Block block) {
        return block == Blocks.DEEPSLATE_COAL_ORE || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.DEEPSLATE_COPPER_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE;
    }
}
