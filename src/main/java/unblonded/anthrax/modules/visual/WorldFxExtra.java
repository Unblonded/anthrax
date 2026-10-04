package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Must be called while a Gizmos collector is active. */
final class WorldFxExtra {
    static final int RADAR = 1, PILLARS = 2, SHARDS = 4, AURORA = 8, ENTITIES = 16,
            ORBITALS = 32, COMETS = 64, CONSTELLATION = 128, RUNES = 256, RIFTS = 512,
            NEBULA = 1024, STARDUST = 2048;
    private static final double[][] OCTA = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private WorldFxExtra() {}

    static void draw(Minecraft c, int colA, int colB, float str, boolean rainbow, int mask) {
        float pt = c.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 p = c.player.getPosition(pt);
        double t = System.currentTimeMillis() * .001;
        float s = .35f + .65f * str;
        if ((mask & AURORA) != 0) aurora(p, t, colA, colB, s, rainbow);
        if ((mask & PILLARS) != 0) pillars(p, t, colA, colB, s, rainbow);
        if ((mask & SHARDS) != 0) shards(p, t, colA, colB, s, rainbow);
        if ((mask & RADAR) != 0) radar(p, t, colA, colB, s, rainbow);
        if ((mask & ENTITIES) != 0) entities(c, p, colA, colB, s);
        if ((mask & ORBITALS) != 0) orbitals(p, t, colA, colB, s, rainbow);
        if ((mask & COMETS) != 0) comets(p, t, colA, colB, s, rainbow);
        if ((mask & CONSTELLATION) != 0) constellation(p, t, colA, colB, s, rainbow);
        if ((mask & RUNES) != 0) runes(p, t, colA, colB, s, rainbow);
        if ((mask & RIFTS) != 0) rifts(p, t, colA, colB, s, rainbow);
        if ((mask & NEBULA) != 0) nebula(p, t, colA, colB, s, rainbow);
        if ((mask & STARDUST) != 0) stardust(p, t, colA, colB, s, rainbow);
    }

    // ------------------------------------------------------------ linked stars overhead
    private static void constellation(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        Vec3[] pts = new Vec3[28];
        for (int i = 0; i < pts.length; i++) {
            long k = 23000 + i * 13L;
            double a = hash(k) * Math.PI * 2 + t * .035;
            double r = 6 + hash(k + 1) * 16;
            double drift = Math.sin(t * .32 + i * 2.1) * .7;
            pts[i] = new Vec3(p.x + Math.cos(a) * (r + drift),
                    p.y + 8 + hash(k + 2) * 14 + Math.sin(t * .5 + i) * .45,
                    p.z + Math.sin(a) * (r + drift));
        }
        for (int i = 0; i < pts.length; i++) {
            int rgb = rainbow ? hsl(i * 31 + t * 22, .8f, .68f) : ((i & 1) == 0 ? colA : colB);
            int apha = (int) ((125 + 125 * (.5 + .5 * Math.sin(t * 2.2 + i * 1.7))) * s);
            Gizmos.point(pts[i], argb(0xFFFFFF, apha), 4f);
            // Build a local network from each star's three nearest neighbors.
            int[] nearest = {-1, -1, -1};
            double[] distances = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            for (int j = 0; j < pts.length; j++) {
                if (j == i) continue;
                double d = pts[i].distanceToSqr(pts[j]);
                for (int n = 0; n < nearest.length; n++) {
                    if (d < distances[n]) {
                        for (int shift = nearest.length - 1; shift > n; shift--) {
                            distances[shift] = distances[shift - 1]; nearest[shift] = nearest[shift - 1];
                        }
                        distances[n] = d; nearest[n] = j; break;
                    }
                }
            }
            for (int j : nearest) {
                if (j <= i || j < 0) continue;
                double d = Math.sqrt(pts[i].distanceToSqr(pts[j]));
                if (d > 14) continue;
                float strength = (float) Math.max(.12, 1 - d / 18);
                seg(pts[i], pts[j], rgb, (int) (95 * strength * s), 1.15f, false);
                // A luminous packet travels along the connection like a signal.
                double phase = (t * (.42 + .02 * i) + hash(i * 71L + j) * 4) % 1;
                Vec3 spark = new Vec3(pts[i].x + (pts[j].x - pts[i].x) * phase,
                        pts[i].y + (pts[j].y - pts[i].y) * phase,
                        pts[i].z + (pts[j].z - pts[i].z) * phase);
                Gizmos.point(spark, argb(0xFFFFFF, (int) (220 * strength * s)), 3f);
            }
        }
    }

    // ------------------------------------------------------------ distant flowing nebula ribbons
    private static void nebula(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        for (int band = 0; band < 4; band++) {
            double direction = band * 1.41 + .3;
            double ax = Math.cos(direction), az = Math.sin(direction);
            double px = -az, pz = ax;
            Vec3 prev = null;
            for (int i = 0; i <= 32; i++) {
                double u = i / 32.0 - .5;
                double wave = Math.sin(u * 9 + t * .26 + band) * 3 + Math.sin(u * 19 - t * .18) * 1.1;
                double along = u * 54;
                double across = 37 + band * 5 + wave;
                Vec3 cur = new Vec3(p.x + ax * along + px * across,
                        p.y + 27 + Math.sin(u * 7 + t * .3 + band) * 2.6,
                        p.z + az * along + pz * across);
                float edge = (float) Math.max(0, 1 - Math.abs(u) * 1.85);
                int rgb = rainbow ? hsl(180 + band * 37 + t * 8 + i * 1.8, .82f, .57f)
                        : lerpRgb(colA, colB, (float) (.5 + .5 * Math.sin(u * 4 + band + t * .16)));
                int alpha = (int) (105 * edge * s * (.65 + .35 * Math.sin(t * .7 + i * .4 + band)));
                if (prev != null) seg(prev, cur, rgb, alpha, 3.2f, i % 2 == 0);
                prev = cur;
            }
        }
    }

    // ------------------------------------------------------------ drifting dust with short luminous trails
    private static void stardust(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        for (int i = 0; i < 26; i++) {
            long k = 41000 + i * 19L;
            double phase = (t * (.12 + hash(k) * .16) + hash(k + 1)) % 1;
            double a = hash(k + 2) * Math.PI * 2 + t * (.1 + hash(k + 3) * .12);
            double r = 4 + hash(k + 4) * 17;
            double y = p.y + (hash(k + 5) - .5) * 17 + Math.sin(t * .8 + i * 1.9) * .5;
            Vec3 head = new Vec3(p.x + Math.cos(a) * r, y + (phase - .5) * 2, p.z + Math.sin(a) * r);
            Vec3 tail = new Vec3(head.x - Math.cos(a) * (.35 + phase * .8), head.y - .45, head.z - Math.sin(a) * (.35 + phase * .8));
            int rgb = rainbow ? hsl(t * 28 + i * 17, .72f, .74f) : ((i & 1) == 0 ? colA : colB);
            int alpha = (int) ((100 + 130 * Math.sin(Math.PI * phase)) * s);
            seg(tail, head, rgb, alpha, 1.2f, true);
            Gizmos.point(head, argb(0xFFFFFF, alpha), 2.8f);
        }
    }

    // ------------------------------------------------------------ geometric ground sigils
    private static void runes(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        for (int rune = 0; rune < 3; rune++) {
            double angle = t * (rune % 2 == 0 ? .22 : -.18) + rune * 2.094;
            double x = p.x + Math.cos(angle) * (3.1 + rune * .55);
            double z = p.z + Math.sin(angle) * (3.1 + rune * .55);
            double y = p.y + .035;
            double radius = 1.05 + .18 * Math.sin(t * 1.4 + rune);
            int rgb = rainbow ? hsl(t * 26 + rune * 120, .9f, .62f) : (rune == 1 ? colB : colA);
            int a = (int) (180 * s);
            circleXZ(x, y, z, radius, rgb, a, 28);
            // Rotating inscribed triangle and short radial tick marks.
            Vec3[] v = new Vec3[3];
            for (int j = 0; j < 3; j++) {
                double q = angle * 2 + j * Math.PI * 2 / 3;
                v[j] = new Vec3(x + Math.cos(q) * radius * .78, y + .008, z + Math.sin(q) * radius * .78);
            }
            for (int j = 0; j < 3; j++) seg(v[j], v[(j + 1) % 3], rgb, a, 1.3f, true);
            for (int j = 0; j < 8; j++) {
                double q = angle + j * Math.PI / 4;
                seg(new Vec3(x + Math.cos(q) * radius * .78, y, z + Math.sin(q) * radius * .78),
                        new Vec3(x + Math.cos(q) * radius, y, z + Math.sin(q) * radius), rgb, a / 2, 1f, false);
            }
        }
    }

    // ------------------------------------------------------------ floating rift gates in the near distance
    private static void rifts(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        for (int gate = 0; gate < 2; gate++) {
            double ang = t * .12 + gate * Math.PI;
            double x = p.x + Math.cos(ang) * 8, z = p.z + Math.sin(ang) * 8;
            double y = p.y + 2.4 + Math.sin(t * .8 + gate * 2) * .45;
            int rgb = rainbow ? hsl(t * 30 + gate * 180, .92f, .62f) : (gate == 0 ? colA : colB);
            int a = (int) (190 * s);
            // Two crossed, vertically oriented ellipses form a readable portal silhouette.
            for (int plane = 0; plane < 2; plane++) {
                Vec3 prev = null;
                for (int i = 0; i <= 40; i++) {
                    double q = i / 40.0 * Math.PI * 2 + t * (gate == 0 ? .35 : -.28);
                    double u = Math.cos(q) * 1.45, v = Math.sin(q) * 2.35;
                    double px = plane == 0 ? u : u * .42;
                    double pz = plane == 0 ? u * .42 : u;
                    Vec3 cur = new Vec3(x + px, y + v, z + pz);
                    if (prev != null) seg(prev, cur, rgb, a, 1.7f, i % 3 == 0);
                    prev = cur;
                }
            }
            Gizmos.point(new Vec3(x, y, z), argb(0xFFFFFF, a), 5f);
        }
    }

    // ------------------------------------------------------------ rotating runic rings around the player
    private static void orbitals(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        for (int ring = 0; ring < 3; ring++) {
            double phase = t * (0.65 + ring * .17) + ring * 2.094;
            double radius = 1.35 + ring * .28;
            double y = p.y + 1.0 + ring * .38;
            int rgb = rainbow ? hsl(t * 34 + ring * 120, .88f, .62f) : (ring % 2 == 0 ? colA : colB);
            int a = (int) (205 * s);
            Vec3 prev = null;
            for (int i = 0; i <= 48; i++) {
                double u = i / 48.0 * Math.PI * 2;
                double ang = u + phase;
                double wobble = 1 + .09 * Math.sin(u * 4 + phase * 2);
                Vec3 cur = new Vec3(p.x + Math.cos(ang) * radius * wobble,
                        y + Math.sin(u * 2 + phase) * .22, p.z + Math.sin(ang) * radius * wobble);
                if (prev != null) seg(prev, cur, rgb, a, 1.5f, i % 4 == 0);
                prev = cur;
            }
            // Bright rotating nodes make the rings read as magical instruments.
            for (int node = 0; node < 4; node++) {
                double ang = phase + node * Math.PI / 2;
                Vec3 q = new Vec3(p.x + Math.cos(ang) * radius, y + Math.sin(ang * 2 + phase) * .22,
                        p.z + Math.sin(ang) * radius);
                Gizmos.point(q, argb(0xFFFFFF, a), 4f);
            }
        }
    }

    // ------------------------------------------------------------ short-lived shooting stars in the sky
    private static void comets(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        long slot = (long) Math.floor(t / 2.4);
        double phase = (t / 2.4) - Math.floor(t / 2.4);
        for (int i = 0; i < 3; i++) {
            long key = slot * 17 + i * 7919L;
            if (hash(key) < .48) continue;
            double start = hash(key + 1) * 1.35 - .18;
            if (phase < start || phase > start + .32) continue;
            double q = (phase - start) / .32;
            double x = p.x + (hash(key + 2) * 2 - 1) * 27 + q * 24;
            double z = p.z + (hash(key + 3) * 2 - 1) * 27 - q * 10;
            double y = p.y + 20 + hash(key + 4) * 18 - q * 8;
            int rgb = rainbow ? hsl(hash(key + 5) * 360 + t * 18, .9f, .66f) : (i % 2 == 0 ? colA : colB);
            int a = (int) (230 * Math.sin(Math.PI * q) * s);
            if (a < 5) continue;
            Vec3 head = new Vec3(x, y, z);
            for (int tail = 1; tail <= 8; tail++) {
                double f = tail / 8.0;
                Vec3 end = new Vec3(x - f * 3.5, y + f * 1.15, z + f * 1.4);
                seg(head, end, rgb, (int) (a * (1 - f) * .75), 1.1f + (float) (1 - f), tail < 4);
            }
            Gizmos.point(head, argb(0xFFFFFF, a), 3.5f);
        }
    }

    // ------------------------------------------------------------ radar sweep
    private static void radar(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        final double R = 13;
        double ang = t * 2.2;
        int trail = 16;
        double y = p.y + .05;
        for (int i = 0; i < trail; i++) {
            double a = ang - i * .07;
            float k = 1f - i / (float) trail;
            int rgb = rainbow ? hsl(Math.toDegrees(a) + t * 20, .9f, .6f) : lerpRgb(colA, colB, 1 - k);
            Vec3 to = new Vec3(p.x + Math.cos(a) * R, y, p.z + Math.sin(a) * R);
            seg(new Vec3(p.x, y, p.z), to, rgb, (int) (235 * k * k * s), i == 0 ? 2.4f : 1.2f, i == 0);
        }
        for (double r : new double[]{4, 8, 12}) circle(p, y, r, colB, (int) (90 * s), 1.2f, 40);
        // tick marks on the outer ring
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            double r0 = i % 6 == 0 ? 11 : 12;
            seg(new Vec3(p.x + Math.cos(a) * r0, y, p.z + Math.sin(a) * r0),
                    new Vec3(p.x + Math.cos(a) * 13, y, p.z + Math.sin(a) * 13), colA, (int) (160 * s), 1.4f, false);
        }
    }

    private static void circle(Vec3 p, double y, double r, int rgb, int a, float w, int n) {
        Vec3 prev = new Vec3(p.x + r, y, p.z);
        for (int i = 1; i <= n; i++) {
            double ang = i * Math.PI * 2 / n;
            Vec3 cur = new Vec3(p.x + Math.cos(ang) * r, y, p.z + Math.sin(ang) * r);
            seg(prev, cur, rgb, a, w, false);
            prev = cur;
        }
    }

    // ------------------------------------------------------------ distant light pillars
    private static void pillars(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        final int CELL = 18, RANGE = 3;
        int cx0 = (int) Math.floor(p.x / CELL), cz0 = (int) Math.floor(p.z / CELL);
        for (int ix = -RANGE; ix <= RANGE; ix++) {
            for (int iz = -RANGE; iz <= RANGE; iz++) {
                int cx = cx0 + ix, cz = cz0 + iz;
                long key = cx * 73856093L ^ cz * 19349663L;
                if (hash(key) < .62) continue;
                double x = (cx + .15 + hash(key + 1) * .7) * CELL;
                double z = (cz + .15 + hash(key + 2) * .7) * CELL;
                double dist = Math.hypot(x - p.x, z - p.z);
                float fade = (float) (Math.min(1, (dist - 8) / 10) * Math.min(1, (CELL * RANGE + 8 - dist) / 14));
                if (fade <= .02f) continue;

                double hgt = 45 + hash(key + 3) * 45, baseY = p.y - 14;
                double ch = hash(key + 4);
                int rgb = rainbow ? hsl(ch * 360 + t * 30, .9f, .6f) : (ch < .5 ? colA : colB);

                for (int j = 0; j < 6; j++) {
                    double y0 = baseY + hgt * j / 6, y1 = baseY + hgt * (j + 1) / 6;
                    int a = (int) (230 * (1 - j / 6f) * fade * s);
                    seg(new Vec3(x, y0, z), new Vec3(x, y1, z), rgb, a, 1.4f, true);
                }
                // expanding ring climbing the pillar
                double ph = (t * .25 + hash(key + 5)) % 1.0;
                circleXZ(x, baseY + ph * hgt, z, 1.3, rgb, (int) (210 * (1 - ph) * fade * s), 14);
                // data packets
                for (int j = 0; j < 2; j++) {
                    double q = (t * .5 + hash(key + 6) + j * .5) % 1.0;
                    Gizmos.point(new Vec3(x, baseY + q * hgt, z),
                            argb(0xFFFFFF, (int) (255 * fade * s * (1 - q))), 5f);
                }
            }
        }
    }

    private static void circleXZ(double x, double y, double z, double r, int rgb, int a, int n) {
        Vec3 prev = new Vec3(x + r, y, z);
        for (int i = 1; i <= n; i++) {
            double ang = i * Math.PI * 2 / n;
            Vec3 cur = new Vec3(x + Math.cos(ang) * r, y, z + Math.sin(ang) * r);
            seg(prev, cur, rgb, a, 1.6f, true);
            prev = cur;
        }
    }

    // ------------------------------------------------------------ spinning wireframe shards
    private static void shards(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        final double R = 22, H = 16;
        for (int i = 0; i < 7; i++) {
            long k = 9000 + i * 7L;
            double dx = wrap(hash(k) * 2 * R - p.x, 2 * R) - R;
            double dz = wrap(hash(k + 1) * 2 * R - p.z, 2 * R) - R;
            double dyw = wrap(hash(k + 2) * H + Math.sin(t * .8 + i) * .8 - p.y, H) - H / 2;
            double d2 = (dx * dx + dz * dz) / (R * R), v = dyw / (H / 2);
            float fade = (float) (Math.max(0, 1 - d2) * Math.max(0, 1 - v * v));
            int a = (int) (240 * fade * s);
            if (a < 6) continue;

            Vec3 c = new Vec3(p.x + dx, p.y + dyw + 3, p.z + dz);
            double yaw = t * (.5 + hash(k + 3)), tilt = t * .37 + i;
            double r = .9 + hash(k + 4) * .6;
            int c1 = rainbow ? hsl(i * 51 + t * 40, .9f, .62f) : colA;
            int c2 = rainbow ? hsl(i * 51 + 150 + t * 40, .9f, .62f) : colB;
            octa(c, r, yaw, tilt, c1, a, 1.8f);
            octa(c, r * .45, -yaw * 1.6, tilt * .6, c2, a, 1.4f);
            Gizmos.point(c, argb(0xFFFFFF, a), 4f);
        }
    }

    private static void octa(Vec3 c, double r, double yaw, double tilt, int rgb, int a, float w) {
        Vec3[] v = new Vec3[6];
        double cy = Math.cos(yaw), sy = Math.sin(yaw), ct = Math.cos(tilt), st = Math.sin(tilt);
        for (int i = 0; i < 6; i++) {
            double x = OCTA[i][0] * r, y = OCTA[i][1] * r, z = OCTA[i][2] * r;
            double y1 = y * ct - z * st, z1 = y * st + z * ct;
            double x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
            v[i] = new Vec3(c.x + x2, c.y + y1, c.z + z2);
        }
        for (int i = 0; i < 6; i++)
            for (int j = i + 1; j < 6; j++)
                if (i / 2 != j / 2) seg(v[i], v[j], rgb, a, w, true);
    }

    // ------------------------------------------------------------ aurora curtains
    private static void aurora(Vec3 p, double t, int colA, int colB, float s, boolean rainbow) {
        final int N = 22;
        for (int c = 0; c < 3; c++) {
            double th = c * 2.094 + .5;
            double ax = Math.cos(th), az = Math.sin(th), px = -az, pz = ax;
            Vec3 prevBottom = null;
            int prevRgb = 0;
            int prevA = 0;
            for (int k = 0; k <= N; k++) {
                double u = k / (double) N;
                double wave = Math.sin(u * 4 + t * .3 + c) * 12 + Math.sin(u * 9 - t * .5) * 3;
                double ox = ax * (u - .5) * 110 + px * (52 + c * 8 + wave);
                double oz = az * (u - .5) * 110 + pz * (52 + c * 8 + wave);
                double top = p.y + 62 + Math.sin(u * 5 + t * .4 + c) * 7;
                double hgt = 24 + 9 * Math.sin(u * 7 + t * .6 + c * 2);
                int rgb = rainbow ? hsl(110 + u * 170 + t * 8 + c * 30, .9f, .6f)
                        : lerpRgb(colA, colB, (float) (.5 + .5 * Math.sin(u * 3 + t * .5 + c)));
                float edge = (float) Math.sin(u * Math.PI);
                float pulse = .65f + .35f * (float) Math.sin(t * 1.7 + u * 8 + c);

                double x = p.x + ox, z = p.z + oz;
                for (int j = 0; j < 4; j++) {
                    double yHi = top - hgt * j / 4, yLo = top - hgt * (j + 1) / 4;
                    int a = (int) (190 * (j + 1) / 4f * edge * pulse * s * .6f);
                    Gizmos.line(new Vec3(x, yHi, z), new Vec3(x, yLo, z), argb(rgb, a), 3f);
                }
                Vec3 bottom = new Vec3(x, top - hgt, z);
                int ba = (int) (200 * edge * pulse * s * .6f);
                if (prevBottom != null) seg(prevBottom, bottom, rgb, Math.min(ba, prevA), 2.5f, true);
                prevBottom = bottom;
                prevRgb = rgb;
                prevA = ba;
            }
        }
    }

    // ------------------------------------------------------------ entity glow boxes
    private static void entities(Minecraft c, Vec3 p, int colA, int colB, float s) {
        float pt = c.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity e : c.level.entitiesForRendering()) {
            if (e == c.player || !(e instanceof LivingEntity || e instanceof ItemEntity)) continue;
            double dist = Math.sqrt(e.distanceToSqr(p));
            if (dist > 48) continue;
            int a = (int) (255 * s * Math.min(1.0, 1.3 - dist / 48));
            if (a < 6) continue;

            AABB b = e.getBoundingBox().move(e.getPosition(pt).subtract(e.position()));
            int rgb = e instanceof Player ? colA : e instanceof Enemy ? 0xFF3B5C
                    : e instanceof ItemEntity ? 0xFFD35A : colB;
            box(b, rgb, a);
            double cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2;
            seg(new Vec3(cx, b.maxY, cz), new Vec3(cx, b.maxY + 3, cz), rgb, a / 2, 1.2f, true);
        }
    }

    private static void box(AABB b, int rgb, int a) {
        Vec3[] cs = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            cs[i] = new Vec3((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
        }
        for (int i = 0; i < 8; i++)
            for (int bit : new int[]{1, 2, 4})
                if ((i & bit) == 0) seg(cs[i], cs[i | bit], rgb, a, 1.8f, true);
    }

    // ------------------------------------------------------------ helpers
    private static void seg(Vec3 a, Vec3 b, int rgb, int alpha, float w, boolean glow) {
        if (alpha < 3) return;
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

    static int hsl(double h, float s, float l) {
        h = ((h % 360) + 360) % 360;
        float c = (1 - Math.abs(2 * l - 1)) * s;
        double hp = h / 60;
        float x = (float) (c * (1 - Math.abs(hp % 2 - 1)));
        float r = 0, g = 0, b = 0;
        switch ((int) hp) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        float m = l - c / 2;
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
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
