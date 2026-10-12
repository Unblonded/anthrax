package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import unblonded.anthrax.modules.visual.CosmicCrosshairModule;

import static unblonded.anthrax.gui.Draw.*;

/**
 * The Cosmic Crosshair. Drawn in real window pixels with anti-aliased masks, so it stays crisp at any
 * GUI scale. It reacts to the game: opens up while you move or jump, shows the attack cooldown as an
 * arc, flashes a hit marker when you swing, and tightens and turns red when you aim at a living target.
 *
 * Ring/arc masks are cached per radius forever, so they are built once at a fixed radius and animated
 * by scaling the pose instead of by changing the radius.
 */
public final class CrosshairRenderer {
    private CrosshairRenderer() {}

    private static final int HOT = 0xFFFF4D6D;
    private static float phase, spread, lock, coolShown, prevStrength = 1;
    private static long hitAt, lastNs = System.nanoTime();
    private static boolean hitTarget;

    public static void render(GuiGraphicsExtractor g, Minecraft mc) {
        CosmicCrosshairModule m = CosmicCrosshairModule.INSTANCE;
        LocalPlayer p = mc.player;
        float gs = (float) mc.getWindow().getGuiScale();
        int pw = Math.round(g.guiWidth() * gs), ph = Math.round(g.guiHeight() * gs);
        long now = System.nanoTime();
        float dt = Math.min(.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        phase += dt * m.speed();   // accumulated, so changing speed never jumps the animation

        // ---- reactive state
        double speedMs = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20;
        float wantSpread = m.spread() ? (float) Math.min(1, speedMs / 5.6) + (p.onGround() ? 0 : .5f) : 0;
        spread += (wantSpread - spread) * Math.min(1, dt * 10);
        boolean onTarget = m.targetLock() && mc.crosshairPickEntity instanceof LivingEntity;
        lock += ((onTarget ? 1 : 0) - lock) * Math.min(1, dt * 14);
        float strength = p.getAttackStrengthScale(0);
        if (prevStrength > .8f && strength < .3f) { hitAt = System.currentTimeMillis(); hitTarget = mc.crosshairPickEntity != null; }
        prevStrength = strength;
        coolShown += ((m.cooldown() && strength < 1 ? 1 : 0) - coolShown) * Math.min(1, dt * 12);

        int savedS = Draw.S;
        Font savedFont = Draw.measureFont;
        Draw.S = Math.max(1, Math.min(4, Math.round(ph / 720f)));
        Draw.measureFont = mc.font;
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            int cx = pw / 2, cy = ph / 2;
            int a = Math.round(255 * m.opacity());
            int[] cols = colors(m.color());
            int c1 = alpha(lerp(cols[0], HOT, lock), a), c2 = alpha(lerp(cols[1], HOT, lock * .7f), a);
            int R = u(m.size()), t = Math.max(1, Math.round(m.thickness() * S * .75f));
            boolean outline = m.outline();
            if (m.glow() && FANCY) glow(g, cx, cy, R + u(4), c1, 3, Math.round(10 * m.opacity() * (1 + lock)));
            switch (m.style()) {
                case "Reticle" -> reticle(g, cx, cy, R, t, c1, c2, outline);
                case "Pulse" -> pulse(g, cx, cy, R, t, c1, c2, outline, a);
                case "Nova" -> nova(g, cx, cy, R, t, c1, c2, outline);
                case "Minimal" -> {}
                default -> orbit(g, cx, cy, R, t, c1, c2, outline);
            }
            dot(g, cx, cy, Math.max(1, Math.round(t * (.8f + .25f * lock))), c1, outline);
            if (coolShown > .02f) {
                int cr = R + u(7), ca = Math.round(a * coolShown);
                if (outline) ringAt(g, cx, cy, cr + 1, Math.max(1, S / 2) + 3, 1, 0, 100, alpha(0x000000, ca / 3));
                ring(g, cx, cy, cr, Math.max(1, S / 2) + 1, strength, alpha(0xFFFFFF, ca / 6), alpha(c2, ca), alpha(c1, ca));
            }
            hitMarker(g, m, cx, cy, a);
        } finally {
            g.pose().popMatrix();
            Draw.S = savedS;
            Draw.measureFont = savedFont;
        }
    }

    // ------------------------------------------------------------------ styles
    /** Three arc segments circling the centre plus two counter-orbiting satellites. */
    private static void orbit(GuiGraphicsExtractor g, int cx, int cy, int R, int t, int c1, int c2, boolean outline) {
        float s = 1 + spread * .45f - lock * .22f;
        for (int i = 0; i < 3; i++) {
            float ang = phase * 1.6f + i * (float) (Math.PI * 2 / 3);
            if (outline) ringAt(g, cx, cy, R + 1, t + 2, s, ang, 18, alpha(0x000000, (c1 >>> 24) / 2));
            ringAt(g, cx, cy, R, t, s, ang, 18, i == 1 ? c2 : c1);
        }
        for (int i = 0; i < 2; i++) {
            double ang = -phase * 2.4 + i * Math.PI;
            float r = R * 1.45f * s;
            dot(g, cx + (int) Math.round(Math.cos(ang) * r), cy + (int) Math.round(Math.sin(ang) * r), Math.max(1, t - 1 + S / 2), c2, outline);
        }
    }

    /** Four gap lines that open with movement and twist into an X on a target, with rotating corner ticks. */
    private static void reticle(GuiGraphicsExtractor g, int cx, int cy, int R, int t, int c1, int c2, boolean outline) {
        float gap = u(3) + spread * u(6) - lock * u(1.5f), len = R * .65f;
        float twist = lock * (float) Math.PI / 4;
        for (int i = 0; i < 4; i++) bar(g, cx, cy, twist + i * (float) Math.PI / 2, gap, gap + len, t, c1, outline);
        float s = 1 + spread * .3f - lock * .15f;
        for (int i = 0; i < 4; i++)
            ringAt(g, cx, cy, Math.round(R * 1.35f), Math.max(1, t - 1), s, phase * .6f + i * (float) Math.PI / 2 - .2f, 7, c2);
    }

    /** A breathing dot with rings rippling outward. */
    private static void pulse(GuiGraphicsExtractor g, int cx, int cy, int R, int t, int c1, int c2, boolean outline, int a) {
        for (int k = 0; k < 3; k++) {
            float f = (phase * .7f + k / 3f) % 1f;
            float s = .35f + f * 1.1f + spread * .3f - lock * .15f;
            int ca = Math.round(((k & 1) == 0 ? (c1 >>> 24) : (c2 >>> 24)) * (1 - f));
            ringAt(g, cx, cy, R, t, s, 0, 100, alpha((k & 1) == 0 ? c1 : c2, ca));
        }
        float breathe = 1 + .35f * (float) Math.sin(phase * 4);
        circle(g, cx, cy, Math.max(1, Math.round(t * 1.4f * breathe)), alpha(c2, (c2 >>> 24) / 3));
    }

    /** Eight rays spinning and breathing around the centre, inside a dashed rotating ring. */
    private static void nova(GuiGraphicsExtractor g, int cx, int cy, int R, int t, int c1, int c2, boolean outline) {
        for (int i = 0; i < 8; i++) {
            float ang = phase * .8f + i * (float) Math.PI / 4;
            float r0 = R * .35f * (1 + spread * .6f) - lock * u(1);
            float r1 = r0 + R * (.3f + .22f * (float) Math.sin(phase * 3 + i));
            bar(g, cx, cy, ang, r0, r1, Math.max(1, t - (i & 1)), (i & 1) == 0 ? c1 : c2, outline);
        }
        float s = 1 + spread * .3f - lock * .2f;
        for (int i = 0; i < 6; i++)
            ringAt(g, cx, cy, Math.round(R * 1.2f), Math.max(1, t - 1), s, -phase * .5f + i * (float) Math.PI / 3, 9, i % 2 == 0 ? c1 : c2);
    }

    private static void hitMarker(GuiGraphicsExtractor g, CosmicCrosshairModule m, int cx, int cy, int a) {
        long age = System.currentTimeMillis() - hitAt;
        if (!m.hitMarker() || age > 320) return;
        float k = 1 - age / 320f;
        int col = alpha(hitTarget ? HOT : 0xFFFFFF, Math.round(a * k));
        float r0 = u(4) + (1 - k) * u(3), r1 = r0 + u(4) * (.6f + .4f * k);
        for (int i = 0; i < 4; i++) bar(g, cx, cy, (float) Math.PI / 4 + i * (float) Math.PI / 2, r0, r1, Math.max(1, S), col, true);
    }

    // ------------------------------------------------------------------ primitives
    /** A ring (percent 100) or arc of a fixed-radius mask, scaled and rotated about the centre. */
    private static void ringAt(GuiGraphicsExtractor g, int cx, int cy, int r, int t, float scale, float angle, int percent, int color) {
        if (r < 2 || (color >>> 24) == 0) return;
        t = Math.max(1, Math.min(t, r));
        int d = r * 2;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().rotate(angle);
        Shapes.blit(g, percent >= 100 ? Shapes.ring(r, t) : Shapes.arc(r, t, percent), -r, -r, d, d, 0, 0, d, d, d, d, color);
        g.pose().popMatrix();
    }

    /** A bar from r0 to r1 along an angle from the centre, centred on its thickness. */
    private static void bar(GuiGraphicsExtractor g, int cx, int cy, float angle, float r0, float r1, int t, int color, boolean outline) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(angle);
        int a0 = Math.round(r0), a1 = Math.round(r1), half = t / 2;
        if (outline) g.fill(a0 - 1, -half - 1, a1 + 1, t - half + 1, alpha(0x000000, (color >>> 24) / 2));
        g.fill(a0, -half, a1, t - half, color);
        g.pose().popMatrix();
    }

    private static void dot(GuiGraphicsExtractor g, int x, int y, int r, int color, boolean outline) {
        if (outline) circle(g, x, y, r + 1, alpha(0x000000, (color >>> 24) / 2));
        circle(g, x, y, r, color);
    }

    private static int[] colors(String mode) {
        float hue = (phase * 60) % 360;
        return switch (mode) {
            case "Rainbow" -> new int[]{hsl(hue, .95f, .62f), hsl(hue + 120, .95f, .66f)};
            case "White" -> new int[]{0xFFFFFFFF, 0xFFD8DCE8};
            case "Cyan" -> new int[]{0xFF4AF5FF, 0xFF9AF9FF};
            case "Pink" -> new int[]{0xFFFF5CC8, 0xFFFFA8E0};
            case "Lime" -> new int[]{0xFF7CFF5C, 0xFFC4FF9A};
            case "Gold" -> new int[]{0xFFFFC94A, 0xFFFFE6A3};
            default -> { Theme.tick(System.currentTimeMillis()); yield new int[]{Theme.accent(), Theme.accent2()}; }
        };
    }
}
