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
import net.minecraft.world.level.block.state.BlockState;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Seed-based ore X-ray: re-runs vanilla ore generation from the world seed (see {@link SeedOrePredictor})
 * and outlines every ore it places in the loaded chunks around you. Works through anti-xray, since fake
 * ores are turned back into stone before simulating. "Verify accuracy" compares the prediction against
 * the real blocks in loaded chunks (on servers without anti-xray), so you can confirm the seed is right.
 */
public final class SeedRayModule extends FeatureModule {
    public static final SeedRayModule INSTANCE = new SeedRayModule();
    private static final ExecutorService PREDICTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Anthrax seed ray predictor"); t.setDaemon(true); return t;
    });
    public record Marker(BlockPos pos, int color) {}
    private record Ore(String name, Setting.Bool enabled, int color) {}
    /** Result of a verification run. */
    public record Accuracy(int predicted, int actual, int matched, int chunks) {
        public double precision() { return predicted == 0 ? 1 : matched / (double) predicted; }
        public double recall() { return actual == 0 ? 1 : matched / (double) actual; }
    }

    private final Setting.Text worldSeed = setting(Setting.Text.standalone("World seed", "", 32), "The server's world seed. In singleplayer it is filled in automatically.");
    private final Setting.Num radius = setting(Setting.Num.standalone("Prediction radius (chunks)", 4, 1, 10, 1), "How far around you ore positions are predicted.");
    private final Setting.Mode renderMode = setting(Setting.Mode.standalone("Render mode", "Wire", "Wire", "Glow"));
    private final Setting.Bool deepslateOres = setting(Setting.Bool.standalone("Deepslate ores", true));
    private final Setting.Bool hideMined = setting(Setting.Bool.standalone("Hide mined ores", true), "Hide predicted ores whose block is already gone.");
    private final Setting.Action verify = setting(new Setting.Action("Verify accuracy", () -> INSTANCE.verifyRequested = true));
    private final Setting.Action verifyBedrock = setting(new Setting.Action("Verify with bedrock", () -> INSTANCE.bedrockRequested = true));
    private final List<Ore> ores = new ArrayList<>();
    private static final Set<String> ALL_ORES = Set.of("Coal", "Iron", "Gold", "Redstone", "Diamond", "Lapis", "Copper",
            "Emerald", "Quartz", "Ancient Debris");
    private volatile List<Marker> markers = List.of();
    private volatile Accuracy lastAccuracy;
    private volatile boolean verifyRequested, bedrockRequested, antiXrayNoticed;
    private volatile BedrockCheck.Result lastBedrock;
    private final AtomicBoolean predicting = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private String requestedKey = "", observedLayout = "";
    private ClientLevel observedWorld;
    private long layoutChangedAt;

    private SeedRayModule() {
        super("Seed-Ray", "Exact ore positions from the world seed, even through anti-xray", "Mining", false);
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

    /** Numeric seeds as-is; text seeds hash the way vanilla's world creation screen does. */
    static OptionalLong parseSeed(String text) {
        String s = text.trim();
        if (s.isEmpty()) return OptionalLong.empty();
        try { return OptionalLong.of(Long.parseLong(s)); }
        catch (NumberFormatException e) { return OptionalLong.of(s.hashCode()); }
    }

    /** The seed Seed-Ray would use (typed in its settings, or the singleplayer world's), whether or not it is on. */
    public static OptionalLong activeSeed() { return INSTANCE.seed(Minecraft.getInstance()); }

    private OptionalLong seed(Minecraft mc) {
        OptionalLong typed = parseSeed(worldSeed.get());
        if (typed.isPresent()) return typed;
        // singleplayer: the seed is right there
        var server = mc.getSingleplayerServer();
        return server == null ? OptionalLong.empty() : OptionalLong.of(server.overworld().getSeed());
    }

    @Override public void tick(Minecraft mc) {
        ClientLevel world = mc.level;
        if (world == null || mc.player == null) { reset(); return; }
        OptionalLong seed = seed(mc);
        if (seed.isEmpty()) { if (!markers.isEmpty()) reset(); return; }
        int cx = mc.player.chunkPosition().x(), cz = mc.player.chunkPosition().z(), r = (int) radius.get();
        String path = world.dimension().identifier().getPath();
        if (!path.equals("overworld") && !path.equals("the_nether")) { markers = List.of(); return; }
        if (bedrockRequested) {
            bedrockRequested = false;
            SeedOrePredictor.Snapshot bedrockSnap = new SeedOrePredictor.Snapshot(world, cx, cz, r);
            long checkSeed = seed.getAsLong();
            PREDICTOR.execute(() -> {
                try { reportBedrock(BedrockCheck.run(bedrockSnap, checkSeed), checkSeed); }
                catch (RuntimeException e) { Notifications.error("Seed-Ray", "Bedrock check failed: " + e.getMessage()); }
            });
        }

        // wait for streamed chunks to settle before snapshotting
        String layout = layout(world, cx, cz, r + 1);
        long now = System.currentTimeMillis();
        if (world != observedWorld || !layout.equals(observedLayout)) {
            observedWorld = world;
            observedLayout = layout;
            layoutChangedAt = now;
        }
        Map<String, Integer> enabled = new HashMap<>();
        for (Ore ore : ores) if (ore.enabled.get()) enabled.put(ore.name, ore.color);
        String key = seed.getAsLong() + "|" + cx + "|" + cz + "|" + r + "|" + path + "|" + enabled.keySet() + "|"
                + deepslateOres.get() + hideMined.get() + "|" + layout;
        boolean verifyNow = verifyRequested;
        if (key.equals(requestedKey) && !verifyNow) return;
        if (now - layoutChangedAt < 750L || !predicting.compareAndSet(false, true)) return;
        verifyRequested = false;
        requestedKey = key;
        long gen = generation.incrementAndGet();

        List<int[]> chunks = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                if (dx * dx + dz * dz <= r * r) chunks.add(new int[]{cx + dx, cz + dz});
        chunks.sort(Comparator.comparingInt(c -> (c[0] - cx) * (c[0] - cx) + (c[1] - cz) * (c[1] - cz)));
        // verification checks the whole square (more chunks with every neighbour simulated)
        List<int[]> square = new ArrayList<>();
        if (verifyNow) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) square.add(new int[]{cx + dx, cz + dz});
        SeedOrePredictor.Snapshot snap = new SeedOrePredictor.Snapshot(world, cx, cz, r);
        long s = seed.getAsLong();
        boolean deepslate = deepslateOres.get(), hide = hideMined.get();
        PREDICTOR.execute(() -> {
            try {
                List<SeedOrePredictor.Hit> hits = SeedOrePredictor.predict(snap, s, verifyNow ? square : chunks);
                List<Marker> out = new ArrayList<>();
                for (SeedOrePredictor.Hit h : hits) {
                    Block b = h.state().getBlock();
                    Integer color = enabled.get(oreName(b));
                    if (color == null || (!deepslate && isDeepslate(b))) continue;
                    BlockState now2 = snap.get(h.pos().getX(), h.pos().getY(), h.pos().getZ());
                    if (hide && (now2.isAir() || !now2.getFluidState().isEmpty())) continue;
                    out.add(new Marker(h.pos(), color));
                }
                if (generation.get() == gen && isEnabled()) markers = out;
                if (SeedOrePredictor.lastAntiXray && !antiXrayNoticed) {
                    antiXrayNoticed = true;
                    Notifications.info("Seed-Ray", "Anti-xray detected: predicting through the fake blocks");
                }
                if (verifyNow) report(verify(snap, hits, square, ALL_ORES, true));
            } catch (RuntimeException e) {
                requestedKey = "";
                System.err.println("Anthrax Seed-Ray: prediction failed: " + e);
                e.printStackTrace();
                if (verifyNow) Notifications.error("Seed-Ray", "Prediction failed: " + e.getMessage());
            } finally { predicting.set(false); }
        });
    }

    /**
     * Compares predicted ores with the real blocks, in chunks whose neighbours were all predicted too
     * (only there can every vein that reaches the chunk be accounted for).
     */
    static Accuracy verify(SeedOrePredictor.Snapshot snap, List<SeedOrePredictor.Hit> hits, List<int[]> chunks,
                           Set<String> enabled, boolean deepslate) {
        Set<Long> predictedChunks = new HashSet<>();
        for (int[] c : chunks) if (snap.hasArea(c[0], c[1])) predictedChunks.add(net.minecraft.world.level.ChunkPos.pack(c[0], c[1]));
        Set<Long> inner = new HashSet<>();
        for (long k : predictedChunks) {
            int x = net.minecraft.world.level.ChunkPos.getX(k), z = net.minecraft.world.level.ChunkPos.getZ(k);
            boolean all = true;
            for (int dx = -1; dx <= 1 && all; dx++)
                for (int dz = -1; dz <= 1 && all; dz++)
                    if (!predictedChunks.contains(net.minecraft.world.level.ChunkPos.pack(x + dx, z + dz))) all = false;
            if (all) inner.add(k);
        }
        Map<BlockPos, Block> predicted = new HashMap<>();
        for (SeedOrePredictor.Hit h : hits) {
            Block b = h.state().getBlock();
            String name = oreName(b);
            if (name == null || !enabled.contains(name) || (!deepslate && isDeepslate(b))) continue;
            if (inner.contains(net.minecraft.world.level.ChunkPos.pack(h.pos().getX() >> 4, h.pos().getZ() >> 4))) predicted.put(h.pos(), b);
        }
        int actual = 0, matched = 0;
        for (long k : inner) {
            int bx = net.minecraft.world.level.ChunkPos.getX(k) << 4, bz = net.minecraft.world.level.ChunkPos.getZ(k) << 4;
            for (int y = snap.minY; y < snap.minY + snap.height; y++)
                for (int x = bx; x < bx + 16; x++)
                    for (int z = bz; z < bz + 16; z++) {
                        Block b = snap.get(x, y, z).getBlock();
                        String name = oreName(b);
                        if (name == null || !enabled.contains(name) || (!deepslate && isDeepslate(b))) continue;
                        actual++;
                        if (predicted.get(new BlockPos(x, y, z)) == b) matched++;
                    }
        }
        return new Accuracy(predicted.size(), actual, matched, inner.size());
    }

    private void reportBedrock(BedrockCheck.Result b, long checked) {
        lastBedrock = b;
        String text = String.format("%.2f%% of %d bedrock blocks match seed %d (%d chunks)", b.score() * 100, b.total(), checked, b.chunks());
        System.out.println("[Seed-Ray] bedrock " + text);
        if (b.total() == 0) Notifications.error("Seed-Ray", "No loaded chunks to check yet");
        else if (b.score() > 0.999) Notifications.success("Seed confirmed by bedrock", text);
        else if (b.score() < 0.8) Notifications.error("Seed does not match", text + ". Wrong seed, or a modded/custom world");
        else Notifications.info("Seed-Ray", text);
    }

    public BedrockCheck.Result lastBedrock() { return lastBedrock; }
    public void requestBedrockCheck() { bedrockRequested = true; }

    private void report(Accuracy a) {
        lastAccuracy = a;
        String text = String.format("%d chunks: %.2f%% of predicted ores are real, %.2f%% of real ores predicted (%d/%d)",
                a.chunks(), a.precision() * 100, a.recall() * 100, a.matched(), a.actual());
        System.out.println("[Seed-Ray] verify " + text);
        if (a.chunks() == 0) Notifications.error("Seed-Ray", "Not enough loaded chunks to verify; stand still a moment");
        else if (a.precision() > 0.97 && a.recall() > 0.97) Notifications.success("Seed-Ray verified", text);
        else if (a.precision() < 0.2) {
            // Bedrock depends only on the seed and is never hidden, so it tells the two causes apart.
            BedrockCheck.Result b = lastBedrock;
            if (b != null && b.total() > 0 && b.score() > 0.999)
                Notifications.info("Seed-Ray: seed is right", "Bedrock matches, so ores here are hidden by anti-xray, "
                        + "changed by the server, or from an older game version. " + text);
            else if (b != null && b.total() > 0)
                Notifications.error("Seed-Ray: wrong seed", "Bedrock does not match either. " + text);
            else
                Notifications.error("Seed-Ray", "Wrong seed, or ores here are hidden or altered. Run \"Verify with bedrock\" to tell which. " + text);
        }
        else Notifications.info("Seed-Ray", text);
    }

    public Accuracy lastAccuracy() { return lastAccuracy; }
    public void requestVerify() { verifyRequested = true; }

    private void reset() {
        generation.incrementAndGet();
        markers = List.of();
        requestedKey = "";
        observedWorld = null;
        observedLayout = "";
    }

    @Override protected void onToggle(boolean enabled) { reset(); }

    @Override public String hudInfo() { return markers.isEmpty() ? null : Integer.toString(markers.size()); }

    private static String layout(ClientLevel world, int centerX, int centerZ, int radius) {
        StringBuilder sb = new StringBuilder((radius * 2 + 1) * (radius * 2 + 1));
        for (int x = centerX - radius; x <= centerX + radius; x++)
            for (int z = centerZ - radius; z <= centerZ + radius; z++) sb.append(world.hasChunk(x, z) ? '1' : '0');
        return sb.toString();
    }

    public static void renderGizmos(LevelRenderer renderer) {
        SeedRayModule module = INSTANCE;
        List<Marker> list = module.markers;
        if (!module.isEnabled() || list.isEmpty()) return;
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        boolean glow = module.renderMode.get().equals("Glow");
        try (var ignored = Gizmos.withCollector(collector)) {
            for (Marker marker : list) {
                GizmoStyle style = glow
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
