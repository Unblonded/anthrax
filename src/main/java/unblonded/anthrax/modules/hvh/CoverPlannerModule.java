package unblonded.anthrax.modules.hvh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.combat.CombatUtil;
import unblonded.anthrax.modules.combat.ServerSafety;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranks nearby standing spots by explosion cover, attack opportunity, escape routes and how long enemies would need
 * to mine you out, and draws the walking route to the best one.
 */
public final class CoverPlannerModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final CoverPlannerModule INSTANCE = new CoverPlannerModule();
    private final Setting.Num radius = setting(Setting.Num.standalone("Search radius", 6, 3, 10, 1), "How far around you to look for spots.");
    private final Setting.Num enemyRange = setting(Setting.Num.standalone("Enemy range", 16, 4, 32, 2), "Enemies this close are considered.");
    private final Setting.Num weightCover = setting(Setting.Num.standalone("Weight: cover", 1.0, 0, 2, 0.1), "How much blast-proof walls around a spot matter.");
    private final Setting.Num weightAttack = setting(Setting.Num.standalone("Weight: attack", 0.6, 0, 2, 0.1), "How much being close enough to fight back matters.");
    private final Setting.Num weightEscape = setting(Setting.Num.standalone("Weight: escape", 0.5, 0, 2, 0.1), "How much open routes out of a spot matter.");
    private final Setting.Num weightMine = setting(Setting.Num.standalone("Weight: mining time", 0.4, 0, 2, 0.1), "How much the time to dig you out matters, worked out from each enemy's actual tool, enchantments and haste.");
    private final Setting.Num weightDistance = setting(Setting.Num.standalone("Weight: walk distance", 0.3, 0, 2, 0.1), "Penalty for spots that are far to walk to.");
    private final Setting.Num shown = setting(Setting.Num.standalone("Spots shown", 3, 1, 8, 1), "How many of the best spots to outline.");
    private final List<Spot> best = new ArrayList<>();
    private List<BlockPos> route = List.of();
    private int tick;

    /** {@code mineTicks}: how long the fastest enemy needs to break the weakest wall around the spot. */
    private record Spot(BlockPos pos, double score, int cover, int exits, int mineTicks) {}

    private CoverPlannerModule() { super("Cover Planner", "Ranks nearby positions by explosion cover, attack opportunities, escape routes and mining time, and draws the route to the best one", "HVH", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null || ++tick % 10 != 0) return;
        List<Player> foes = Hvh.enemies(mc, enemyRange.get());
        java.util.Map<BlockPos, BlockPos> parent = new java.util.HashMap<>();
        java.util.Map<BlockPos, Integer> dist = bfs(mc, self.blockPosition(), (int) radius.get(), parent);
        best.clear();
        List<Spot> all = new ArrayList<>();
        for (var entry : dist.entrySet()) {
            BlockPos p = entry.getKey();
            int cover = 0, exits = 0;
            int weakest = Integer.MAX_VALUE;
            for (Direction d : Hvh.SIDES) {
                BlockPos side = p.relative(d);
                var s = mc.level.getBlockState(side);
                if (Hvh.blastProof(s)) cover++;
                if (walkable(mc, side)) exits++;
                // an open side needs no mining at all; a wall takes as long as the quickest enemy needs with their own tool
                int t = s.getCollisionShape(mc.level, side).isEmpty() ? 0 : mineTicks(mc, foes, self, s, side);
                weakest = Math.min(weakest, t);
            }
            double mine = Math.min(weakest, 400) / 400.0;
            if (Hvh.blastProof(mc.level.getBlockState(p.below()))) cover++;
            double attack = 0;
            for (Player f : foes) {
                double d = Vec3.atBottomCenterOf(p).distanceTo(f.position());
                attack = Math.max(attack, d <= 3.5 ? 1 : d <= 6 ? 0.5 : 0);
            }
            double score = weightCover.get() * cover / 5.0 + weightAttack.get() * attack + weightEscape.get() * Math.min(exits, 2) / 2.0
                    + weightMine.get() * mine - weightDistance.get() * entry.getValue() / Math.max(1.0, radius.get());
            all.add(new Spot(p, score, cover, exits, weakest));
        }
        all.sort((a, b) -> Double.compare(b.score, a.score));
        for (int i = 0; i < Math.min(shown.get(), all.size()); i++) best.add(all.get(i));
        // walking route to the top spot, from the search tree
        List<BlockPos> path = new ArrayList<>();
        if (!best.isEmpty()) for (BlockPos p = best.getFirst().pos; p != null; p = parent.get(p)) path.addFirst(p);
        route = path;
    }

    /**
     * Ticks to break a block, using each enemy's real mining speed: their held tool, its enchantments, haste and
     * whether they are on the ground (Player.getDestroySpeed). The quickest enemy counts; with none around, your own.
     */
    private static int mineTicks(Minecraft mc, List<Player> foes, Player self, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(mc.level, pos);
        if (hardness < 0) return 100_000;            // unbreakable
        if (hardness == 0) return 1;
        int best = Integer.MAX_VALUE;
        for (Player who : foes.isEmpty() ? List.of(self) : foes) {
            float speed = who.getDestroySpeed(state);
            float perTick = speed / hardness / (who.hasCorrectToolForDrops(state) ? 30f : 100f);
            int ticks = perTick >= 1 ? 1 : (int) Math.ceil(1 / Math.max(1e-6f, perTick));
            best = Math.min(best, ticks);
        }
        return best;
    }

    /** Standing spots reachable on foot (step up or down one block), with walking distance. */
    private static java.util.Map<BlockPos, Integer> bfs(Minecraft mc, BlockPos start, int r, java.util.Map<BlockPos, BlockPos> parent) {
        java.util.Map<BlockPos, Integer> dist = new java.util.HashMap<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        dist.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty() && dist.size() < 4000) {
            BlockPos p = queue.poll();
            for (Direction d : Hvh.SIDES)
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos n = p.relative(d).above(dy);
                    if (dist.containsKey(n) || Math.abs(n.getX() - start.getX()) > r || Math.abs(n.getZ() - start.getZ()) > r
                            || Math.abs(n.getY() - start.getY()) > 3 || !walkable(mc, n)) continue;
                    dist.put(n, dist.get(p) + 1);
                    parent.put(n, p);
                    queue.add(n);
                }
        }
        return dist;
    }

    private static boolean walkable(Minecraft mc, BlockPos feet) {
        var l = mc.level;
        return l.getBlockState(feet).getCollisionShape(l, feet).isEmpty() && l.getBlockState(feet.above()).getCollisionShape(l, feet.above()).isEmpty()
                && !l.getBlockState(feet.below()).getCollisionShape(l, feet.below()).isEmpty() && l.getFluidState(feet).isEmpty();
    }

    @Override public boolean active() { return isEnabled() && !best.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        for (int i = 0; i < best.size(); i++) {
            int color = i == 0 ? 0x55FF88 : i == 1 ? 0xFFD24A : 0xFF8855;
            var box = new net.minecraft.world.phys.AABB(best.get(i).pos).setMaxY(best.get(i).pos.getY() + 0.1);
            net.minecraft.gizmos.Gizmos.cuboid(box, net.minecraft.gizmos.GizmoStyle.strokeAndFill(WorldOverlays.argb(0xE0, color), 2f, WorldOverlays.argb(0x40, color))).setAlwaysOnTop();
            int mt = best.get(i).mineTicks;
            String label = mt >= 100_000 ? "undiggable" : mt == 0 ? "open" : String.format("%.1fs dig", mt / 20.0);
            net.minecraft.gizmos.Gizmos.billboardText(label, Vec3.atBottomCenterOf(best.get(i).pos).add(0, 0.6, 0),
                    net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(WorldOverlays.argb(0xFF, color)).withScale(0.35f)).setAlwaysOnTop();
        }
        for (int i = 1; i < route.size(); i++)
            net.minecraft.gizmos.Gizmos.line(Vec3.atBottomCenterOf(route.get(i - 1)).add(0, 0.1, 0), Vec3.atBottomCenterOf(route.get(i)).add(0, 0.1, 0),
                    WorldOverlays.argb(0xD0, 0x55FF88), 2f).setAlwaysOnTop();
    }

    @Override public String hudInfo() {
        if (best.isEmpty()) return null;
        Spot s = best.getFirst();
        String dig = s.mineTicks >= 100_000 ? "undiggable" : s.mineTicks == 0 ? "open side" : String.format("%.1fs to dig", s.mineTicks / 20.0);
        return s.cover + "/5 cover, " + dig;
    }
}
