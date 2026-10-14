package unblonded.anthrax.modules.cosmetic;

/**
 * The knife shapes, built from boxes in knife space: the grip is at the origin, the blade points up (+Y), the
 * cutting edge faces +X and the thickness runs along Z. One unit is one block.
 */
final class KnifeModels {
    private KnifeModels() {}

    /** Colours for one draw: the blade finish along the blade (u 0 base, 1 tip; v across), handle and metal parts. */
    interface Finish { int blade(float u, float v); }

    record Look(Finish finish, int handle, int handleDark, int metal) {}

    // ------------------------------------------------------------------ karambit
    /** Where the finger ring sits relative to the grip; the knife is posed and spun around this point. */
    static final float KARAMBIT_RING_Y = -0.2f;

    // Outlines traced from a reference photo (pixels, y down). The ring centre is the origin.
    private static final float PX = 0.00024f, RING_PX = 255, RING_PY = 1700;
    private static final float[][] BLADE_SPINE = {{700, 450}, {800, 330}, {950, 232}, {1100, 172}, {1250, 152}, {1400, 166}, {1550, 200}, {1700, 238}, {1880, 262}};
    private static final float[][] BLADE_EDGE = {{820, 705}, {900, 565}, {1000, 452}, {1150, 362}, {1300, 312}, {1450, 287}, {1600, 272}, {1750, 264}, {1880, 262}};
    private static final float[][] HANDLE_BACK = {{500, 465}, {470, 560}, {440, 650}, {400, 760}, {362, 880}, {322, 1000}, {283, 1120}, {243, 1240}, {192, 1370}, {135, 1500}, {95, 1630}};
    private static final float[][] HANDLE_FRONT = {{812, 642}, {640, 752}, {552, 826}, {532, 945}, {478, 1012}, {470, 1130}, {428, 1205}, {418, 1330}, {380, 1425}, {332, 1560}, {300, 1640}};

    private static float kx(float px) { return (px - RING_PX) * PX; }
    private static float ky(float py) { return (RING_PY - py) * PX; }

    /**
     * Karambit: claw blade with its edge on the inside of the curve, a thick curved handle with finger grooves,
     * a guard hump, two screws and a finger ring in the blade's finish. The origin is the centre of the ring,
     * and the ring's hole faces along Z, so a turn around Z spins the knife around the finger.
     */
    static void karambit(KnifeMesh m, Look look) {
        // blade: thick along the spine, sharpened to a thin edge, finish shaded along and across
        float[][] bs = BLADE_SPINE, be = BLADE_EDGE;
        int n = bs.length;
        float[] ax = new float[n], ay = new float[n], bx = new float[n], by = new float[n];
        for (int i = 0; i < n; i++) { ax[i] = kx(bs[i][0]); ay[i] = ky(bs[i][1]); bx[i] = kx(be[i][0]); by[i] = ky(be[i][1]); }
        m.strip(ax, ay, 0.0065f, bx, by, 0.0009f, false, (u, v) -> {
            int c = look.finish.blade(u, v * 2 - 1);
            return v > 0.82f ? brighten(c, 0.45f) : c;   // polished cutting edge
        });
        // handle
        int h = HANDLE_BACK.length;
        float[] hx = new float[h], hy = new float[h], fx = new float[h], fy = new float[h];
        for (int i = 0; i < h; i++) { hx[i] = kx(HANDLE_BACK[i][0]); hy[i] = ky(HANDLE_BACK[i][1]); fx[i] = kx(HANDLE_FRONT[i][0]); fy[i] = ky(HANDLE_FRONT[i][1]); }
        m.strip(hx, hy, 0.0135f, fx, fy, 0.0125f, false, (u, v) -> u < 0.2f ? look.handleDark : (v > 0.5f ? look.handle : look.handleDark));
        // guard hump on the spine side, with the red pin
        float gx = kx(560), gy = ky(470);
        m.push().translate(gx, gy, 0).rotZ(-28);
        m.box(-0.012f, -0.006f, -0.0145f, 0.024f, 0.014f, 0.0145f, look.handleDark);
        m.pop();
        m.box(kx(655) - 0.002f, ky(520) - 0.002f, -0.0152f, kx(655) + 0.002f, ky(520) + 0.002f, 0.0152f, 0xD02020);
        // screws on the grip plate, both faces
        for (float[] sc : new float[][]{{350, 880}, {236, 1195}}) {
            float sx = kx(sc[0]), sy = ky(sc[1]);
            m.push().translate(sx, sy, 0).rotZ(-20);
            m.box(-0.0035f, -0.0035f, -0.0148f, 0.0035f, 0.0035f, 0.0148f, look.metal);
            m.pop();
        }
        // grip plate between the screws
        m.push().translate(kx(293), ky(1037), 0).rotZ(-20);
        m.box(-0.0045f, -0.042f, -0.0142f, 0.0045f, 0.042f, 0.0142f, KnifeModels.darken(look.handleDark, 0.3f));
        m.pop();
        // ridges on the back of the handle near the ring
        for (int i = 0; i < 5; i++) {
            float ry = 1290 + i * 42;
            float rxp = 175 - i * 14;
            m.push().translate(kx(rxp), ky(ry), 0).rotZ(-22);
            m.box(-0.012f, -0.0028f, -0.012f, 0.002f, 0.0028f, 0.012f, look.handleDark);
            m.pop();
        }
        // finger ring, in the blade's finish like the reference
        int seg = 28;
        float ro = 108 * PX, ri = 64 * PX;
        float[] ox = new float[seg], oy = new float[seg], ix = new float[seg], iy = new float[seg];
        for (int i = 0; i < seg; i++) {
            double a = Math.PI * 2 * i / seg;
            ox[i] = (float) Math.cos(a) * ro; oy[i] = (float) Math.sin(a) * ro;
            ix[i] = (float) Math.cos(a) * ri; iy[i] = (float) Math.sin(a) * ri;
        }
        m.strip(ox, oy, 0.0115f, ix, iy, 0.0105f, true, (u, v) -> darken(look.finish.blade(0.35f + 0.3f * (float) Math.sin(u * Math.PI * 2), v), 0.08f));
    }

    // ------------------------------------------------------------------ butterfly
    /**
     * Balisong. {@code bladeAngle} 0 points the blade up (open), 180 tucks it down between the handles;
     * {@code swingAngle} turns the free handle around the pivot (0 and 360 rest beside the fixed handle).
     */
    static void butterfly(KnifeMesh m, Look look, float bladeAngle, float swingAngle) {
        float pivotY = 0.02f;
        // fixed (held) handle
        m.push().translate(0, pivotY, 0.012f);
        balisongHandle(m, look);
        m.pop();
        // free handle, swinging around the pivot
        m.push().translate(0, pivotY, -0.012f).rotZ(swingAngle);
        balisongHandle(m, look);
        m.pop();
        // blade around the pivot
        m.push().translate(0, pivotY, 0).rotZ(bladeAngle);
        m.box(-0.012f, -0.012f, -0.005f, 0.012f, 0.012f, 0.005f, look.metal);   // tang
        int n = 12;
        float length = 0.2f, seg = length / n;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n, t1 = (i + 1) / (float) n;
            float w = 0.036f, wTop = 0.036f;
            float x0 = -w / 2, x1 = w / 2, tx0 = -wTop / 2, tx1 = wTop / 2;
            if (t1 > 0.75f) {   // spine sweeps down to the point
                float k0 = (t - 0.75f) / 0.25f, k1 = (t1 - 0.75f) / 0.25f;
                x0 = -w / 2 + w * 0.9f * Math.max(0, k0);
                tx0 = -w / 2 + w * 0.9f * k1;
                tx1 = w / 2 - w * 0.25f * k1 * k1;
                x1 = w / 2 - w * 0.25f * Math.max(0, k0) * Math.max(0, k0);
            }
            final float u0 = t, u1 = t1;
            m.push().translate(0, 0.012f + i * seg, 0);
            m.taper(x0, 0, -0.004f, x1, seg, 0.004f, tx0, tx1, (x, y, z) -> look.finish.blade(y > seg / 2 ? u1 : u0, x / 0.018f));
            m.pop();
        }
        m.pop();
        // pivot pins
        m.box(-0.008f, pivotY - 0.008f, -0.022f, 0.008f, pivotY + 0.008f, 0.022f, look.metal);
    }

    private static void balisongHandle(KnifeMesh m, Look look) {
        // a channel handle: two rails with cut-outs, joined at the ends
        m.box(-0.021f, -0.205f, -0.006f, -0.011f, 0.0f, 0.006f, look.handle);
        m.box(0.011f, -0.205f, -0.006f, 0.021f, 0.0f, 0.006f, look.handle);
        for (int i = 0; i < 4; i++) {
            float y = -0.18f + i * 0.045f;
            m.box(-0.011f, y, -0.005f, 0.011f, y + 0.016f, 0.005f, look.handleDark);
        }
        m.box(-0.021f, -0.215f, -0.007f, 0.021f, -0.2f, 0.007f, look.metal);   // end cap
        m.box(-0.021f, -0.01f, -0.007f, 0.021f, 0.006f, 0.007f, look.metal);    // pivot block
    }

    // ------------------------------------------------------------------ bayonet
    /** Straight military blade with a saw-toothed spine, cross guard with a muzzle ring and a ribbed handle. */
    static void bayonet(KnifeMesh m, Look look) {
        straightHandle(m, look, 0.17f, 0.046f, 0.034f, 5);
        // pommel
        m.box(-0.026f, -0.192f, -0.02f, 0.026f, -0.17f, 0.02f, look.metal);
        // guard with the muzzle ring on the spine side
        m.box(-0.05f, -0.006f, -0.018f, 0.038f, 0.014f, 0.018f, look.metal);
        for (int i = 0; i < 10; i++) {
            m.push().translate(-0.068f, 0.004f, 0).rotZ(i * 36f).translate(0, 0.016f, 0);
            m.box(-0.005f, -0.004f, -0.008f, 0.005f, 0.004f, 0.008f, look.metal);
            m.pop();
        }
        blade(m, look, 0.014f, 0.29f, 0.05f, 0.012f, 0.7f, true, 0.12f, 0.5f);
    }

    // ------------------------------------------------------------------ flip
    /** Folding knife; {@code fold} 0 is open, 180 folds the blade down into the handle. */
    static void flip(KnifeMesh m, Look look, float fold) {
        straightHandle(m, look, 0.165f, 0.044f, 0.028f, 0);
        m.box(-0.024f, -0.18f, -0.016f, 0.024f, -0.165f, 0.016f, look.metal);
        m.box(-0.025f, -0.012f, -0.016f, 0.025f, 0.008f, 0.016f, look.metal);   // pivot bolster
        m.push().translate(0, 0.0f, 0).rotZ(fold);
        blade(m, look, 0.008f, 0.2f, 0.046f, 0.009f, 0.62f, false, 0, 0);
        // thumb stud
        m.box(-0.03f, 0.03f, -0.012f, -0.018f, 0.042f, 0.012f, look.metal);
        m.pop();
    }

    // ------------------------------------------------------------------ huntsman
    /** Big clip-point hunting blade with spine teeth, a guard and a grooved grip. */
    static void huntsman(KnifeMesh m, Look look) {
        straightHandle(m, look, 0.18f, 0.048f, 0.036f, 0);
        for (int i = 0; i < 3; i++) {   // finger grooves on the edge side
            float y = -0.15f + i * 0.045f;
            m.box(0.022f, y, -0.017f, 0.03f, y + 0.026f, 0.017f, look.handle);
        }
        m.box(-0.03f, -0.2f, -0.02f, 0.03f, -0.18f, 0.02f, look.metal);
        m.box(-0.045f, -0.006f, -0.02f, 0.055f, 0.016f, 0.02f, look.metal);
        blade(m, look, 0.016f, 0.31f, 0.058f, 0.013f, 0.62f, true, 0.06f, 0.3f);
    }

    // ------------------------------------------------------------------ gut
    /** Short blade with a hooked spine near the tip and a finger choil. */
    static void gut(KnifeMesh m, Look look) {
        straightHandle(m, look, 0.16f, 0.044f, 0.032f, 3);
        m.box(-0.025f, -0.175f, -0.019f, 0.025f, -0.16f, 0.019f, look.metal);
        m.box(-0.032f, -0.006f, -0.018f, 0.034f, 0.012f, 0.018f, look.metal);
        blade(m, look, 0.012f, 0.21f, 0.052f, 0.011f, 0.7f, false, 0, 0);
        // the gut hook: a curl cut into the spine near the tip
        for (int i = 0; i < 7; i++) {
            float a = 200 + i * 25;
            m.push().translate(-0.026f, 0.17f, 0).rotZ(a).translate(0, 0.016f, 0);
            m.box(-0.004f, -0.004f, -0.0055f, 0.004f, 0.004f, 0.0055f, (x, y, z) -> brighten(look.finish.blade(0.8f, -1), 0.25f));
            m.pop();
        }
    }

    // ------------------------------------------------------------------ shared parts
    private static void straightHandle(KnifeMesh m, Look look, float len, float w, float t, int ribs) {
        m.box(-w / 2, -len, -t / 2, w / 2, 0, t / 2, (x, y, z) -> (int) (y * 80) % 2 == 0 ? look.handle : look.handleDark);
        for (int i = 0; i < ribs; i++) {
            float y = -len + (i + 0.6f) * len / (ribs + 0.4f);
            m.box(-w / 2 - 0.002f, y, -t / 2 - 0.002f, w / 2 + 0.002f, y + 0.007f, t / 2 + 0.002f, look.handleDark);
        }
    }

    /**
     * A straight blade from y0: {@code clip} is where the spine starts sweeping down to the point; optional saw
     * teeth on the spine between {@code teethFrom} and {@code teethTo} (fractions of the length).
     */
    private static void blade(KnifeMesh m, Look look, float y0, float len, float w, float th, float clip, boolean teeth, float teethFrom, float teethTo) {
        int n = 16;
        float seg = len / n;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n, t1 = (i + 1) / (float) n;
            float sx0 = -w / 2, sx1 = -w / 2;
            if (t > clip) sx0 = -w / 2 + w * 0.95f * (float) Math.pow((t - clip) / (1 - clip), 1.2);
            if (t1 > clip) sx1 = -w / 2 + w * 0.95f * (float) Math.pow((t1 - clip) / (1 - clip), 1.2);
            // the edge side curves up into the point over the last fifth
            float ex0 = w / 2 - (t > 0.8f ? w * 0.05f * (t - 0.8f) / 0.2f : 0);
            float ex1 = w / 2 - (t1 > 0.8f ? w * 0.05f * (t1 - 0.8f) / 0.2f : 0);
            if (i == n - 1) { sx1 = ex1 - 0.001f; }
            final float u0 = t, u1 = t1, half = w / 2;
            float tt = th * (1 - 0.4f * t);
            m.push().translate(0, y0 + i * seg, 0);
            m.taper(sx0, 0, -tt / 2, ex0, seg, tt / 2, sx1, ex1, (x, y, z) -> look.finish.blade(y > seg / 2 ? u1 : u0, x / half));
            // bright cutting-edge bevel
            m.taper(ex0 - 0.006f, 0, -tt / 4, ex0 + 0.001f, seg, tt / 4, ex1 - 0.006f, ex1 + 0.001f, (x, y, z) -> brighten(look.finish.blade(u0, 1), 0.4f));
            // fuller: a dark groove along the middle of the lower blade
            if (t < 0.55f) m.box(-w * 0.18f, 0, -tt / 2 - 0.0008f, -w * 0.02f, seg, tt / 2 + 0.0008f, (x, y, z) -> darken(look.finish.blade(u0, 0), 0.3f));
            m.pop();
            if (teeth && t >= teethFrom && t < teethTo && i % 1 == 0) {
                m.push().translate(-w / 2, y0 + i * seg + seg / 2, 0).rotZ(45);
                m.box(-0.006f, -0.006f, -tt / 2.5f, 0.006f, 0.006f, tt / 2.5f, (x, y, z) -> darken(look.finish.blade(u0, -1), 0.1f));
                m.pop();
            }
        }
    }

    static int brighten(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * k) << 16) | ((int) (g + (255 - g) * k) << 8) | (int) (b + (255 - b) * k);
    }

    static int darken(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r * (1 - k)) << 16) | ((int) (g * (1 - k)) << 8) | (int) (b * (1 - k));
    }
}
