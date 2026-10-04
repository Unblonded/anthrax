package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Must be called while a Gizmos collector is active (inside WorldThemeModule.renderParticles). */
final class WorldFx {
    private static final double R = 14;
    private static final int MAX_FACES = 350;
    private static final int[] faces = new int[MAX_FACES * 3];
    private static int faceCount;
    private static long lastScan;
    private static BlockPos lastCenter = BlockPos.ZERO;

    private WorldFx() {}

    static void draw(Minecraft c, int colA, int colB, float str, boolean grid, boolean bolts, boolean target) {
        float pt = c.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 p = c.player.getPosition(pt);
        double t = System.currentTimeMillis() * .001;
        float s = .35f + .65f * str;
        if (grid) {
            scan(c);
            double wave = (t * 7) % (R + 4);
            grid(p, wave, colA, colB, s);
            rings(p, wave, colA, s);
        }
        if (bolts) lightning(p, t, colA, s);
        if (target) target(c, t, colA, colB, s);
    }

    // ------------------------------------------------------------ neon block-edge grid
    private static void scan(Minecraft c) {
        long now = System.currentTimeMillis();
        BlockPos center = c.player.blockPosition();
        if (now - lastScan < 400 && center.equals(lastCenter)) return;
        lastScan = now;
        lastCenter = center;

        ClientLevel level = c.level;
        List<int[]> cand = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r = (int) R;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int dy = -4; dy <= 3; dy++) {
                    int x = center.getX() + dx, y = center.getY() + dy, z = center.getZ() + dz;
                    m.set(x, y, z);
                    BlockState st = level.getBlockState(m);
                    if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                    m.set(x, y + 1, z);
                    if (!level.getBlockState(m).isAir()) continue;
                    cand.add(new int[]{x, y, z, dx * dx + dz * dz + dy * dy});
                }
            }
        }
        cand.sort(Comparator.comparingInt(a -> a[3]));
        faceCount = Math.min(MAX_FACES, cand.size());
        for (int i = 0; i < faceCount; i++) {
            int[] f = cand.get(i);
            faces[i * 3] = f[0];
            faces[i * 3 + 1] = f[1];
            faces[i * 3 + 2] = f[2];
        }
    }

    private static void grid(Vec3 p, double wave, int colA, int colB, float s) {
        for (int i = 0; i < faceCount; i++) {
            int x = faces[i * 3], y = faces[i * 3 + 1], z = faces[i * 3 + 2];
            double dist = Math.hypot(x + .5 - p.x, z + .5 - p.z);
            double d = dist - wave;
            float k = (float) Math.exp(-d * d / (2 * 1.5 * 1.5));
            float fall = (float) Math.max(0, 1 - dist / R);
            int a = (int) (255 * Math.min(1f, (.12f + .88f * k) * fall * s * 1.4f));
            if (a < 6) continue;
            int rgb = lerpRgb(colB, colA, k);
            double Y = y + 1.01;
            Vec3 v0 = new Vec3(x, Y, z), v1 = new Vec3(x + 1, Y, z);
            Vec3 v2 = new Vec3(x + 1, Y, z + 1), v3 = new Vec3(x, Y, z + 1);
            float w = 1.2f + 2.2f * k;
            boolean glow = k > .25f;
            seg(v0, v1, rgb, a, w, glow);
            seg(v1, v2, rgb, a, w, glow);
            seg(v2, v3, rgb, a, w, glow);
            seg(v3, v0, rgb, a, w, glow);
        }
    }

    private static void rings(Vec3 p, double wave, int colA, float s) {
        double[] radii = {wave, wave - 4.5};
        for (double r : radii) {
            if (r < .3) continue;
            int a = (int) (255 * Math.max(0, 1 - r / R) * s);
            if (a < 6) continue;
            int n = 48;
            Vec3 prev = new Vec3(p.x + r, p.y + .03, p.z);
            for (int i = 1; i <= n; i++) {
                double ang = i * Math.PI * 2 / n;
                Vec3 cur = new Vec3(p.x + Math.cos(ang) * r, p.y + .03, p.z + Math.sin(ang) * r);
                seg(prev, cur, colA, a, 2.2f, true);
                prev = cur;
            }
        }
    }

    // ------------------------------------------------------------ lightning
    private static void lightning(Vec3 p, double t, int col, float s) {
        long bucket = (long) (t * 3);
        double f = t * 3 - bucket;
        if (hash(bucket) < .78) return;
        float flick = hash(bucket * 13 + (long) (t * 30)) > .35 ? 1f : .3f;
        float fade = (float) Math.pow(1 - f, 2) * flick * s;
        int a = (int) (255 * Math.min(1f, fade * 1.3f));
        if (a < 6) return;

        double ang = hash(bucket * 7 + 1) * Math.PI * 2;
        double dist = 12 + hash(bucket * 7 + 2) * 18;
        double topY = p.y + 40 + hash(bucket * 7 + 3) * 10;
        int n = 14;
        double stepY = (topY - p.y) / n;
        Vec3[] pts = new Vec3[n + 1];
        double bx = p.x + Math.cos(ang) * dist, bz = p.z + Math.sin(ang) * dist;
        pts[0] = new Vec3(bx, topY, bz);
        for (int k = 1; k <= n; k++) {
            bx += (hash(bucket * 31 + k) * 2 - 1) * 2.4;
            bz += (hash(bucket * 37 + k) * 2 - 1) * 2.4;
            pts[k] = new Vec3(bx, topY - k * stepY, bz);
        }
        for (int k = 0; k < n; k++) bolt(pts[k], pts[k + 1], col, a);

        // branch
        Vec3 prev = pts[5];
        double dirx = hash(bucket * 41) * 2 - 1, dirz = hash(bucket * 43) * 2 - 1;
        for (int k = 1; k <= 7; k++) {
            Vec3 cur = new Vec3(prev.x + dirx * 2.2 + (hash(bucket * 53 + k) * 2 - 1) * 1.6,
                    prev.y - stepY, prev.z + dirz * 2.2 + (hash(bucket * 59 + k) * 2 - 1) * 1.6);
            bolt(prev, cur, col, (int) (a * .6f));
            prev = cur;
        }
    }

    private static void bolt(Vec3 a, Vec3 b, int col, int alpha) {
        Gizmos.line(a, b, argb(col, alpha / 8), 8f);
        Gizmos.line(a, b, argb(col, alpha / 3), 4f);
        Gizmos.line(a, b, argb(0xFFFFFF, alpha), 1.6f);
    }

    // ------------------------------------------------------------ targeted block outline
    private static void target(Minecraft c, double t, int colA, int colB, float s) {
        if (!(c.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos bp = hit.getBlockPos();
        double e = .003;
        double x0 = bp.getX() - e, y0 = bp.getY() - e, z0 = bp.getZ() - e;
        double x1 = bp.getX() + 1 + e, y1 = bp.getY() + 1 + e, z1 = bp.getZ() + 1 + e;
        float pulse = .5f + .5f * (float) Math.sin(t * 4);
        int rgb = lerpRgb(colA, colB, pulse);
        int a = (int) (255 * Math.min(1f, s * 1.2f));

        Vec3[] cs = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            cs[i] = new Vec3((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
        }
        for (int i = 0; i < 8; i++) {
            for (int bit : new int[]{1, 2, 4}) {
                if ((i & bit) == 0) seg(cs[i], cs[i | bit], rgb, a, 2.5f, true);
            }
        }
        // light beam rising from the top of the block, fading out
        double cx = bp.getX() + .5, cz = bp.getZ() + .5;
        for (int i = 0; i < 8; i++) {
            float fa = 1f - i / 8f;
            seg(new Vec3(cx, y1 + i * .5, cz), new Vec3(cx, y1 + (i + 1) * .5, cz), rgb, (int) (a * fa * .8f), 1.6f, true);
        }
    }

    // ------------------------------------------------------------ helpers
    private static void seg(Vec3 a, Vec3 b, int rgb, int alpha, float w, boolean glow) {
        if (glow) {
            Gizmos.line(a, b, argb(rgb, alpha / 7), w * 4.5f);
            Gizmos.line(a, b, argb(rgb, alpha / 3), w * 2.4f);
        }
        Gizmos.line(a, b, argb(rgb, alpha), w);
    }

    private static int argb(int rgb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static double hash(long n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 32;
        return (x & 0xFFFFFF) / (double) 0x1000000;
    }
}
