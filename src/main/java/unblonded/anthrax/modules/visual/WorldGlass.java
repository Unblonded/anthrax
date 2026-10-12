package unblonded.anthrax.modules.visual;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "Liquid Glass" world theme: refracted-light caustics rippling across the ground, glass bubbles with
 * rim light and specular highlights, slowly turning glass panels, and liquid ripples under the player.
 * Everything is translucent filled geometry rather than wireframe. Call inside an active Gizmos collector.
 */
final class WorldGlass {
    static final int CAUSTICS = 1, BUBBLES = 2, PANELS = 4, RIPPLES = 8;

    private static final double R = 13;
    private static final int MAX_FACES = 420;
    private static final int[] faces = new int[MAX_FACES * 3];
    private static int faceCount;
    private static long lastScan;
    private static BlockPos lastCenter = BlockPos.ZERO;
    private static Vec3 lastStep;
    private static final double[] ripples = new double[6 * 4]; // x, y, z, birth time
    private static int rippleHead;

    // camera basis for billboards, refreshed every frame
    private static Vec3 camPos = Vec3.ZERO, right = new Vec3(1, 0, 0), up = new Vec3(0, 1, 0), forward = new Vec3(0, 0, 1);

    private WorldGlass() {}

    static void draw(Minecraft c, int tint, int tint2, float strength, int mask) {
        float pt = c.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 p = c.player.getPosition(pt);
        double t = System.currentTimeMillis() * .001;
        float s = .3f + .7f * strength;
        cameraBasis(c.gameRenderer.mainCamera());
        if ((mask & CAUSTICS) != 0) { scan(c); caustics(p, t, tint, tint2, s); }
        if ((mask & RIPPLES) != 0) ripples(c, p, t, tint, s);
        if ((mask & PANELS) != 0) panels(p, t, tint, tint2, s);
        if ((mask & BUBBLES) != 0) bubbles(p, t, tint, tint2, s);
    }

    private static void cameraBasis(Camera camera) {
        camPos = camera.position();
        double yaw = Math.toRadians(camera.yRot()), pitch = Math.toRadians(camera.xRot());
        forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 r = forward.cross(new Vec3(0, 1, 0));
        right = r.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : r.normalize();
        up = right.cross(forward).normalize();
    }

    // ------------------------------------------------------------ caustics on the ground
    private static void scan(Minecraft c) {
        long now = System.currentTimeMillis();
        BlockPos center = c.player.blockPosition();
        if (now - lastScan < 350 && center.equals(lastCenter)) return;
        lastScan = now;
        lastCenter = center;
        ClientLevel level = c.level;
        List<int[]> cand = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r = (int) R;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r * r) continue;
            for (int dy = -4; dy <= 3; dy++) {
                int x = center.getX() + dx, y = center.getY() + dy, z = center.getZ() + dz;
                m.set(x, y, z);
                BlockState st = level.getBlockState(m);
                if (st.isAir() || !st.getFluidState().isEmpty() || !st.isSolidRender()) continue;
                m.set(x, y + 1, z);
                if (!level.getBlockState(m).isAir()) continue;
                cand.add(new int[]{x, y, z, dx * dx + dz * dz + dy * dy * 4});
            }
        }
        cand.sort(Comparator.comparingInt(a -> a[3]));
        faceCount = Math.min(MAX_FACES, cand.size());
        for (int i = 0; i < faceCount; i++) {
            int[] f = cand.get(i);
            faces[i * 3] = f[0]; faces[i * 3 + 1] = f[1]; faces[i * 3 + 2] = f[2];
        }
    }

    /** Classic caustic look: sum of drifting waves, sharpened so only the bright filaments remain. */
    private static double caustic(double x, double z, double t) {
        double v = Math.sin(x * 1.7 + t * 1.1 + Math.sin(z * 1.3 + t * .7) * 1.6)
                + Math.sin(z * 1.9 - t * .9 + Math.sin(x * 1.1 - t * .5) * 1.4)
                + Math.sin((x + z) * 1.2 + t * 1.4);
        double k = 1 - Math.abs(v) / 3.0;
        return Math.pow(Math.max(0, k), 6);
    }

    private static void caustics(Vec3 p, double t, int tint, int tint2, float s) {
        for (int i = 0; i < faceCount; i++) {
            int x = faces[i * 3], y = faces[i * 3 + 1], z = faces[i * 3 + 2];
            double dist = Math.hypot(x + .5 - p.x, z + .5 - p.z);
            float fall = (float) Math.max(0, 1 - dist / R);
            if (fall <= 0) continue;
            double Y = y + 1.006;
            // Two sub-cells per block axis give the filaments some shape without many quads.
            for (int sx = 0; sx < 2; sx++) for (int sz = 0; sz < 2; sz++) {
                double cx = x + sx * .5, cz = z + sz * .5;
                double k = caustic(cx + .25, cz + .25, t);
                int a = (int) (150 * k * fall * s);
                if (a < 5) continue;
                int rgb = lerp(tint, 0xFFFFFF, .35f + .5f * (float) k);
                rgb = lerp(rgb, tint2, (float) (.5 + .5 * Math.sin(cx * .4 + cz * .3 + t * .6)) * .35f);
                Gizmos.rect(new Vec3(cx, Y, cz), new Vec3(cx + .5, Y, cz), new Vec3(cx + .5, Y, cz + .5), new Vec3(cx, Y, cz + .5),
                        GizmoStyle.fill(argb(rgb, a)));
            }
            // Faint glassy sheen over the whole tile so the ground reads as wet glass.
            int sheen = (int) (22 * fall * s);
            if (sheen > 3) Gizmos.rect(new Vec3(x, Y - .001, z), new Vec3(x + 1, Y - .001, z), new Vec3(x + 1, Y - .001, z + 1),
                    new Vec3(x, Y - .001, z + 1), GizmoStyle.fill(argb(tint, sheen)));
        }
    }

    // ------------------------------------------------------------ ripples where you step
    private static void ripples(Minecraft c, Vec3 p, double t, int tint, float s) {
        Vec3 feet = new Vec3(p.x, Math.floor(p.y) + .02, p.z);
        if (c.player.onGround() && (lastStep == null || lastStep.distanceToSqr(feet) > 1.6 * 1.6)) {
            lastStep = feet;
            int i = rippleHead++ % (ripples.length / 4) * 4;
            ripples[i] = feet.x; ripples[i + 1] = feet.y; ripples[i + 2] = feet.z; ripples[i + 3] = t;
        }
        for (int i = 0; i < ripples.length; i += 4) {
            double age = t - ripples[i + 3];
            if (ripples[i + 3] == 0 || age > 1.6) continue;
            Vec3 o = new Vec3(ripples[i], ripples[i + 1], ripples[i + 2]);
            for (int ring = 0; ring < 3; ring++) {
                double ra = age - ring * .18;
                if (ra <= 0) continue;
                double radius = .25 + ra * 2.4;
                float fade = (float) Math.max(0, 1 - ra / 1.4);
                int a = (int) (170 * fade * fade * s);
                if (a < 5) continue;
                ring(o, radius, lerp(tint, 0xFFFFFF, .5f), a, 2.2f - ring * .5f);
            }
        }
    }

    // ------------------------------------------------------------ slowly turning glass panels
    private static void panels(Vec3 p, double t, int tint, int tint2, float s) {
        for (int i = 0; i < 7; i++) {
            long k = 7700 + i * 29L;
            double ang = hash(k) * Math.PI * 2 + t * (.05 + hash(k + 1) * .05);
            double dist = 6 + hash(k + 2) * 9;
            Vec3 c = new Vec3(p.x + Math.cos(ang) * dist, p.y + 2.5 + hash(k + 3) * 6 + Math.sin(t * .6 + i) * .5,
                    p.z + Math.sin(ang) * dist);
            double spin = t * (.25 + hash(k + 4) * .3) + i;
            double tilt = .35 * Math.sin(t * .4 + i * 1.3);
            double size = .9 + hash(k + 5) * 1.1;
            // Hexagonal pane in a rotated vertical plane.
            Vec3 ax = new Vec3(Math.cos(spin), 0, Math.sin(spin));
            Vec3 ay = new Vec3(-Math.sin(spin) * Math.sin(tilt), Math.cos(tilt), Math.cos(spin) * Math.sin(tilt));
            Vec3[] hex = new Vec3[6];
            for (int h = 0; h < 6; h++) {
                double a = h * Math.PI / 3 + Math.PI / 6;
                hex[h] = c.add(ax.scale(Math.cos(a) * size)).add(ay.scale(Math.sin(a) * size));
            }
            // Fresnel: panes seen edge-on look brighter, face-on almost clear.
            Vec3 normal = ax.cross(ay).normalize();
            Vec3 view = c.subtract(camPos).normalize();
            float fresnel = (float) Math.pow(1 - Math.abs(normal.dot(view)), 2);
            int body = argb(lerp(tint, tint2, (float) hash(k + 6)), (int) ((26 + 70 * fresnel) * s));
            for (int h = 0; h < 6; h += 2) {
                Gizmos.rect(c, hex[h], hex[(h + 1) % 6], hex[(h + 2) % 6], GizmoStyle.fill(body));
            }
            int edge = lerp(tint, 0xFFFFFF, .6f);
            for (int h = 0; h < 6; h++) {
                Gizmos.line(hex[h], hex[(h + 1) % 6], argb(edge, (int) ((70 + 120 * fresnel) * s)), 1.4f);
            }
            // A specular glint slides along the rim.
            double g = (t * .5 + hash(k + 7)) % 1 * 6;
            int e0 = (int) g;
            Vec3 a0 = hex[e0 % 6], a1 = hex[(e0 + 1) % 6];
            double f = g - e0;
            Vec3 glint = new Vec3(a0.x + (a1.x - a0.x) * f, a0.y + (a1.y - a0.y) * f, a0.z + (a1.z - a0.z) * f);
            Gizmos.point(glint, argb(0xFFFFFF, (int) (230 * s)), 5f);
        }
    }

    // ------------------------------------------------------------ glass bubbles
    private static void bubbles(Vec3 p, double t, int tint, int tint2, float s) {
        for (int i = 0; i < 14; i++) {
            long k = 9100 + i * 17L;
            double rise = (t * (.12 + hash(k) * .12) + hash(k + 1)) % 1;
            double ang = hash(k + 2) * Math.PI * 2 + Math.sin(t * .3 + i) * .3;
            double dist = 3 + hash(k + 3) * 10;
            Vec3 c = new Vec3(p.x + Math.cos(ang) * dist + Math.sin(t * .9 + i) * .3,
                    p.y - 1 + rise * 9, p.z + Math.sin(ang) * dist + Math.cos(t * .8 + i) * .3);
            double radius = .18 + hash(k + 4) * .32;
            float life = (float) Math.min(1, Math.min(rise * 6, (1 - rise) * 4));
            if (life <= .02f) continue;
            bubble(c, radius, lerp(tint, tint2, (float) hash(k + 5)), life * s);
        }
    }

    private static void bubble(Vec3 c, double r, int tint, float a) {
        // translucent body
        disc(c, r, argb(lerp(tint, 0xFFFFFF, .25f), (int) (38 * a)), 14);
        // bright fresnel rim
        Vec3 prev = null;
        for (int i = 0; i <= 18; i++) {
            double ang = i * Math.PI * 2 / 18;
            Vec3 cur = c.add(right.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r));
            if (prev != null) Gizmos.line(prev, cur, argb(lerp(tint, 0xFFFFFF, .55f), (int) (175 * a)), 1.3f);
            prev = cur;
        }
        // specular highlight (upper left) and a faint bounce light (lower right)
        Vec3 spec = c.add(right.scale(-r * .38)).add(up.scale(r * .42)).subtract(forward.scale(r * .1));
        disc(spec, r * .2, argb(0xFFFFFF, (int) (210 * a)), 8);
        Vec3 bounce = c.add(right.scale(r * .4)).add(up.scale(-r * .45));
        disc(bounce, r * .12, argb(lerp(tint, 0xFFFFFF, .3f), (int) (90 * a)), 6);
    }

    /** Camera-facing filled disc built from a triangle fan (degenerate quads). */
    private static void disc(Vec3 c, double r, int color, int segments) {
        GizmoStyle style = GizmoStyle.fill(color);
        Vec3 prev = c.add(right.scale(r));
        for (int i = 1; i <= segments; i++) {
            double ang = i * Math.PI * 2 / segments;
            Vec3 cur = c.add(right.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r));
            Gizmos.rect(c, prev, cur, cur, style);
            prev = cur;
        }
    }

    private static void ring(Vec3 o, double radius, int rgb, int alpha, float width) {
        int n = 36;
        Vec3 prev = new Vec3(o.x + radius, o.y, o.z);
        for (int i = 1; i <= n; i++) {
            double ang = i * Math.PI * 2 / n;
            Vec3 cur = new Vec3(o.x + Math.cos(ang) * radius, o.y, o.z + Math.sin(ang) * radius);
            Gizmos.line(prev, cur, argb(rgb, alpha / 4), width * 3f);
            Gizmos.line(prev, cur, argb(rgb, alpha), width);
            prev = cur;
        }
    }

    private static int argb(int rgb, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF); }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static double hash(long n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29; x *= 0xBF58476D1CE4E5B9L; x ^= x >>> 32;
        return (x & 0xFFFFFF) / (double) 0x1000000;
    }
}
