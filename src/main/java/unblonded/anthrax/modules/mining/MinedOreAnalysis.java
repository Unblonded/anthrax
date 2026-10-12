package unblonded.anthrax.modules.mining;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds ores players have mined out, using the world seed. Seed-Ray knows exactly where vanilla put
 * every ore; nothing in vanilla removes an ore after generation, so a predicted ore that is now air was
 * dug out by a player. Player-dug tunnels (straight, flat, narrow passages) are simulated as the rock
 * they were dug from, so the ores that sat in them are predicted and counted as mined.
 * Strip mines, branch mines and quarries show up even when the base itself is
 * hidden, and it works through anti-xray (air is never faked).
 */
public final class MinedOreAnalysis {
    /** Per chunk and layer (index 0 below y=0, 1 above): ores predicted, still there, and dug out. */
    public record ChunkResult(int[] predicted, int[] present, int[] mined, int[] tunnelBlocks, List<BlockPos> minedSpots) {}

    private final SeedOrePredictor.Snapshot snap;

    private MinedOreAnalysis(SeedOrePredictor.Snapshot snap) { this.snap = snap; }

    /** Copies the loaded chunks around a centre (client thread). Analysable chunks are those with all neighbours loaded. */
    public static MinedOreAnalysis prepare(ClientLevel level, int centerX, int centerZ, int radius) {
        return new MinedOreAnalysis(new SeedOrePredictor.Snapshot(level, centerX, centerZ, radius));
    }

    public boolean canAnalyse(int cx, int cz) { return snap.hasArea(cx, cz); }

    /** Runs the ore simulation for the chunks (worker thread). Keys are {@link ChunkPos#pack} of each chunk. */
    public Map<Long, ChunkResult> run(long seed, Collection<int[]> chunks) {
        List<int[]> todo = new ArrayList<>();
        for (int[] c : chunks) if (snap.hasArea(c[0], c[1])) todo.add(c);
        Map<Long, ChunkResult> out = new HashMap<>();
        if (todo.isEmpty()) return out;
        for (int[] c : todo)
            out.put(ChunkPos.pack(c[0], c[1]), new ChunkResult(new int[2], new int[2], new int[2], new int[2], new ArrayList<>()));
        SeedOrePredictor.Tunnels tunnels = new SeedOrePredictor.Tunnels(snap);
        for (SeedOrePredictor.Hit h : SeedOrePredictor.predict(snap, seed, todo, true)) {
            BlockPos p = h.pos();
            ChunkResult r = out.get(ChunkPos.pack(p.getX() >> 4, p.getZ() >> 4));
            if (r == null) continue;
            int layer = p.getY() < 0 ? 0 : 1;
            r.predicted[layer]++;
            BlockState now = snap.get(p.getX(), p.getY(), p.getZ());
            Block b = h.state().getBlock();
            // only air in a dug tunnel counts: an ore predicted into cave air is a prediction miss, not a mined ore
            if (now.isAir() && tunnels.at(p.getX(), p.getY(), p.getZ())) {
                r.mined[layer]++;
                if (r.minedSpots.size() < 64) r.minedSpots.add(p);
            } else if (SeedRayModule.oreName(now.getBlock()) != null && SeedRayModule.oreName(now.getBlock()).equals(SeedRayModule.oreName(b))) {
                r.present[layer]++;
            }
        }
        // dug tunnel volume: tunnel-shaped air blocks per chunk layer (needs no seed, but seed-mined ores confirm it)
        for (int[] c : todo) {
            ChunkResult r = out.get(ChunkPos.pack(c[0], c[1]));
            for (int y = snap.minY; y < snap.minY + snap.height; y++)
                for (int x = c[0] << 4; x < (c[0] << 4) + 16; x++)
                    for (int z = c[1] << 4; z < (c[1] << 4) + 16; z++)
                        if (snap.get(x, y, z).isAir() && tunnels.at(x, y, z)) {
                            r.tunnelBlocks[y < 0 ? 0 : 1]++;
                        }
        }
        return out;
    }

    /** Whether the last analysis saw anti-xray (ores hidden, so "present" counts are unreliable). */
    public static boolean antiXraySeen() { return SeedOrePredictor.lastAntiXray; }
}
