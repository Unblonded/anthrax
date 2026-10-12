package unblonded.anthrax.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Amethyst geode ESP. Budding amethyst in loaded chunks is grouped into geodes; each geode gets an
 * outline and a label with its size, how many crystals are fully grown (harvestable), and the amethyst
 * clock: estimated hours of player presence nearby (grown crystals mean people spent time here).
 */
public final class AmethystEspModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final AmethystEspModule INSTANCE = new AmethystEspModule();
    private static final int GEODE_COLOR = 0xFFB76BFF, BUDDING_COLOR = 0xFFE07BFF, CLUSTER_COLOR = 0xFF7FE9FF;

    /** A detected geode. */
    private record Geode(AABB box, Vec3 center, int budding, int clusters, float hours,
                         List<BlockPos> buddingBlocks, List<BlockPos> clusterBlocks) {}

    private final Setting.Num range = setting(Setting.Num.standalone("Range", 96, 16, 256, 8));
    private final Setting.Bool showBudding = setting(Setting.Bool.standalone("Highlight budding amethyst", true));
    private final Setting.Bool showClusters = setting(Setting.Bool.standalone("Highlight grown clusters", true));
    private final Setting.Bool labels = setting(Setting.Bool.standalone("Labels", true));
    private final Setting.Bool tracers = setting(Setting.Bool.standalone("Tracers", false));
    private final Setting.Bool throughWalls = setting(Setting.Bool.standalone("Through walls", true));
    private volatile List<Geode> geodes = List.of();
    private long lastScan;

    private Block budding, cluster;
    private final Map<Block, Integer> budStage = new IdentityHashMap<>();

    private AmethystEspModule() {
        super("Amethyst ESP", "Find amethyst geodes, grown clusters and how long players spent nearby", "Mining", false);
    }

    @Override public boolean active() { return isEnabled() && !geodes.isEmpty(); }

    private void resolveBlocks() {
        if (budding != null) return;
        for (Block b : BuiltInRegistries.BLOCK) {
            switch (BuiltInRegistries.BLOCK.getKey(b).getPath()) {
                case "budding_amethyst" -> budding = b;
                case "small_amethyst_bud" -> budStage.put(b, 1);
                case "medium_amethyst_bud" -> budStage.put(b, 2);
                case "large_amethyst_bud" -> budStage.put(b, 3);
                case "amethyst_cluster" -> { budStage.put(b, 4); cluster = b; }
                default -> {}
            }
        }
    }

    @Override public void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (mc.level == null || mc.player == null || now - lastScan < 2000) return;
        lastScan = now;
        resolveBlocks();
        if (budding == null) return;

        // 1. collect budding amethyst positions (palette check first, so most sections are skipped instantly)
        int r = (int) Math.ceil(range.get() / 16.0);
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        List<BlockPos> found = new ArrayList<>();
        for (int cx = pcx - r; cx <= pcx + r; cx++)
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection s = sections[i];
                    if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.getBlock() == budding)) continue;
                    int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++)
                                if (s.getBlockState(x, y, z).getBlock() == budding)
                                    found.add(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z));
                }
            }

        // 2. group into geodes: budding blocks within 6 blocks of each other belong together
        List<List<BlockPos>> groups = new ArrayList<>();
        boolean[] used = new boolean[found.size()];
        for (int i = 0; i < found.size(); i++) {
            if (used[i]) continue;
            List<BlockPos> group = new ArrayList<>();
            group.add(found.get(i));
            used[i] = true;
            for (int g = 0; g < group.size(); g++) {
                BlockPos a = group.get(g);
                for (int j = 0; j < found.size(); j++) {
                    if (used[j]) continue;
                    if (a.distManhattan(found.get(j)) <= 6) { used[j] = true; group.add(found.get(j)); }
                }
            }
            groups.add(group);
        }

        // 3. measure each geode: growth of the crystals on its budding faces
        double rangeSq = range.get() * range.get();
        List<Geode> out = new ArrayList<>();
        for (List<BlockPos> group : groups) {
            int faces = 0, stageSum = 0;
            List<BlockPos> clusters = new ArrayList<>();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos p : group) {
                minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    BlockState s = mc.level.getBlockState(n);
                    Integer stage = budStage.get(s.getBlock());
                    if (stage != null) {
                        if (s.hasProperty(BlockStateProperties.FACING) && s.getValue(BlockStateProperties.FACING) == d) {
                            faces++;
                            stageSum += stage;
                            if (s.getBlock() == cluster) clusters.add(n);
                        }
                    } else if (s.isAir() || s.getFluidState().isSource()) {
                        faces++;
                    }
                }
            }
            AABB box = new AABB(minX - 1, minY - 1, minZ - 1, maxX + 2, maxY + 2, maxZ + 2);
            Vec3 center = box.getCenter();
            if (mc.player.distanceToSqr(center) > rangeSq) continue;
            float hours = faces >= 6 ? AmethystClock.hours(faces, stageSum, group.size()) : -1f;
            out.add(new Geode(box, center, group.size(), clusters.size(), hours, List.copyOf(group), List.copyOf(clusters)));
        }
        Vec3 p = mc.player.position();
        out.sort(Comparator.comparingDouble(g -> g.center.distanceToSqr(p)));
        geodes = List.copyOf(out.size() > 40 ? out.subList(0, 40) : out);
    }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        boolean onTop = throughWalls.get();
        int boxBudget = 1500, labelled = 0;
        for (Geode g : geodes) {   // sorted nearest first
            var outline = Gizmos.cuboid(g.box, GizmoStyle.strokeAndFill(WorldOverlays.argb(0xD0, GEODE_COLOR), 1.5f,
                    WorldOverlays.argb(0x14, GEODE_COLOR)));
            if (onTop) outline.setAlwaysOnTop();
            if (showBudding.get())
                for (BlockPos b : g.buddingBlocks) {
                    if (boxBudget-- <= 0) break;
                    var box = Gizmos.cuboid(b, GizmoStyle.stroke(WorldOverlays.argb(0xB0, BUDDING_COLOR), 1f));
                    if (onTop) box.setAlwaysOnTop();
                }
            if (showClusters.get())
                for (BlockPos c : g.clusterBlocks) {
                    if (boxBudget-- <= 0) break;
                    var box = Gizmos.cuboid(new AABB(c).deflate(0.2), GizmoStyle.strokeAndFill(WorldOverlays.argb(0xF0, CLUSTER_COLOR), 1.5f,
                            WorldOverlays.argb(0x40, CLUSTER_COLOR)));
                    if (onTop) box.setAlwaysOnTop();
                }
            if (tracers.get())
                Gizmos.line(eye.add(look.scale(2)), g.center, WorldOverlays.argb(0xC0, GEODE_COLOR), 1.5f).setAlwaysOnTop();
            // only the nearest few get a label, so a cave full of geodes stays readable
            if (labels.get() && labelled++ < 8) {
                Vec3 top = new Vec3(g.center.x, g.box.maxY + 0.6, g.center.z);
                double dist = eye.distanceTo(top);
                float scale = (float) (TextGizmo.Style.DEFAULT_SCALE * Math.max(1.2, dist / 6.0));
                String clock = g.hours < 0 ? "" : g.hours >= 12 ? "   12h+ players nearby" : String.format("   ~%.1fh players nearby", g.hours);
                String text = "Geode  " + g.budding + " budding   " + g.clusters + " grown" + clock + "   " + Math.round(dist) + "m";
                var label = Gizmos.billboardText(text, top, TextGizmo.Style.forColorAndCentered(GEODE_COLOR).withScale(scale));
                if (onTop) label.setAlwaysOnTop();
            }
        }
    }

    @Override protected void onToggle(boolean enabled) { geodes = List.of(); lastScan = 0; }
    @Override public String hudInfo() { return Integer.toString(geodes.size()); }
}
