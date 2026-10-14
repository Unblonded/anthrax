package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import unblonded.anthrax.modules.cosmetic.DamageEffectsModule;
import unblonded.anthrax.modules.cosmetic.ExplosionEffectsModule;
import unblonded.anthrax.modules.cosmetic.HitMarkersModule;
import unblonded.anthrax.modules.cosmetic.PickupStreaksModule;
import unblonded.anthrax.modules.cosmetic.VitalsModule;

import static unblonded.anthrax.gui.Draw.*;

/** HUD pieces for the cosmetics: screen flashes, hit markers, vitals arcs and pickup streaks. */
final class CosmeticHud {
    private CosmeticHud() {}

    static void draw(GuiGraphicsExtractor g, Minecraft mc, int pw, int ph) {
        flashes(g, pw, ph);
        vitals(g, mc, pw, ph);
        hitMarker(g, pw, ph);
        streaks(g, pw, ph);
    }

    // ------------------------------------------------------------------ damage pulse and explosion flash
    private static void flashes(GuiGraphicsExtractor g, int pw, int ph) {
        float pulse = DamageEffectsModule.INSTANCE.pulse();
        if (pulse > 0.01f) {
            int rgb = DamageEffectsModule.INSTANCE.pulseRgb();
            int edge = alpha(rgb, Math.round(200 * pulse)), clear = alpha(rgb, 0);
            int bw = Math.round(pw * 0.18f), bh = Math.round(ph * 0.22f);
            vgrad(g, 0, 0, pw, bh, edge, clear);
            vgrad(g, 0, ph - bh, pw, bh, clear, edge);
            hgrad(g, 0, 0, bw, ph, edge, clear);
            hgrad(g, pw - bw, 0, bw, ph, clear, edge);
        }
        float flash = ExplosionEffectsModule.INSTANCE.flash();
        if (flash > 0.01f) fill(g, 0, 0, pw, ph, alpha(0xFFF6E8, Math.round(235 * flash)));
    }

    // ------------------------------------------------------------------ hit marker
    private static void hitMarker(GuiGraphicsExtractor g, int pw, int ph) {
        HitMarkersModule m = HitMarkersModule.INSTANCE;
        float t = m.progress();
        if (t < 0) return;
        int cx = pw / 2, cy = ph / 2;
        float k = m.size() * (m.kill() ? 1.4f : 1f);
        int a = Math.round(255 * (1 - t * t));
        int c = alpha(m.rgb(), a);
        float pop = 1 + 0.35f * (1 - t);   // starts a bit bigger and settles
        int th = Math.max(1, Math.round(u(1.6f) * k));
        switch (m.style()) {
            case "Circle" -> {
                int r = Math.round(u(7 + 6 * t) * k);
                ring(g, cx, cy, r, th, 1f, 0, c, c);
            }
            case "Brackets" -> {
                int s = Math.round(u(9) * k * pop), l = Math.round(u(4) * k);
                brackets(g, cx - s, cy - s, s * 2, s * 2, l, th, c);
            }
            case "Dot burst" -> {
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4;
                    float d = u(5 + 9 * t) * k;
                    int dx = cx + Math.round((float) Math.cos(ang) * d), dy = cy + Math.round((float) Math.sin(ang) * d);
                    circle(g, dx, dy, Math.max(1, th), c);
                }
            }
            default -> {
                float gap = u(4) * k * pop, len = u(5) * k;
                for (int i = 0; i < 4; i++) {
                    double ang = Math.PI / 4 + i * Math.PI / 2;
                    float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                    int x0 = cx + Math.round(cs * gap), y0 = cy + Math.round(sn * gap);
                    int x1 = cx + Math.round(cs * (gap + len)), y1 = cy + Math.round(sn * (gap + len));
                    line(g, x0, y0 - th / 2, x1, y1 - th / 2, len, th, c);
                }
            }
        }
    }

    // ------------------------------------------------------------------ vitals
    private static void vitals(GuiGraphicsExtractor g, Minecraft mc, int pw, int ph) {
        VitalsModule m = VitalsModule.INSTANCE;
        LocalPlayer p = mc.player;
        if (!m.isEnabled() || p == null || p.isSpectator() || p.getAbilities().instabuild) return;
        int cx = pw / 2, cy = ph / 2;
        int r = Math.round(u(m.radius())), t = Math.max(1, Math.round(u(m.thickness())));
        float op = m.opacity();
        // health: left arc, filling upwards; absorption continues in gold
        float hp = p.getHealth() / Math.max(1, p.getMaxHealth()), abs = Math.min(1, p.getAbsorptionAmount() / Math.max(1, p.getMaxHealth()));
        float hpA = m.fadeWhenFull() && hp >= 0.999f && abs <= 0 ? 0.25f : 1f;
        int hpCol = hp > 0.5f ? 0x4DE36B : hp > 0.25f ? 0xFFC23D : 0xFF3B4A;
        arc(g, cx, cy, r, t, 210, 1 / 3f, alpha(0x000000, Math.round(90 * op * hpA)));
        arc(g, cx, cy, r, t, 210, hp / 3f, alpha(hpCol, Math.round(235 * op * hpA)));
        if (abs > 0) arc(g, cx, cy, r + t + u(1), Math.max(1, t / 2 + 1), 210, abs / 3f, alpha(0xFFD54A, Math.round(235 * op)));
        // hunger: right arc, filling upwards (drawn from its bottom end)
        float food = p.getFoodData().getFoodLevel() / 20f;
        float fA = m.fadeWhenFull() && food >= 0.999f ? 0.25f : 1f;
        arc(g, cx, cy, r, t, 30, 1 / 3f, alpha(0x000000, Math.round(90 * op * fA)));
        arc(g, cx, cy, r, t, 150 - 120 * food, food / 3f, alpha(0xE8A04A, Math.round(235 * op * fA)));
        // armour: a short arc under the crosshair
        if (m.armour() && p.getArmorValue() > 0) {
            float ar = p.getArmorValue() / 20f;
            int rr = r + t + u(3);
            arc(g, cx, cy, rr, Math.max(1, t - 1), 160, 40 / 360f, alpha(0x000000, Math.round(70 * op)));
            arc(g, cx, cy, rr, Math.max(1, t - 1), 160, ar * 40 / 360f, alpha(0xA9C4E8, Math.round(220 * op)));
        }
        // air: a short arc above, only while it is running out
        int air = p.getAirSupply(), maxAir = p.getMaxAirSupply();
        if (air < maxAir && maxAir > 0) {
            float a = Math.max(0, air / (float) maxAir);
            int rr = r + t + u(3);
            arc(g, cx, cy, rr, Math.max(1, t - 1), 340, 40 / 360f, alpha(0x000000, Math.round(70 * op)));
            arc(g, cx, cy, rr, Math.max(1, t - 1), 340, a * 40 / 360f, alpha(0x5CC8FF, Math.round(230 * op)));
        }
        if (m.numbers()) {
            textR(g, cx - r - t - u(3), cy + r / 2, String.valueOf(Math.round(p.getHealth() + p.getAbsorptionAmount())), alpha(hpCol, Math.round(230 * op * hpA)), true);
            text(g, cx + r + t + u(3), cy + r / 2, String.valueOf(p.getFoodData().getFoodLevel()), alpha(0xE8A04A, Math.round(230 * op * fA)), true);
        }
    }

    /** An arc starting {@code startDeg} clockwise from 12 o'clock, covering {@code frac} of the full circle. */
    private static void arc(GuiGraphicsExtractor g, int cx, int cy, int r, int t, float startDeg, float frac, int color) {
        int pct = Math.round(Math.max(0, Math.min(1, frac)) * 100);
        if (pct <= 0 || r < 2 || (color >>> 24) == 0) return;
        int d = r * 2;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((float) Math.toRadians(startDeg));
        Shapes.blit(g, Shapes.arc(r, Math.min(t, r), pct), -r, -r, d, d, 0, 0, d, d, d, d, color);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ pickup streaks
    private static void streaks(GuiGraphicsExtractor g, int pw, int ph) {
        PickupStreaksModule m = PickupStreaksModule.INSTANCE;
        var list = m.streaks();
        if (list.isEmpty()) return;
        long now = System.currentTimeMillis();
        int h = u(22), gap = u(4), icon = u(14);
        // a feed just right of the crosshair, newest on top
        int y = ph / 2 + u(16) + h;
        for (int i = list.size() - 1; i >= 0; i--) {
            var s = list.get(i);
            float since = (now - s.last) / (float) m.windowMs();
            float in = Math.min(1, (now - s.born) / 160f);
            float fade = since < 1 ? 1 : Math.max(0, 1 - (now - s.last - (float) m.windowMs()) / 600f);
            float a = in * fade;
            if (a <= 0.02f) continue;
            String label = "+" + s.count + "  " + s.name;
            int w = u(10) + icon + u(6) + textWidth(label, true) + u(10);
            int x = pw / 2 + u(18) + Math.round((1 - in) * u(24));
            // a quick bump when the streak grows
            float bump = Math.max(0, 1 - (now - s.last) / 180f);
            round(g, x, y - h, w, h, h / 2, alpha(0x101014, Math.round(0xC0 * a)));
            outline(g, x, y - h, w, h, h / 2, rimWidth(), alpha(0xFFFFFF, Math.round((0x30 + 0x60 * bump) * a)), alpha(0xFFFFFF, Math.round(0x10 * a)));
            if (a > 0.6f) {
                g.pose().pushMatrix();
                g.pose().translate(x + u(10), y - h + (h - icon) / 2f);
                g.pose().scale(icon / 16f, icon / 16f);
                g.item(s.icon, 0, 0);
                g.pose().popMatrix();
            }
            text(g, x + u(10) + icon + u(6), y - h / 2, label, alpha(0xFFFFFF, Math.round(255 * a)), true);
            y += h + gap;
        }
    }
}
