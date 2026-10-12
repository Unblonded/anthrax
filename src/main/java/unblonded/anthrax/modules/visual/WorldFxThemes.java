package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Set-piece effects for the newer world themes: synthwave sun, digital rain, jellyfish, black-sun
 * eclipse, sky lanterns, spirit wisps, meteor shower and a wireframe holo skyline.
 * Must be called while a Gizmos collector is active.
 */
final class WorldFxThemes {
    static final int SUN = 1, RAIN = 2, JELLY = 4, ECLIPSE = 8, LANTERNS = 16, WISPS = 32, METEORS = 64, SKYLINE = 128;

    private static final String GLYPHS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜ0123456789Z:.=*+<>";
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private WorldFxThemes() {}

    static void draw(Minecraft c, int colA, int colB, float str, boolean rainbow, int mask) {
        float pt = c.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 p = c.player.getPosition(pt);
        double t = System.currentTimeMillis() % 3_600_000L * .001;
        float s = .35f + .65f * str;
        if ((mask & SKYLINE) != 0) skyline(p, t, colA, colB, s, rainbow);
        if ((mask & SUN) != 0) sun(p, t, s);
        if ((mask & ECLIPSE) != 0) eclipse(p, t, colA, colB, s);
        if ((mask & METEORS) != 0) meteors(p, t, s);
        if ((mask & LANTERNS) != 0) lanterns(p, t, s);
        if ((mask & JELLY) != 0) jellyfish(p, t, colA, colB, s, rainbow);
        if ((mask & WISPS) != 0) wisps(p, t, colA, colB, s, rainbow);
        if ((mask & RAIN) != 0) rain(p, t, s);
    }

    // ------------------------------------------------------------ synthwave sun on the horizon
    private static void sun(Vec3 p, double t, float s) {
        Vec3 dir = new Vec3(-.94, 0, -.34).normalize();
        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        double R = 22;
        Vec3 c = p.add(dir.scale(72)).add(0, 12, 0);
        int bands = 22;
        for (int i = 0; i < bands; i++) {
            double y0 = R - 2 * R * i / bands, y1 = R - 2 * R * (i + 1) / bands;
            double mid = (y0 + y1) / 2;
            // the classic sliced lower half: gaps widen toward the bottom
            if (mid < 0) {
                double gap = (-mid / R) * .7;
                y1 = y0 - (y0 - y1) * (1 - gap);
            }
            double hw0 = Math.sqrt(Math.max(0, R * R - y0 * y0)), hw1 = Math.sqrt(Math.max(0, R * R - y1 * y1));
            float k = i / (float) (bands - 1);
            int rgb = lerp(0xFFE45C, 0xFF2E8B, k);
            int a = (int) (235 * s);
            quad(c.add(right.scale(-hw0)).add(0, y0, 0), c.add(right.scale(hw0)).add(0, y0, 0),
                    c.add(right.scale(hw1)).add(0, y1, 0), c.add(right.scale(-hw1)).add(0, y1, 0), argb(rgb, a));
        }
        // soft halo rings around the disc
        for (int ring = 1; ring <= 4; ring++) {
            double r = R + ring * 1.6;
            int a = (int) (110 * s / ring);
            Vec3 prev = null;
            for (int i = 0; i <= 64; i++) {
                double q = i / 64.0 * Math.PI * 2;
                Vec3 cur = c.add(right.scale(Math.cos(q) * r)).add(0, Math.sin(q) * r, 0);
                if (prev != null) Gizmos.line(prev, cur, argb(lerp(0xFFB347, 0xFF2E8B, (float) (.5 - .5 * Math.sin(q))), a), 3f + ring);
                prev = cur;
            }
        }
        // horizon line
        Gizmos.line(c.add(right.scale(-R * 2.4)).add(0, -R * .05, 0), c.add(right.scale(R * 2.4)).add(0, -R * .05, 0),
                argb(0xFF4FD8, (int) (200 * s)), 3f);
    }

    // ------------------------------------------------------------ eclipse: black disc with a living corona
    private static void eclipse(Vec3 p, double t, int colA, int colB, float s) {
        Vec3 dir = new Vec3(.32, .58, -.75).normalize();
        Vec3 c = p.add(dir.scale(85));
        Vec3 right = dir.cross(UP).normalize(), up = right.cross(dir).normalize();
        double R = 11;
        // corona rays, flickering and slowly turning
        int rays = 72;
        for (int i = 0; i < rays; i++) {
            double q = i / (double) rays * Math.PI * 2 + t * .05;
            double len = R * (.25 + .55 * hash(i * 7L + (long) (t * 6)) * (.6 + .4 * Math.sin(t * 1.3 + i)));
            Vec3 a = c.add(right.scale(Math.cos(q) * R * 1.02)).add(up.scale(Math.sin(q) * R * 1.02));
            Vec3 b = c.add(right.scale(Math.cos(q) * (R + len))).add(up.scale(Math.sin(q) * (R + len)));
            int rgb = (i & 1) == 0 ? colA : colB;
            Gizmos.line(a, b, argb(rgb, (int) (40 * s)), 7f);
            Gizmos.line(a, b, argb(lerp(rgb, 0xFFFFFF, .4f), (int) (150 * s)), 1.6f);
        }
        // the black disc (a fan of quads) and its bright rim
        int seg = 36;
        for (int i = 0; i < seg; i++) {
            double q0 = i * Math.PI * 2 / seg, q1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 e0 = c.add(right.scale(Math.cos(q0) * R)).add(up.scale(Math.sin(q0) * R));
            Vec3 e1 = c.add(right.scale(Math.cos(q1) * R)).add(up.scale(Math.sin(q1) * R));
            quad(c, e0, e1, e1, argb(0x040208, (int) (245 * s)));
            Gizmos.line(e0, e1, argb(0xFFFFFF, (int) (230 * s)), 2.5f);
            Gizmos.line(e0, e1, argb(colA, (int) (90 * s)), 9f);
        }
        // the "diamond ring" flare creeping round the rim
        double fq = t * .2;
        Vec3 flare = c.add(right.scale(Math.cos(fq) * R)).add(up.scale(Math.sin(fq) * R));
        Gizmos.point(flare, argb(0xFFFFFF, (int) (255 * s)), 14f);
        Gizmos.point(flare, argb(colB, (int) (120 * s)), 30f);
    }

    // ------------------------------------------------------------ meteor shower
    private static void meteors(Vec3 p, double t, float s) {
        for (int i = 0; i < 6; i++) {
            double period = 2.6 + hash(i * 31L) * 2;
            long slot = (long) Math.floor(t / period + hash(i * 17L));
            double q = t / period + hash(i * 17L) - slot;
            if (q > .45 || hash(slot * 13 + i) < .25) continue;
            double k = q / .45;
            long key = slot * 101 + i;
            double ang = hash(key) * Math.PI * 2, dist = 18 + hash(key + 1) * 30;
            double heading = ang + 1.2 + hash(key + 2) * .8;
            Vec3 start = new Vec3(p.x + Math.cos(ang) * dist, p.y + 48 + hash(key + 3) * 18, p.z + Math.sin(ang) * dist);
            Vec3 vel = new Vec3(Math.cos(heading) * 26, -34, Math.sin(heading) * 26);
            Vec3 head = start.add(vel.scale(k));
            int a = (int) (255 * Math.sin(Math.PI * k) * s);
            if (a < 6) continue;
            for (int seg = 0; seg < 10; seg++) {
                double f0 = seg / 10.0, f1 = (seg + 1) / 10.0;
                Vec3 a0 = head.subtract(vel.scale(f0 * .12)), a1 = head.subtract(vel.scale(f1 * .12));
                int rgb = seg < 2 ? 0xFFF6D8 : seg < 5 ? 0xFFB13B : 0xFF4A2A;
                int al = (int) (a * (1 - f0));
                Gizmos.line(a0, a1, argb(rgb, al / 4), 9f * (float) (1 - f0) + 2);
                Gizmos.line(a0, a1, argb(rgb, al), 2.4f * (float) (1 - f0) + .8f);
            }
            Gizmos.point(head, argb(0xFFFFFF, a), 7f);
        }
    }

    // ------------------------------------------------------------ sky lanterns drifting upward
    private static void lanterns(Vec3 p, double t, float s) {
        final double R = 20, H = 22;
        for (int i = 0; i < 18; i++) {
            long k = 61000 + i * 11L;
            double dx = wrap(hash(k) * 2 * R + Math.sin(t * .3 + i) * 1.2 - p.x, 2 * R) - R;
            double dz = wrap(hash(k + 1) * 2 * R + Math.cos(t * .27 + i) * 1.2 - p.z, 2 * R) - R;
            double dy = wrap(hash(k + 2) * H + t * (.35 + hash(k + 3) * .25) - p.y, H) - 4;
            double d2 = (dx * dx + dz * dz) / (R * R);
            float fade = (float) (Math.max(0, 1 - d2) * Math.min(1, (H - 4 - dy) / 5) * Math.min(1, (dy + 4) / 3));
            if (fade < .03f) continue;
            double x = p.x + dx, y = p.y + dy, z = p.z + dz;
            float flick = .8f + .2f * (float) Math.sin(t * 9 + i * 3.1);
            int body = argb(0xFF9A3C, (int) (150 * fade * s * flick));
            Gizmos.cuboid(new AABB(x - .18, y, z - .18, x + .18, y + .5, z + .18),
                    GizmoStyle.strokeAndFill(argb(0xFFD08A, (int) (200 * fade * s)), 1.2f, body));
            Gizmos.point(new Vec3(x, y + .12, z), argb(0xFFF1C2, (int) (255 * fade * s * flick)), 6f);
            Gizmos.point(new Vec3(x, y + .25, z), argb(0xFF8A2A, (int) (70 * fade * s)), 26f);
        }
    }

    // ------------------------------------------------------------ bioluminescent jellyfish
    private static void jellyfish(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        final double R = 16, H = 14;
        for (int i = 0; i < 8; i++) {
            long k = 72000 + i * 13L;
            double dx = wrap(hash(k) * 2 * R + Math.sin(t * .2 + i) * 2 - p.x, 2 * R) - R;
            double dz = wrap(hash(k + 1) * 2 * R + Math.cos(t * .17 + i) * 2 - p.z, 2 * R) - R;
            double dy = wrap(hash(k + 2) * H + t * .3 - p.y, H) - H / 2 + 3;
            double d2 = (dx * dx + dz * dz) / (R * R);
            float fade = (float) (Math.max(0, 1 - d2) * Math.max(0, 1 - Math.pow((dy - 3) / (H / 2), 2)));
            int a = (int) (220 * fade * s);
            if (a < 6) continue;
            Vec3 c = new Vec3(p.x + dx, p.y + dy, p.z + dz);
            double r = .7 + hash(k + 3) * .6;
            float beat = .5f + .5f * (float) Math.sin(t * 2.2 + i * 1.7);
            int rgb = rainbow ? WorldFxExtra.hsl(t * 30 + i * 45, .9f, .65f) : ((i & 1) == 0 ? colA : colB);
            // bell: latitude rings plus meridians, squeezing on each beat
            Vec3[][] grid = new Vec3[5][13];
            for (int lat = 0; lat < 5; lat++) {
                double phi = lat / 4.0 * Math.PI / 2;
                double rr = r * Math.cos(phi) * (1 - .18 * beat), yy = r * Math.sin(phi) * (.75 + .2 * beat);
                for (int j = 0; j <= 12; j++) {
                    double q = j / 12.0 * Math.PI * 2 + t * .3;
                    grid[lat][j] = c.add(Math.cos(q) * rr, yy, Math.sin(q) * rr);
                }
            }
            for (int lat = 0; lat < 5; lat++)
                for (int j = 0; j < 12; j++) seg(grid[lat][j], grid[lat][j + 1], rgb, lat == 0 ? a : a * 2 / 3, lat == 0 ? 1.8f : 1.1f, lat == 0);
            for (int j = 0; j < 12; j += 2)
                for (int lat = 0; lat < 4; lat++) seg(grid[lat][j], grid[lat + 1][j], rgb, a / 2, 1f, false);
            // trailing tentacles
            for (int tn = 0; tn < 6; tn++) {
                double q = tn / 6.0 * Math.PI * 2;
                Vec3 prev = c.add(Math.cos(q) * r * .6, 0, Math.sin(q) * r * .6);
                for (int sgm = 1; sgm <= 7; sgm++) {
                    double sway = Math.sin(t * 1.8 + sgm * .7 + tn) * .18 * sgm / 7;
                    Vec3 cur = c.add(Math.cos(q) * (r * .6 + sway), -sgm * .38, Math.sin(q) * (r * .6 + sway));
                    seg(prev, cur, rgb, (int) (a * (1 - sgm / 8.0)), .9f, sgm < 3);
                    prev = cur;
                }
            }
            Gizmos.point(c.add(0, r * .3, 0), argb(0xFFFFFF, a), 6f + 4f * beat);
        }
    }

    // ------------------------------------------------------------ spirit wisps with comet tails
    private static void wisps(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        for (int i = 0; i < 9; i++) {
            long k = 83000 + i * 7L;
            double radius = 2.6 + hash(k) * 4, speed = (.4 + hash(k + 1) * .5) * ((i & 1) == 0 ? 1 : -1);
            double phase = hash(k + 2) * Math.PI * 2, bob = hash(k + 3) * Math.PI * 2;
            int rgb = rainbow ? WorldFxExtra.hsl(t * 40 + i * 40, .9f, .66f) : ((i % 3 == 0) ? colB : colA);
            Vec3 prev = null;
            for (int tr = 0; tr < 12; tr++) {
                double tt = t - tr * .05;
                double ang = phase + tt * speed;
                Vec3 cur = new Vec3(p.x + Math.cos(ang) * radius, p.y + 1.2 + Math.sin(tt * 1.4 + bob) * .9 + Math.sin(tt * 3.1 + bob) * .2,
                        p.z + Math.sin(ang) * radius);
                if (prev != null) seg(prev, cur, rgb, (int) (200 * s * (1 - tr / 12.0)), 2.2f * (float) (1 - tr / 12.0) + .4f, tr < 4);
                else {
                    Gizmos.point(cur, argb(0xFFFFFF, (int) (255 * s)), 6f);
                    Gizmos.point(cur, argb(rgb, (int) (90 * s)), 18f);
                }
                prev = cur;
            }
        }
    }

    // ------------------------------------------------------------ digital rain (falling glyph columns)
    private static void rain(Vec3 p, double t, float s) {
        final double R = 12, H = 16;
        float scale = TextGizmo.Style.DEFAULT_SCALE * 1.6f;
        for (int col = 0; col < 26; col++) {
            long k = 94000 + col * 17L;
            double dx = wrap(hash(k) * 2 * R - p.x, 2 * R) - R;
            double dz = wrap(hash(k + 1) * 2 * R - p.z, 2 * R) - R;
            double d2 = (dx * dx + dz * dz) / (R * R);
            if (d2 > 1 || dx * dx + dz * dz < 2.2) continue;
            double speed = 3 + hash(k + 2) * 5;
            double head = p.y + 9 - ((t * speed + hash(k + 3) * H) % H);
            float fade = (float) (1 - d2);
            for (int g = 0; g < 10; g++) {
                double y = head + g * .42;
                double band = (y - p.y) / 9;
                float vfade = (float) Math.max(0, 1 - band * band);
                int a = (int) (255 * fade * vfade * s * (1 - g / 10f));
                if (a < 10) continue;
                char ch = GLYPHS.charAt((int) (hash(k * 31 + g * 7 + (long) (t * (g == 0 ? 14 : 4))) * GLYPHS.length()));
                int rgb = g == 0 ? 0xE6FFE6 : g < 3 ? 0x7CFF8A : 0x1FD655;
                Gizmos.billboardText(String.valueOf(ch), new Vec3(p.x + dx, y, p.z + dz),
                        TextGizmo.Style.forColorAndCentered(argb(rgb, a)).withScale(scale));
            }
        }
    }

    // ------------------------------------------------------------ wireframe holo skyline in the distance
    private static void skyline(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        final int CELL = 26, RANGE = 4;
        int cx0 = (int) Math.floor(p.x / CELL), cz0 = (int) Math.floor(p.z / CELL);
        for (int ix = -RANGE; ix <= RANGE; ix++)
            for (int iz = -RANGE; iz <= RANGE; iz++) {
                int cx = cx0 + ix, cz = cz0 + iz;
                long key = cx * 92837111L ^ cz * 689287499L;
                if (hash(key) < .45) continue;
                double x = (cx + .2 + hash(key + 1) * .6) * CELL, z = (cz + .2 + hash(key + 2) * .6) * CELL;
                double dist = Math.hypot(x - p.x, z - p.z);
                float fade = (float) (Math.min(1, (dist - 40) / 14) * Math.min(1, (CELL * RANGE + 4 - dist) / 18));
                if (fade <= .02f) continue;
                double w = 3 + hash(key + 3) * 5, d = 3 + hash(key + 4) * 5, h = 22 + hash(key + 5) * 60;
                double base = p.y - 12;
                int rgb = rainbow ? WorldFxExtra.hsl(hash(key + 6) * 360 + t * 20, .9f, .6f) : (hash(key + 6) < .5 ? colA : colB);
                int a = (int) (150 * fade * s);
                double x0 = x - w / 2, x1 = x + w / 2, z0 = z - d / 2, z1 = z + d / 2;
                double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
                for (double[] cn : corners) seg(new Vec3(cn[0], base, cn[1]), new Vec3(cn[0], base + h, cn[1]), rgb, a, 1.3f, true);
                // floor bands, a few "lit" ones flicker brighter
                for (double fy = base + 6; fy < base + h; fy += 6) {
                    boolean lit = hash(key * 7 + (long) fy + (long) (t * 2)) > .8;
                    int la = lit ? a * 2 : a / 2;
                    for (int e = 0; e < 4; e++) {
                        double[] c0 = corners[e], c1 = corners[(e + 1) % 4];
                        seg(new Vec3(c0[0], fy, c0[1]), new Vec3(c1[0], fy, c1[1]), rgb, la, lit ? 1.6f : .9f, lit);
                    }
                }
                // roof outline and blinking antenna
                for (int e = 0; e < 4; e++) {
                    double[] c0 = corners[e], c1 = corners[(e + 1) % 4];
                    seg(new Vec3(c0[0], base + h, c0[1]), new Vec3(c1[0], base + h, c1[1]), rgb, a, 1.6f, true);
                }
                seg(new Vec3(x, base + h, z), new Vec3(x, base + h + 6, z), rgb, a, 1f, false);
                if ((long) (t * 1.5 + hash(key + 7) * 10) % 2 == 0)
                    Gizmos.point(new Vec3(x, base + h + 6, z), argb(0xFF3B5C, (int) (255 * fade * s)), 7f);
            }
    }

    // ------------------------------------------------------------ helpers
    /** Filled quad, drawn with both windings so it shows from either side. */
    private static void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        GizmoStyle fill = GizmoStyle.fill(argb);
        Gizmos.rect(a, b, c, d, fill);
        Gizmos.rect(d, c, b, a, fill);
    }

    private static void seg(Vec3 a, Vec3 b, int rgb, int alpha, float w, boolean glow) {
        if (alpha < 3) return;
        if (glow) {
            Gizmos.line(a, b, argb(rgb, alpha / 7), w * 4.5f);
            Gizmos.line(a, b, argb(rgb, alpha / 3), w * 2.4f);
        }
        Gizmos.line(a, b, argb(rgb, alpha), w);
    }

    private static int argb(int rgb, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF); }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static double wrap(double v, double period) {
        double m = v % period;
        return m < 0 ? m + period : m;
    }

    private static double hash(long n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 32;
        return (x & 0xFFFFFF) / (double) 0x1000000;
    }
}
