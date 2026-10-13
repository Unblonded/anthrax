package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Boxes around ores in loaded chunks through walls, coloured by ore type. */
public final class OreEspModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final OreEspModule INSTANCE = new OreEspModule();
    private static final ExecutorService SCANNER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Anthrax ore ESP scanner"); t.setDaemon(true); return t;
    });

    private record Found(BlockPos pos, int color) {}

    private final Setting.Num radius = setting(Setting.Num.standalone("Radius", 48, 16, 128, 8), "Horizontal scan radius in blocks.");
    private final Setting.Bool diamond = setting(Setting.Bool.standalone("Diamond", true), "");
    private final Setting.Bool debris = setting(Setting.Bool.standalone("Ancient debris", true), "");
    private final Setting.Bool emerald = setting(Setting.Bool.standalone("Emerald", true), "");
    private final Setting.Bool gold = setting(Setting.Bool.standalone("Gold", true), "");
    private final Setting.Bool iron = setting(Setting.Bool.standalone("Iron", false), "");
    private final Setting.Bool redstone = setting(Setting.Bool.standalone("Redstone", false), "");
    private final Setting.Bool lapis = setting(Setting.Bool.standalone("Lapis", false), "");
    private final Setting.Bool copper = setting(Setting.Bool.standalone("Copper", false), "");
    private final Setting.Bool coal = setting(Setting.Bool.standalone("Coal", false), "");
    private final Setting.Bool quartz = setting(Setting.Bool.standalone("Nether quartz", false), "");
    private final Setting.Bool exposedOnly = setting(Setting.Bool.standalone("Only exposed to air", false), "Hides ores fully buried in stone (servers with anti-xray fake those).");
    private final Setting.Num limit = setting(Setting.Num.standalone("Max boxes", 1500, 100, 5000, 100), "Stops drawing past this many.");
    private final AtomicBoolean scanning = new AtomicBoolean();
    private volatile List<Found> found = List.of();
    private long lastScan;

    private OreEspModule() { super("Ore ESP", "Shows boxes around ores through walls, coloured by ore type. No resource pack needed", "Visuals", false); }

    private Map<Block, Integer> wanted() {
        Map<Block, Integer> m = new HashMap<>();
        if (diamond.get()) { m.put(Blocks.DIAMOND_ORE, 0x5CE1E6); m.put(Blocks.DEEPSLATE_DIAMOND_ORE, 0x5CE1E6); }
        if (debris.get()) m.put(Blocks.ANCIENT_DEBRIS, 0x8B5A3C);
        if (emerald.get()) { m.put(Blocks.EMERALD_ORE, 0x41F384); m.put(Blocks.DEEPSLATE_EMERALD_ORE, 0x41F384); }
        if (gold.get()) { m.put(Blocks.GOLD_ORE, 0xFFD24A); m.put(Blocks.DEEPSLATE_GOLD_ORE, 0xFFD24A); m.put(Blocks.NETHER_GOLD_ORE, 0xFFD24A); }
        if (iron.get()) { m.put(Blocks.IRON_ORE, 0xD8AF93); m.put(Blocks.DEEPSLATE_IRON_ORE, 0xD8AF93); }
        if (redstone.get()) { m.put(Blocks.REDSTONE_ORE, 0xFF3B3B); m.put(Blocks.DEEPSLATE_REDSTONE_ORE, 0xFF3B3B); }
        if (lapis.get()) { m.put(Blocks.LAPIS_ORE, 0x3B5BFF); m.put(Blocks.DEEPSLATE_LAPIS_ORE, 0x3B5BFF); }
        if (copper.get()) { m.put(Blocks.COPPER_ORE, 0xE07B4C); m.put(Blocks.DEEPSLATE_COPPER_ORE, 0xE07B4C); }
        if (coal.get()) { m.put(Blocks.COAL_ORE, 0x3A3A3A); m.put(Blocks.DEEPSLATE_COAL_ORE, 0x3A3A3A); }
        if (quartz.get()) m.put(Blocks.NETHER_QUARTZ_ORE, 0xF0E8E0);
        return m;
    }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { found = List.of(); return; }
        long now = System.currentTimeMillis();
        if (now - lastScan < 1500 || !scanning.compareAndSet(false, true)) return;
        lastScan = now;
        Map<Block, Integer> wanted = wanted();
        int r = (int) Math.ceil(radius.get() / 16.0);
        int cx = mc.player.chunkPosition().x(), cz = mc.player.chunkPosition().z();
        List<LevelChunk> chunks = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
            LevelChunk c = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (c != null) chunks.add(c);
        }
        int minY = mc.level.getMinY(), maxY = mc.level.getMaxY(), max = (int) limit.get();
        boolean exposed = exposedOnly.get();
        var level = mc.level;
        SCANNER.execute(() -> {
            try {
                List<Found> out = new ArrayList<>();
                outer:
                for (LevelChunk chunk : chunks) {
                    ChunkPos cp = chunk.getPos();
                    var sections = chunk.getSections();
                    for (int s = 0; s < sections.length; s++) {
                        var sec = sections[s];
                        if (sec == null || sec.hasOnlyAir()) continue;
                        if (!sec.maybeHas(st -> wanted.containsKey(st.getBlock()))) continue;
                        int baseY = minY + s * 16;
                        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                            Integer color = wanted.get(sec.getBlockState(x, y, z).getBlock());
                            if (color == null) continue;
                            BlockPos p = new BlockPos(cp.getMinBlockX() + x, baseY + y, cp.getMinBlockZ() + z);
                            if (exposed && !touchesAir(level, p)) continue;
                            out.add(new Found(p, color));
                            if (out.size() >= max) break outer;
                        }
                    }
                }
                found = List.copyOf(out);
            } catch (RuntimeException ignored) {
                // a chunk can unload mid-scan; the next pass picks up the change
            } finally { scanning.set(false); }
        });
    }

    private static boolean touchesAir(net.minecraft.world.level.Level level, BlockPos p) {
        for (var d : net.minecraft.core.Direction.values()) if (level.getBlockState(p.relative(d)).isAir()) return true;
        return false;
    }

    @Override protected void onToggle(boolean enabled) { if (!enabled) found = List.of(); lastScan = 0; }

    @Override public boolean active() { return isEnabled() && !found.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        for (Found f : found)
            Gizmos.cuboid(f.pos, GizmoStyle.strokeAndFill(WorldOverlays.argb(0xE0, f.color), 1.5f, WorldOverlays.argb(0x30, f.color))).setAlwaysOnTop();
    }

    @Override public String hudInfo() { return found.isEmpty() ? null : String.valueOf(found.size()); }
}
