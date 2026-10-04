package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Finds configured block IDs in already-loaded chunks and draws a lightweight wire overlay. */
public final class BlockEspModule extends FeatureModule {
    private static final ExecutorService SCANNER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Anthrax block ESP scanner"); t.setDaemon(true); return t;
    });
    private final Setting.Num radius = setting(Setting.Num.standalone("Radius (blocks)", 64, 16, 192, 16));
    private final Setting.Bool drawBlocks = setting(Setting.Bool.standalone("Draw blocks", true));
    private final Setting.Bool tracers = setting(Setting.Bool.standalone("Draw tracers", false));
    private final Setting.Mode drawMode = setting(Setting.Mode.standalone("Render mode", "Wire", "Wire", "Glow"));
    private final Setting.Mode color = setting(Setting.Mode.standalone("Color", "Cyan", "Cyan", "Red", "Green", "Gold", "Violet", "White"));
    private final Setting.Text blockIds = setting(Setting.Text.standalone("Block IDs (comma separated)", "", 512));
    private volatile List<BlockPos> found = List.of();
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final AtomicLong scanGeneration = new AtomicLong();
    private long lastScan;
    private net.minecraft.client.multiplayer.ClientLevel scannedWorld;
    private String scanConfigKey = "";

    private BlockEspModule() {
        super("Advanced ESP", "Highlight configured blocks in loaded chunks", "Visuals", false);
    }

    @Override public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (scannedWorld != null) scanGeneration.incrementAndGet();
            scannedWorld = null; scanConfigKey = ""; found = List.of(); lastScan = 0; return;
        }
        long now = System.currentTimeMillis();
        if (client.level != scannedWorld) {
            scannedWorld = client.level;
            scanGeneration.incrementAndGet();
            found = List.of(); lastScan = 0;
        }
        String configKey = client.player.chunkPosition().x() + ":" + client.player.chunkPosition().z()
                + ":" + radius.get() + ":" + blockIds.get();
        if (!configKey.equals(scanConfigKey)) {
            scanConfigKey = configKey;
            scanGeneration.incrementAndGet();
            found = List.of();
            lastScan = 0;
        }
        if (now - lastScan < 1200 || !scanning.compareAndSet(false, true)) return;
        lastScan = now;
        Set<Block> wanted = parseBlocks(blockIds.get());
        if (wanted.isEmpty()) { found = List.of(); scanning.set(false); return; }
        int r = (int) Math.ceil(radius.get() / 16.0);
        int cx = client.player.chunkPosition().x(), cz = client.player.chunkPosition().z();
        int minY = client.level.getMinY(), maxY = client.level.getMaxY();
        List<LevelChunk> chunks = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
            if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > r * r) continue;
            LevelChunk chunk = client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (chunk != null) chunks.add(chunk);
        }
        int px = client.player.blockPosition().getX(), pz = client.player.blockPosition().getZ();
        int maxRadius = (int) radius.get();
        long generation = scanGeneration.incrementAndGet();
        SCANNER.execute(() -> {
            try {
                List<BlockPos> result = new ArrayList<>();
                for (LevelChunk chunk : chunks) {
                    ChunkPos cp = chunk.getPos();
                    for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++)
                        for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                            if ((x - px) * (x - px) + (z - pz) * (z - pz) > maxRadius * maxRadius) continue;
                            for (int y = minY; y < maxY; y++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                if (wanted.contains(chunk.getBlockState(pos).getBlock())) {
                                    result.add(pos);
                                    if (result.size() >= 2048) {
                                        if (scanGeneration.get() == generation && isEnabled()) found = List.copyOf(result);
                                        return;
                                    }
                                }
                            }
                        }
                }
                if (scanGeneration.get() == generation && isEnabled()) found = List.copyOf(result);
            } catch (RuntimeException ignored) {
                // Chunks may unload while the asynchronous read is in progress.
            } finally { scanning.set(false); }
        });
    }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled) { scanGeneration.incrementAndGet(); found = List.of(); lastScan = 0; }
    }

    private static Set<Block> parseBlocks(String input) {
        Set<Block> blocks = new HashSet<>();
        for (String raw : input.split(",")) {
            String id = raw.trim();
            if (id.isEmpty()) continue;
            if (!id.contains(":" )) id = "minecraft:" + id;
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null) BuiltInRegistries.BLOCK.getOptional(identifier).ifPresent(blocks::add);
        }
        return blocks;
    }

    public static void renderGizmos(LevelRenderer renderer) {
        BlockEspModule module = INSTANCE;
        if (module == null || !module.isEnabled() || module.found.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            float[] rgb = color(module.color.get());
            int argb = 0xFF000000 | (Math.round(rgb[0] * 255) << 16) | (Math.round(rgb[1] * 255) << 8) | Math.round(rgb[2] * 255);
            if (module.drawBlocks.get()) for (BlockPos p : module.found) {
                GizmoStyle style = module.drawMode.get().equals("Glow")
                        ? GizmoStyle.strokeAndFill(argb, 2.0f, (argb & 0x00FFFFFF) | 0x30000000)
                        : GizmoStyle.stroke(argb, 1.5f);
                Gizmos.cuboid(p, style).setAlwaysOnTop();
            }
            if (module.tracers.get()) {
                var eye = client.player.getEyePosition();
                for (BlockPos p : module.found) Gizmos.line(eye, new net.minecraft.world.phys.Vec3(p.getX()+.5,p.getY()+.5,p.getZ()+.5), argb, 1.25f).setAlwaysOnTop();
            }
        }
        renderer.addMainThreadGizmos(collector.drainGizmos());
    }

    public static final BlockEspModule INSTANCE = new BlockEspModule();

    static float[] color(String name) {
        return switch (name) {
            case "Red" -> new float[]{1,.2f,.2f}; case "Green" -> new float[]{.25f,1,.35f};
            case "Gold" -> new float[]{1,.7f,.15f}; case "Violet" -> new float[]{.75f,.35f,1};
            case "White" -> new float[]{1,1,1}; default -> new float[]{.1f,.85f,1};
        };
    }
}
