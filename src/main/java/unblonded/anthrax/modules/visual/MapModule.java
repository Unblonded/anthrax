package unblonded.anthrax.modules.visual;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the terrain you explore (top-down map colours, shaded by height like vanilla maps) and the path you walk,
 * per server and dimension, saved between sessions. A key opens the full-screen map; the minimap shows the same on
 * the HUD. Terrain is kept in 256x256-block regions, each a texture that is updated as chunks are seen.
 */
public final class MapModule extends FeatureModule {
    public static final MapModule INSTANCE = new MapModule();
    private static final Logger LOG = LoggerFactory.getLogger("Anthrax/Map");
    public static final int REGION = 256;

    private final Setting.Key key = setting(Setting.Key.standalone("Map key", GLFW.GLFW_KEY_M,
            () -> Minecraft.getInstance().gui.setScreen(new unblonded.anthrax.gui.MapScreen())), "Opens the full-screen map.");
    private final Setting.Bool minimap = setting(Setting.Bool.standalone("Minimap", true), "Shows a minimap on the HUD (move it in HUD Layout).");
    private final Setting.Num minimapZoom = setting(Setting.Num.standalone("Minimap zoom", 1.0, 0.25, 4, 0.25), "Blocks per minimap pixel. Lower is closer.");
    private final Setting.Bool path = setting(Setting.Bool.standalone("Record path", true), "Remembers where you have walked and draws it on the map.");
    private final Setting.Bool caves = setting(Setting.Bool.standalone("Cave view underground", false), "When you are underground, maps the level you are on instead of the surface.");

    /** One 256x256 block region: pixels and the texture showing them. */
    public static final class Region {
        public final int rx, rz;
        public final Identifier id;
        final NativeImage image;
        final DynamicTexture texture;
        boolean dirty, changed;

        Region(int rx, int rz, NativeImage image, String key) {
            this.rx = rx; this.rz = rz; this.image = image;
            this.id = Identifier.fromNamespaceAndPath("anthrax", "map/" + key + "/" + rx + "_" + rz);
            this.texture = new DynamicTexture(() -> "anthrax map " + rx + "," + rz, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    private final Map<Long, Region> regions = new HashMap<>();
    private final Map<Long, Long> seenChunks = new HashMap<>();
    private final List<BlockPos> trail = new ArrayList<>();
    private String key0 = "";
    private Path folder;
    private int tick;
    private long lastSave;

    private MapModule() { super("Map", "Records explored terrain and the path you travel, per server, saved between sessions. Press its key for a full-screen map with your waypoints; the minimap shows the same in the HUD", "Visuals", false); }

    private static long pack(int a, int b) { return ((long) a << 32) ^ (b & 0xFFFFFFFFL); }

    /** Server (or singleplayer world) and dimension, safe for a folder name. */
    private static String worldKey(Minecraft mc) {
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip
                : mc.getSingleplayerServer() != null ? "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName() : "unknown";
        String dim = mc.level.dimension().identifier().toString();
        return (server + "_" + dim).toLowerCase().replaceAll("[^a-z0-9._-]", "_");
    }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { if (!regions.isEmpty()) unload(); return; }
        String k = worldKey(mc);
        if (!k.equals(key0)) { unload(); key0 = k; folder = FabricLoader.getInstance().getConfigDir().resolve("anthrax-map").resolve(k); loadTrail(); }
        tick++;
        if (path.get() && tick % 20 == 0) {
            BlockPos at = mc.player.blockPosition();
            if (trail.isEmpty() || trail.getLast().distSqr(at) > 16) { trail.add(at); if (trail.size() > 20000) trail.removeFirst(); }
        }
        if (tick % 10 == 0) {
            int view = mc.options.getEffectiveRenderDistance();
            int cx = mc.player.chunkPosition().x(), cz = mc.player.chunkPosition().z();
            long now = System.currentTimeMillis();
            int budget = 24;
            for (int r = 0; r <= view && budget > 0; r++)
                for (int x = cx - r; x <= cx + r && budget > 0; x++)
                    for (int z = cz - r; z <= cz + r && budget > 0; z++) {
                        if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != r) continue;
                        long ck = pack(x, z);
                        Long seen = seenChunks.get(ck);
                        if (seen != null && now - seen < 20000) continue;
                        LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                        if (chunk == null) continue;
                        record(mc, chunk);
                        seenChunks.put(ck, now);
                        budget--;
                    }
        }
        for (Region r : regions.values()) if (r.changed) { r.texture.upload(); r.changed = false; }
        if (System.currentTimeMillis() - lastSave > 60000) save();
    }

    /** Paints one chunk's top-down colours into its region. */
    private void record(Minecraft mc, LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
        Region region = region(Math.floorDiv(bx, REGION), Math.floorDiv(bz, REGION), true);
        boolean ceiling = mc.level.dimensionType().hasCeiling();
        boolean cave = caves.get() && !mc.level.canSeeSky(mc.player.blockPosition()) && !ceiling;
        int startY = Math.min(mc.level.getMaxY() - 1, mc.player.getBlockY() + 2);
        int[] heights = new int[17];
        for (int x = 0; x < 16; x++) {
            int prevH = Integer.MIN_VALUE;
            for (int z = -1; z < 16; z++) {
                int lz = Math.max(0, z);
                int y = ceiling || cave ? floorBelow(chunk, x, lz, startY) : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, lz);
                if (z < 0) { prevH = y; continue; }
                BlockPos pos = new BlockPos(bx + x, y, bz + z);
                BlockState state = chunk.getBlockState(pos);
                int color;
                if (state.isAir()) color = 0;
                else {
                    MapColor mc0 = state.getMapColor(mc.level, pos);
                    MapColor.Brightness b = y > prevH ? MapColor.Brightness.HIGH : y < prevH ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    if (!state.getFluidState().isEmpty()) b = MapColor.Brightness.NORMAL;
                    color = mc0 == MapColor.NONE ? 0 : 0xFF000000 | (mc0.calculateARGBColor(b) & 0xFFFFFF);
                }
                prevH = y;
                region.image.setPixel(Math.floorMod(bx + x, REGION), Math.floorMod(bz + z, REGION), color);
            }
        }
        region.changed = true;
        region.dirty = true;
    }

    /** Highest solid block at or below {@code fromY} with air above it (for the Nether and cave view). */
    private static int floorBelow(LevelChunk chunk, int x, int z, int fromY) {
        int minY = chunk.getMinY();
        boolean air = false;
        for (int y = fromY; y > minY; y--) {
            BlockState s = chunk.getBlockState(new BlockPos(x, y, z));
            if (s.isAir()) air = true;
            else if (air) return y;
        }
        return minY;
    }

    /** The region at (rx, rz), loaded from disk or created when {@code create}; null if absent and not creating. */
    public Region region(int rx, int rz, boolean create) {
        long k = pack(rx, rz);
        Region r = regions.get(k);
        if (r != null) return r;
        NativeImage img = null;
        Path file = folder == null ? null : folder.resolve("r." + rx + "." + rz + ".png");
        if (file != null && Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { img = NativeImage.read(in); }
            catch (Exception e) { LOG.warn("Could not read map region {}", file, e); }
        }
        if (img == null) {
            if (!create) return null;
            img = new NativeImage(REGION, REGION, true);
        }
        r = new Region(rx, rz, img, key0.hashCode() == 0 ? "x" : Integer.toHexString(key0.hashCode()));
        regions.put(k, r);
        return r;
    }

    /** Regions overlapping a block rectangle, loading saved ones from disk as needed. */
    public List<Region> regionsIn(double minX, double minZ, double maxX, double maxZ) {
        List<Region> out = new ArrayList<>();
        for (int rx = Math.floorDiv((int) Math.floor(minX), REGION); rx <= Math.floorDiv((int) Math.ceil(maxX), REGION); rx++)
            for (int rz = Math.floorDiv((int) Math.floor(minZ), REGION); rz <= Math.floorDiv((int) Math.ceil(maxZ), REGION); rz++) {
                Region r = region(rx, rz, false);
                if (r != null) out.add(r);
            }
        return out;
    }

    public Collection<BlockPos> trail() { return trail; }
    public boolean showMinimap() { return isEnabled() && minimap.get(); }
    public double minimapZoom() { return minimapZoom.get(); }
    public int mapKey() { return key.get(); }

    private void save() {
        lastSave = System.currentTimeMillis();
        if (folder == null) return;
        try {
            Files.createDirectories(folder);
            for (Region r : regions.values()) {
                if (!r.dirty) continue;
                r.image.writeToFile(folder.resolve("r." + r.rx + "." + r.rz + ".png"));
                r.dirty = false;
            }
            if (path.get()) {
                StringBuilder sb = new StringBuilder();
                for (BlockPos p : trail) sb.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append('\n');
                Files.writeString(folder.resolve("path.csv"), sb);
            }
        } catch (Exception e) { LOG.warn("Could not save the map", e); }
    }

    private void loadTrail() {
        trail.clear();
        try {
            Path f = folder.resolve("path.csv");
            if (!Files.exists(f)) return;
            for (String line : Files.readAllLines(f)) {
                String[] p = line.split(",");
                if (p.length == 3) trail.add(new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
            }
        } catch (Exception e) { LOG.warn("Could not read the saved path", e); }
    }

    private void unload() {
        save();
        Minecraft mc = Minecraft.getInstance();
        for (Region r : regions.values()) mc.getTextureManager().release(r.id);
        regions.clear();
        seenChunks.clear();
        key0 = "";
    }

    @Override protected void onToggle(boolean enabled) { if (!enabled) unload(); }

    /** Saves everything on game exit. */
    public void shutdown() { if (!regions.isEmpty() || !trail.isEmpty()) save(); }
}
