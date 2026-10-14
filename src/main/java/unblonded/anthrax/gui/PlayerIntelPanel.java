package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.item.ItemStack;
import unblonded.anthrax.modules.client.FriendsModule;
import unblonded.anthrax.modules.visual.PlayerListModule;
import unblonded.anthrax.modules.visual.PlayerListModule.Entry;
import unblonded.anthrax.modules.visual.PlayerListModule.Gear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Player List HUD panel: a closest-first scan list where each row unfolds into an intel card
 * (vitals, telemetry, status flags, loadout with durability and enchants, effects, threat).
 * With chat open the rows are clickable; {@link #click} is fed from the mouse mixin.
 */
public final class PlayerIntelPanel {
    private PlayerIntelPanel() {}

    private static final int GOLD = 0xFFFFC94A, CYAN = 0xFF4AF5FF;
    private record Hit(int x, int y, int w, int h, Entry entry, int rank) {}
    private static final List<Hit> HITS = new ArrayList<>();
    /** Unfold animation per player, 0 closed .. 1 open. */
    private static final Map<UUID, Float> OPEN = new HashMap<>();
    private static long lastNs = System.nanoTime();

    /** Click in HUD pixels while chat is open; true when it hit a row (the click is then consumed). */
    public static boolean click(double px, double py) {
        for (Hit h : HITS) {
            if (px >= h.x && px < h.x + h.w && py >= h.y && py < h.y + h.h) {
                PlayerListModule.INSTANCE.toggle(h.entry, h.rank);
                return true;
            }
        }
        return false;
    }

    static int width() { return u(248); }

    static int render(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int accent) {
        PlayerListModule module = PlayerListModule.INSTANCE;
        List<Entry> entries = module.entries();
        HITS.clear();
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        boolean chat = mc.gui.screen() instanceof ChatScreen;
        float gs = (float) mc.getWindow().getGuiScale();
        int mx = chat ? (int) Math.round(mc.mouseHandler.getScaledXPos(mc.getWindow()) * gs) : -1;
        int my = chat ? (int) Math.round(mc.mouseHandler.getScaledYPos(mc.getWindow()) * gs) : -1;

        int w = width(), headH = u(26), rowH = u(28), pad = u(8);
        // measure: header + rows + animated cards
        float[] anim = new float[entries.size()];
        int[] cardH = new int[entries.size()];
        int h = headH + u(4);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float target = module.expanded(e, i) ? 1f : 0f;
            float a = OPEN.getOrDefault(e.id(), target);
            a += (target - a) * Math.min(1f, dt * 14f);
            if (Math.abs(target - a) < 0.004f) a = target;
            OPEN.put(e.id(), a);
            anim[i] = a;
            cardH[i] = cardHeight(e, module, w - pad * 2);
            h += rowH + Math.round((cardH[i] + u(6)) * ease(a));
        }
        if (entries.isEmpty()) h += u(22);
        if (chat) h += u(12);
        OPEN.keySet().retainAll(entries.stream().map(Entry::id).toList());

        panel(g, x, y, w, h, accent);
        header(g, x, y, w, headH, entries.size(), module.range(), accent);
        int ry = y + headH + u(2);
        if (entries.isEmpty()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
            circle(g, x + pad + u(6), ry + u(11), u(3), alpha(accent, 80 + (int) (120 * pulse)));
            text(g, x + pad + u(16), ry + u(11), "NO SIGNALS IN RANGE", Theme.MUTED, true);
            ry += u(22);
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            boolean hover = inside(mx, my, x + u(4), ry, w - u(8), rowH);
            row(g, mc, x, ry, w, rowH, e, i, anim[i], hover, accent);
            HITS.add(new Hit(x + u(4), ry, w - u(8), rowH, e, i));
            ry += rowH;
            float a = ease(anim[i]);
            if (a > 0.001f) {
                int shown = Math.round((cardH[i] + u(6)) * a);
                g.enableScissor(x, ry, x + w, ry + shown);
                card(g, x + pad, ry + u(2), w - pad * 2, cardH[i], e, module, accent, anim[i]);
                g.disableScissor();
                ry += shown;
            }
        }
        if (chat) textC(g, x + w / 2, ry + u(5), "CLICK A ROW TO EXPAND / COLLAPSE", Theme.DIM, false);
        return h;
    }

    // ------------------------------------------------------------------ pieces

    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        shadow(g, x, y, w, h, u(10), u(6), 0x40);
        glass(g, x, y, w, h, u(10), alpha(0x0E1119, 0xD0), 0.8f);
        round(g, x + u(3), y + u(8), Math.max(2, S), h - u(16), S, accent);
        brackets(g, x + u(1), y + u(1), w - u(2), h - u(2), u(7), Math.max(1, S / 2), alpha(accent, 0x70));
    }

    private static void header(GuiGraphicsExtractor g, int x, int y, int w, int headH, int count, int range, int accent) {
        int cy = y + headH / 2 + S;
        icon(g, x + u(16), cy, Icons.SATELLITE_DISH, accent);
        text(g, x + u(28), cy, "PROXIMITY SCAN", Theme.SUBTEXT, true);
        long t = System.currentTimeMillis();
        boolean blink = (t / 500) % 2 == 0;
        circle(g, x + u(28) + textWidth("PROXIMITY SCAN", true) + u(7), cy, u(2), blink ? Theme.OK : alpha(Theme.OK, 0x40));
        String right = String.format("%02d TGT  //  R%d", count, range);
        textR(g, x + w - u(10), cy, right, count > 0 ? accent : Theme.MUTED, false);
        // sweep line under the header
        int ly = y + headH - S;
        fill(g, x + u(8), ly, w - u(16), Math.max(1, S / 2), Theme.LINE);
        int sweep = x + u(8) + (int) ((t % 2200) / 2200f * (w - u(16)));
        shineC(g, x + u(8), w - u(16), ly - S / 2, Math.max(1, S), sweep, u(40), 0xD0, accent);
    }

    private static void row(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h, Entry e, int rank,
                            float open, boolean hover, int accent) {
        int threatCol = threatColor(e.threat());
        if (hover || open > 0.01f)
            round(g, x + u(5), y + u(1), w - u(10), h - u(2), u(6), alpha(0xFFFFFF, hover ? 0x14 : Math.round(0x0A * open)));

        // rank chip
        int cy = y + h / 2, rx = x + u(10);
        roundBorder(g, rx, cy - u(8), u(18), u(16), u(4), Math.max(1, S / 2), alpha(threatCol, 0x90), alpha(threatCol, 0x22));
        textC(g, rx + u(9), cy, String.format("%02d", rank + 1), threatCol, true);

        // face
        int face = u(18), fx = rx + u(24);
        PlayerFaceExtractor.extractRenderState(g, e.player().getSkin(), fx, cy - face / 2, face, 0xFFFFFFFF);
        if (e.friend()) outline(g, fx - S, cy - face / 2 - S, face + 2 * S, face + 2 * S, u(2), Math.max(1, S / 2), FriendsModule.COLOR, FriendsModule.COLOR);

        // name + vitals line
        int nx = fx + face + u(7);
        int rightEdge = x + w - u(12);
        int nameMax = rightEdge - nx - u(84);
        int nameCol = e.friend() ? FriendsModule.COLOR : e.player().isCrouching() ? Theme.MUTED : Theme.TEXT;
        text(g, nx, y + u(9), Hud.fit(e.name(), nameMax, true), nameCol, true);
        int barW = u(64), barY = y + u(18);
        segmentBar(g, nx, barY, barW, u(4), 10, e.healthFrac(), hpColor(e.healthFrac()), e.absorption() / Math.max(1, e.maxHealth()));
        String hp = String.format("%.1f", (e.health() + e.absorption()) / 2f);
        text(g, nx + barW + u(5), barY + u(2), hp, hpColor(e.healthFrac()), false);
        int ax = nx + barW + u(7) + textWidth(hp, false);
        icon(g, ax + u(4), barY + u(2), Icons.SHIELD_HALVED, Theme.MUTED);
        text(g, ax + u(10), barY + u(2), Integer.toString(e.armor()), Theme.SUBTEXT, false);
        if (e.pops() > 0) {
            int px = ax + u(14) + textWidth(Integer.toString(e.armor()), false);
            text(g, px, barY + u(2), e.pops() + "P", GOLD, true);
        }

        // held item, distance, bearing, caret
        int caretX = rightEdge - u(4);
        icon(g, caretX, cy, open > 0.5f ? Icons.CARET_DOWN : Icons.CARET_RIGHT, hover ? accent : Theme.DIM);
        int arrowX = caretX - u(14);
        g.pose().pushMatrix();
        g.pose().translate(arrowX, cy);
        g.pose().rotate((float) Math.toRadians(e.bearing() - 45f));
        icon(g, 0, 0, Icons.LOCATION_ARROW, e.lookingAtMe() ? Theme.DANGER : accent);
        g.pose().popMatrix();
        String dist = e.distance() < 10 ? String.format("%.1fm", e.distance()) : Math.round(e.distance()) + "m";
        int distR = arrowX - u(10);
        textR(g, distR, y + u(9), dist, distanceColor(e.distance()), true);
        String dy = Math.abs(e.dy()) < 0.5 ? "LVL" : (e.dy() > 0 ? "+" : "") + Math.round(e.dy()) + "Y";
        textR(g, distR, y + u(19), dy, Theme.DIM, false);
        if (!e.mainHand().isEmpty()) item(g, e.mainHand(), distR - u(40), cy - u(7), u(14));
    }

    // ------------------------------------------------------------------ card

    private static final int SECTION = 13, CELL = 20, GEAR = 24, LINE = 10;

    private static int cardHeight(Entry e, PlayerListModule m, int w) {
        int h = u(6);
        h += u(SECTION) + u(CELL) * 2 + u(10);                            // vitals: 2 rows of cells + bar
        if (m.showTelemetry()) h += u(SECTION) + u(CELL) * 2;           // telemetry
        if (!e.flags().isEmpty()) h += u(SECTION) + chipRows(e.flags(), w) * u(14);
        if (m.showLoadout()) h += u(SECTION) + Math.max(1, e.gear().size()) * u(GEAR);
        if (!e.effects().isEmpty()) h += u(SECTION) + e.effects().size() * u(LINE);
        h += u(SECTION) + u(12);                                         // threat
        return h + u(4);
    }

    private static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, Entry e, PlayerListModule m, int accent, float open) {
        int threatCol = threatColor(e.threat());
        int a = Math.round(255 * Math.min(1f, open * 1.4f));
        round(g, x, y, w, h, u(6), alpha(0x05070C, Math.round(0xB0 * open)));
        outline(g, x, y, w, h, u(6), Math.max(1, S / 2), alpha(accent, Math.round(0x50 * open)), alpha(accent, Math.round(0x18 * open)));
        fill(g, x, y + u(6), Math.max(1, S), h - u(12), alpha(threatCol, a));
        // faint scanlines
        if (FANCY) for (int sy = y + u(3); sy < y + h - u(3); sy += u(4)) fill(g, x + u(2), sy, w - u(4), 1, 0x08FFFFFF);

        int ix = x + u(8), iw = w - u(16), cy = y + u(6);

        // VITALS
        cy = section(g, ix, cy, iw, "VITALS", Icons.HEART, accent);
        int col = iw / 3;
        String hpVal = String.format("%.1f/%.0f", e.health(), e.maxHealth()) + (e.absorption() > 0 ? String.format(" +%.0f", e.absorption()) : "");
        cell(g, ix, cy, col, "HEALTH", hpVal, hpColor(e.healthFrac()));
        cell(g, ix + col, cy, col, "ARMOR", e.armor() + " PTS", Theme.TEXT);
        cell(g, ix + col * 2, cy, col, "TOUGHNESS", String.format("%.1f", e.toughness()), Theme.TEXT);
        cy += u(CELL);
        cell(g, ix, cy, col, "TOTEM POPS", Integer.toString(e.pops()), e.pops() > 0 ? GOLD : Theme.TEXT);
        cell(g, ix + col, cy, col, "PING", e.ping() < 0 ? "N/A" : e.ping() + " MS", pingColor(e.ping()));
        cell(g, ix + col * 2, cy, col, "MODE", e.gameMode(), Theme.TEXT);
        cy += u(CELL);
        float max = Math.max(1, e.maxHealth());
        round(g, ix, cy + u(2), iw, u(5), u(2), 0x22FFFFFF);
        round(g, ix, cy + u(2), Math.max(u(4), Math.round(iw * e.healthFrac())), u(5), u(2), hpColor(e.healthFrac()));
        if (e.absorption() > 0)
            round(g, ix, cy + u(2), Math.max(u(4), Math.round(iw * Math.min(1, e.absorption() / max))), u(2), u(1), GOLD);
        for (int t = 1; t < 10; t++) fill(g, ix + iw * t / 10, cy + u(2), Math.max(1, S / 2), u(5), 0x60000000);
        cy += u(10);

        // TELEMETRY
        if (m.showTelemetry()) {
            cy = section(g, ix, cy, iw, "TELEMETRY", Icons.CROSSHAIRS, accent);
            cell(g, ix, cy, col * 2, "POSITION", e.x() + "  " + e.y() + "  " + e.z(), CYAN);
            cell(g, ix + col * 2, cy, col, "DELTA Y", (e.dy() >= 0 ? "+" : "") + String.format("%.1f", e.dy()), Theme.TEXT);
            cy += u(CELL);
            cell(g, ix, cy, col, "SPEED", String.format("%.1f M/S", e.speed()), e.speed() > 7 ? GOLD : Theme.TEXT);
            cell(g, ix + col, cy, col, "BEARING", String.format("%+.0f°", e.bearing()), Theme.TEXT);
            cell(g, ix + col * 2, cy, col, "LINE OF SIGHT", e.visible() ? "CLEAR" : "BLOCKED", e.visible() ? Theme.OK : Theme.MUTED);
            cy += u(CELL);
        }

        // STATUS chips
        if (!e.flags().isEmpty()) {
            cy = section(g, ix, cy, iw, "STATUS", Icons.BOLT, accent);
            int cx = ix, chipH = u(11);
            for (String f : e.flags()) {
                int cw = textWidth(f, true) + u(10);
                if (cx + cw > ix + iw) { cx = ix; cy += u(14); }
                int c = flagColor(f, accent);
                roundBorder(g, cx, cy + u(1), cw, chipH, chipH / 2, Math.max(1, S / 2), alpha(c, 0xA0), alpha(c, 0x24));
                textC(g, cx + cw / 2, cy + u(1) + chipH / 2, f, c, true);
                cx += cw + u(4);
            }
            cy += u(14);
        }

        // LOADOUT
        if (m.showLoadout()) {
            cy = section(g, ix, cy, iw, "LOADOUT", Icons.SHIELD_HALVED, accent);
            if (e.gear().isEmpty()) {
                text(g, ix, cy + u(GEAR) / 2, "NOTHING EQUIPPED", Theme.DIM, false);
                cy += u(GEAR);
            }
            for (Gear gear : e.gear()) {
                gearRow(g, ix, cy, iw, gear, accent);
                cy += u(GEAR);
            }
        }

        // EFFECTS
        if (!e.effects().isEmpty()) {
            cy = section(g, ix, cy, iw, "EFFECTS", Icons.FLASK, accent);
            for (String fx : e.effects()) {
                circle(g, ix + u(3), cy + u(LINE) / 2, u(2), accent);
                text(g, ix + u(10), cy + u(LINE) / 2, Hud.fit(fx, iw - u(10), false), Theme.SUBTEXT, false);
                cy += u(LINE);
            }
        }

        // THREAT
        cy = section(g, ix, cy, iw, "THREAT ASSESSMENT", Icons.SKULL, accent);
        String label = e.threat() + "  " + e.threatLabel();
        int lw = textWidth(label, true) + u(8);
        int segs = 20, bw = iw - lw, sw = bw / segs;
        for (int s = 0; s < segs; s++) {
            boolean lit = s < Math.round(e.threat() / 100f * segs);
            int c = lerp(Theme.OK, Theme.DANGER, s / (float) (segs - 1));
            fill(g, ix + s * sw, cy + u(3), sw - Math.max(1, S / 2), u(6), lit ? c : 0x1EFFFFFF);
        }
        textR(g, ix + iw, cy + u(6), label, threatCol, true);
    }

    private static void gearRow(GuiGraphicsExtractor g, int x, int y, int w, Gear gear, int accent) {
        item(g, gear.stack(), x, y + u(3), u(16));
        int tx = x + u(21);
        String slot = switch (gear.slot()) {
            case HEAD -> "HD"; case CHEST -> "CH"; case LEGS -> "LG"; case FEET -> "FT"; case MAINHAND -> "MH"; case OFFHAND -> "OH";
            default -> "--";
        };
        text(g, tx, y + u(7), slot, Theme.DIM, true);
        int nameX = tx + u(16);
        float dur = gear.durability();
        String durText = dur < 0 ? (gear.stack().getCount() > 1 ? "x" + gear.stack().getCount() : "") : Math.round(dur * 100) + "%";
        int durW = textWidth(durText, true);
        text(g, nameX, y + u(7), Hud.fit(gear.name(), w - (nameX - x) - durW - u(40), false), gear.enchants().isEmpty() ? Theme.TEXT : 0xFFD9B8FF, false);
        if (dur >= 0) {
            int c = lerp(Theme.DANGER, Theme.OK, dur);
            int bw = u(28);
            round(g, x + w - bw, y + u(6), bw, u(3), u(1), 0x22FFFFFF);
            round(g, x + w - bw, y + u(6), Math.max(u(2), Math.round(bw * dur)), u(3), u(1), c);
            textR(g, x + w - bw - u(4), y + u(7), durText, c, true);
        } else if (!durText.isEmpty()) textR(g, x + w, y + u(7), durText, Theme.SUBTEXT, true);
        String ench = gear.enchants().isEmpty() ? "NO ENCHANTS" : String.join(" · ", gear.enchants());
        text(g, nameX, y + u(17), Hud.fit(ench, w - (nameX - x), false), gear.enchants().isEmpty() ? Theme.DIM : 0xFFB98CFF, false);
    }

    private static int section(GuiGraphicsExtractor g, int x, int y, int w, String title, String glyph, int accent) {
        int cy = y + u(SECTION) / 2;
        icon(g, x + u(4), cy, glyph, accent);
        text(g, x + u(12), cy, title, alpha(accent, 0xE0), true);
        int lx = x + u(16) + textWidth(title, true);
        fill(g, lx, cy, x + w - lx, Math.max(1, S / 2), alpha(accent, 0x30));
        return y + u(SECTION);
    }

    private static void cell(GuiGraphicsExtractor g, int x, int y, int w, String key, String value, int color) {
        text(g, x, y + u(5), key, Theme.DIM, false);
        text(g, x, y + u(14), Hud.fit(value, w - u(4), true), color, true);
    }

    private static int chipRows(List<String> flags, int w) {
        int iw = w - u(16), cx = 0, rows = 1;
        for (String f : flags) {
            int cw = textWidth(f, true) + u(10);
            if (cx + cw > iw) { cx = 0; rows++; }
            cx += cw + u(4);
        }
        return rows;
    }

    private static void segmentBar(GuiGraphicsExtractor g, int x, int y, int w, int h, int segs, float frac, int color, float gold) {
        int sw = w / segs, gap = Math.max(1, S / 2);
        float filled = frac * segs;
        for (int s = 0; s < segs; s++) {
            fill(g, x + s * sw, y, sw - gap, h, 0x22FFFFFF);
            float part = Math.max(0, Math.min(1, filled - s));
            if (part > 0) fill(g, x + s * sw, y, Math.max(1, Math.round((sw - gap) * part)), h, color);
        }
        if (gold > 0) fill(g, x, y + h - Math.max(1, S / 2), Math.round(w * Math.min(1, gold)), Math.max(1, S / 2), GOLD);
    }

    private static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    private static float ease(float t) { return t * t * (3 - 2 * t); }

    private static int hpColor(float f) { return f > 0.6f ? Theme.OK : f > 0.3f ? GOLD : Theme.DANGER; }
    private static int threatColor(int t) { return t >= 75 ? Theme.DANGER : t >= 50 ? 0xFFFF8A4C : t >= 25 ? GOLD : Theme.OK; }
    private static int pingColor(int p) { return p < 0 ? Theme.MUTED : p < 80 ? Theme.OK : p < 180 ? GOLD : Theme.DANGER; }
    private static int distanceColor(double d) { return d < 8 ? Theme.DANGER : d < 24 ? GOLD : Theme.TEXT; }

    private static int flagColor(String f, int accent) {
        return switch (f) {
            case "FIRE", "HIT", "WATCHING" -> Theme.DANGER;
            case "ELYTRA", "SPRINT" -> GOLD;
            case "BLOCK", "USING" -> CYAN;
            case "INVIS" -> 0xFFB98CFF;
            default -> accent;
        };
    }
}
