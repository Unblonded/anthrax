package unblonded.anthrax.gui;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import unblonded.anthrax.modules.GuiModule;
import unblonded.anthrax.modules.ModuleRegistry;
import unblonded.anthrax.modules.client.HudModule;
import unblonded.anthrax.modules.mining.AirUnderCheckModule;
import unblonded.anthrax.modules.utility.FpsChartModule;
import unblonded.anthrax.modules.visual.PlayerListModule;
import unblonded.anthrax.modules.world.BaseFinderModule;
import unblonded.anthrax.modules.world.WaypointsModule;
import unblonded.anthrax.modules.visual.NametagsModule;
import unblonded.anthrax.modules.client.FriendsModule;
import unblonded.anthrax.modules.combat.TotemPops;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static unblonded.anthrax.gui.Draw.*;

/**
 * The in-game overlay: watermark, animated module list, stacked info panels, notifications and
 * coordinates. Like the menu, it draws in real window pixels so text stays crisp at any GUI scale.
 */
public final class Hud {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<GuiModule, Float> SLIDE = new IdentityHashMap<>();
    private static final List<Row> ROWS = new ArrayList<>();
    private static long lastNs = System.nanoTime();
    private static String clock = "";
    private static long clockAt;

    private record Row(GuiModule module, String name, String info, int width, float slide) {}

    private Hud() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("anthrax", "hud"), Hud::render);
    }

    /** Draws one movable block at (x, y); boxW/boxH are its size last frame. Returns {w, h}, or null if hidden. */
    private interface Painter { int[] paint(int x, int y, int boxW, int boxH); }

    /**
     * Places, draws and records one movable block. Returns the next default stack position: blocks that
     * were never moved keep stacking like the classic layout, moved ones leave the stack.
     */
    private static int element(String id, int pw, int ph, int defX, int defY, int gap, Painter painter) {
        int[] size = HudLayout.size(id);
        boolean moved = HudLayout.hasSaved(id);
        int[] at = HudLayout.place(id, size[0], size[1], pw, ph, defX, defY);
        int[][] out = new int[1][];
        // a failing block is skipped (and reported once) instead of crashing the whole HUD
        unblonded.anthrax.modules.Safe.run(HudLayout.element(id).label(), () -> out[0] = painter.paint(at[0], at[1], size[0], size[1]));
        int[] drawn = out[0];
        if (drawn == null || drawn[0] <= 0 || drawn[1] <= 0) return defY;
        HudLayout.record(id, at[0], at[1], drawn[0], drawn[1]);
        return moved ? defY : defY + drawn[1] + gap;
    }

    /** The HUD's UI scale for a frame of this height (the editor needs the same scale). */
    static int hudScale(int ph) { return Math.max(1, Math.min(4, Math.round(ph / 720f) + HudModule.INSTANCE.sizeStep())); }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        long nowNs = System.nanoTime();
        float dt = Math.min(0.05f, (nowNs - lastNs) / 1_000_000_000f);
        lastNs = nowNs;
        boolean menuOpen = mc.gui.screen() instanceof AnthraxScreen;

        float gs = (float) mc.getWindow().getGuiScale();
        int pw = Math.round(g.guiWidth() * gs), ph = Math.round(g.guiHeight() * gs);
        int savedScale = Draw.S;
        Font savedFont = Draw.measureFont;
        Draw.S = hudScale(ph);
        Draw.measureFont = mc.font;
        Theme.tick(System.currentTimeMillis());
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            int accent = Theme.accent(), accent2 = Theme.accent2();
            int margin = u(6), gap = u(6);
            boolean editing = mc.gui.screen() instanceof HudEditorScreen;
            HudLayout.beginFrame();
            // Every block goes through HudLayout: a saved spot if it was moved, else the classic stacked default.
            int leftY = margin;
            HudModule hud = HudModule.INSTANCE;
            if (hud.watermark()) leftY = element("watermark", pw, ph, margin, leftY, gap, (x, y, bw, bh) -> watermark(g, mc, x, y, accent, accent2));
            AirUnderCheckModule dig = AirUnderCheckModule.INSTANCE;
            if (dig.showStatus()) leftY = element("dig", pw, ph, margin, leftY, gap, (x, y, bw, bh) -> digPanel(g, x, y, dig, accent));
            PlayerListModule list = PlayerListModule.INSTANCE;
            if (list.isEnabled() && (list.showWhenEmpty() || !list.entries().isEmpty()))
                leftY = element("players", pw, ph, margin, leftY, gap, (x, y, bw, bh) -> new int[]{PlayerIntelPanel.width(), PlayerIntelPanel.render(g, mc, x, y, accent)});
            BaseFinderModule finder = BaseFinderModule.INSTANCE;
            if (finder.showHud()) leftY = element("basefinder", pw, ph, margin, leftY, gap, (x, y, bw, bh) -> basePanel(g, mc, x, y, finder, accent));
            FpsChartModule chart = FpsChartModule.INSTANCE;
            if (chart.visible(mc)) {
                chart.sample(mc.getFps());
                leftY = element("fps", pw, ph, margin, leftY, gap, (x, y, bw, bh) -> fpsPanel(g, x, y, chart, mc.getFps(), accent, accent2));
            }
            if (!menuOpen && !editing) {
                nametags(g, mc, pw, ph, accent);
                waypointLabels(g, mc, pw, ph);
                pinnedCards(g, mc, pw, ph, accent);
            }
            if (!menuOpen && unblonded.anthrax.modules.combat.TargetHudModule.INSTANCE.isEnabled())
                element("target", pw, ph, pw / 2 + u(18), ph / 2 + u(14), 0, (x, y, bw, bh) -> targetHud(g, mc, x, y, dt, accent));
            int rightY = margin;
            if (hud.moduleList())
                rightY = element("modules", pw, ph, pw - margin - HudLayout.size("modules")[0], rightY, gap,
                        (x, y, bw, bh) -> moduleList(g, x, y, bw, pw, dt, accent, accent2));
            else SLIDE.clear();
            if (hud.effects())
                element("effects", pw, ph, pw - margin - HudLayout.size("effects")[0], rightY, gap, (x, y, bw, bh) -> effects(g, mc, x, y, bw, pw));
            if (hud.armorHud())
                element("armor", pw, ph, Math.round((g.guiWidth() / 2f + 91 + 6) * gs), Math.round((g.guiHeight() - 20) * gs), 0,
                        (x, y, bw, bh) -> armorHud(g, mc, x, y));
            trackClicks(mc);
            if (hud.keystrokes()) {
                int[] ks = keystrokeSize();
                element("keystrokes", pw, ph, pw - u(8) - ks[0], ph - u(64) - ks[1], 0, (x, y, bw, bh) -> keystrokes(g, mc, x, y, accent));
            }
            if (!menuOpen && hud.notifications()) {
                int tw = u(220);
                element("toasts", pw, ph, pw - u(10) - tw, ph - u(10) - HudLayout.size("toasts")[1], 0,
                        (x, y, bw, bh) -> toastStack(g, x, y, tw, bh, pw, ph, accent));
            }
            if (hud.coordinates() && !(mc.gui.screen() instanceof ChatScreen)) {
                int ch = HudLayout.size("coords")[1];
                element("coords", pw, ph, u(4), ph - u(6) - ch, 0, (x, y, bw, bh) -> coordinates(g, mc, x, y));
            }
        } finally {
            g.pose().popMatrix();
            Draw.S = savedScale;
            Draw.measureFont = savedFont;
        }
    }

    // ------------------------------------------------------------------ target HUD
    private static LivingEntity hudTarget;
    private static float hudHealth = -1, hudFade;

    private static int[] targetHud(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float dt, int accent) {
        var module = unblonded.anthrax.modules.combat.TargetHudModule.INSTANCE;
        LivingEntity t = unblonded.anthrax.modules.combat.CombatUtil.target();
        if (t != null && !(t instanceof Player) && !module.showMobs()) t = null;
        if (t != null && t != hudTarget) { hudTarget = t; hudHealth = -1; }
        hudFade += ((t != null ? 1 : 0) - hudFade) * Math.min(1, dt * 12);
        if (hudFade < 0.02f || hudTarget == null) { if (t == null) hudTarget = null; return null; }
        LivingEntity e = hudTarget;
        float max = Math.max(1, e.getMaxHealth()), hp = e.getHealth() + e.getAbsorptionAmount();
        if (hudHealth < 0) hudHealth = hp;
        hudHealth += (hp - hudHealth) * Math.min(1, dt * 10);

        int w = u(150), h = u(module.showArmor() ? 58 : 44);
        int boxY = y;
        y += Math.round((1 - hudFade) * u(8));
        int a = Math.round(hudFade * 255);
        shadow(g, x, y, w, h, u(12), u(8), Math.round(0x50 * hudFade));
        glass(g, x, y, w, h, u(12), alpha(0x0E1119, Math.round(0xD0 * hudFade)), 0.9f * hudFade);

        int face = u(28), fx = x + u(8), fy = y + u(8);
        if (e instanceof net.minecraft.client.player.AbstractClientPlayer p) {
            net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(g, p.getSkin(), fx, fy, face, (a << 24) | 0xFFFFFF);
        } else {
            round(g, fx, fy, face, face, u(6), alpha(accent, Math.round(0x50 * hudFade)));
            textC(g, fx + face / 2, fy + face / 2, e.getName().getString().substring(0, 1).toUpperCase(), alpha(0xFFFFFF, a), true);
        }
        int tx = fx + face + u(8), right = x + w - u(8);
        text(g, tx, fy + u(6), fit(e.getName().getString(), right - tx - u(30), true), alpha(Theme.TEXT, a), true);
        String dist = Math.round(Math.sqrt(mc.player.distanceToSqr(e)) * 10) / 10.0 + "m";
        text(g, right - textWidth(dist, false), fy + u(6), dist, alpha(Theme.MUTED, a), false);

        // status line: pops, and whether we are winning the trade
        float mine = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        String status = mine > hp + 2 ? "Winning" : mine < hp - 2 ? "Losing" : "Even";
        int statusCol = mine > hp + 2 ? Theme.OK : mine < hp - 2 ? Theme.DANGER : 0xFFFFC94A;
        String pops = e instanceof Player p ? TotemPops.pops(p) + " pops   " : "";
        text(g, tx, fy + u(19), pops + status, alpha(pops.isEmpty() ? statusCol : Theme.SUBTEXT, a), false);
        if (!pops.isEmpty()) text(g, tx + textWidth(pops, false), fy + u(19), status, alpha(statusCol, a), false);

        // health bar: smooth fill, red to green, absorption shown in gold past the max
        int by = y + u(42), bw = w - u(16), bh = u(5);
        round(g, fx, by, bw, bh, bh / 2, alpha(0xFFFFFF, Math.round(0x18 * hudFade)));
        float frac = Math.min(1, hudHealth / max);
        int fill = Math.max(bh, Math.round(bw * frac));
        round(g, fx, by, fill, bh, bh / 2, alpha(lerp(Theme.DANGER, Theme.OK, frac), a));
        if (hudHealth > max) {
            int gold = Math.round(bw * Math.min(1, (hudHealth - max) / max));
            round(g, fx, by, Math.max(bh, gold), bh, bh / 2, alpha(0xFFFFC94A, Math.round(0xC0 * hudFade)));
        }
        String hpText = String.format("%.1f", hp / 2f) + " ❤";
        text(g, right - textWidth(hpText, false), by - u(7), hpText, alpha(Theme.SUBTEXT, a), false);

        if (module.showArmor() && hudFade > 0.6f) {
            int ix = fx, iy = y + h - u(13), icon = u(10);
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
            for (EquipmentSlot s : slots) {
                ItemStack st = e.getItemBySlot(s);
                if (st.isEmpty()) continue;
                g.pose().pushMatrix();
                g.pose().translate(ix, iy);
                g.pose().scale(icon / 16f, icon / 16f);
                g.item(st, 0, 0);
                g.pose().popMatrix();
                ix += icon + u(4);
            }
        }
        return new int[]{w, h + y - boxY};
    }

    // ------------------------------------------------------------------ watermark
    private static int[] watermark(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int accent, int accent2) {
        long now = System.currentTimeMillis();
        if (now - clockAt > 1000) { clock = LocalTime.now().format(CLOCK); clockAt = now; }
        String info = mc.getFps() + " fps   " + ping(mc) + " ms   " + clock;
        int h = u(22), tile = h - u(8);
        int titleW = textWidth("Anthrax", true);
        int w = u(8) + tile + u(7) + titleW + u(10) + S + u(10) + textWidth(info, false) + u(10);
        shadow(g, x, y, w, h, h / 2, u(6), 0x40);
        glass(g, x, y, w, h, h / 2, alpha(0x0E1119, 0xC8), 0.9f);
        int cy = y + h / 2;
        logo(g, x + u(4), cy - tile / 2, tile, accent, Theme.accent2());
        int tx = x + u(4) + tile + u(7);
        if (Theme.idx == 0) textRainbow(g, tx, cy, "Anthrax", Theme.hue, 22f, true);
        else text(g, tx, cy, "Anthrax", Theme.TEXT, true);
        tx += titleW + u(10);
        fill(g, tx, cy - u(6), S, u(12), Theme.LINE);
        text(g, tx + S + u(10), cy, info, Theme.SUBTEXT, false);
        return new int[]{w, h};
    }

    private static int ping(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    // ------------------------------------------------------------------ module list
    private static int[] moduleList(GuiGraphicsExtractor g, int bx, int by, int boxW, int pw, float dt, int accent, int accent2) {
        ROWS.clear();
        boolean details = HudModule.INSTANCE.listInfo();
        for (GuiModule module : ModuleRegistry.all()) {
            if (module.isHiddenFromList()) continue;
            float slide = SLIDE.getOrDefault(module, 0f);
            float target = module.isEnabled() ? 1f : 0f;
            slide += (target - slide) * Math.min(1f, dt * 11f);
            if (Math.abs(target - slide) < 0.003f) slide = target;
            SLIDE.put(module, slide);
            if (slide <= 0.001f) continue;
            String info = details ? module.info() : null;
            if (info != null && info.isBlank()) info = null;
            int width = textWidth(module.name, false) + (info == null ? 0 : u(5) + textWidth(info, false));
            ROWS.add(new Row(module, module.name, info, width, slide));
        }
        ROWS.sort((a, b) -> Integer.compare(b.width(), a.width()));
        String mode = HudModule.INSTANCE.listColor();
        // Each module is its own rounded glass pill: soft shadow, frosted body with a top gloss, and a
        // glowing accent dot. Pills align to whichever screen side the list sits on, slide in from that
        // edge, and the stack closes up smoothly.
        int rowH = u(16), pad = u(7), gap = u(3), radius = u(6), dot = Math.max(3, u(5));
        if (ROWS.isEmpty()) return null;
        int extra = pad * 2 + dot + u(6);
        int listW = ROWS.getFirst().width() + extra;
        boolean right = HudLayout.rightSide(bx, listW, pw);
        int y = by;
        int n = ROWS.size();
        for (int i = 0; i < n; i++) {
            Row row = ROWS.get(i);
            float e = 1f - (1f - row.slide()) * (1f - row.slide()) * (1f - row.slide());
            int w = row.width() + extra;
            int slideOut = Math.round((1f - e) * (w + u(6)));
            int x = right ? bx + listW - w + slideOut : bx - slideOut;
            int color = switch (mode) {
                case "Rainbow" -> hsl(Theme.hue - i * 14f, 0.9f, 0.66f);
                case "White" -> Theme.TEXT;
                default -> lerp(accent, accent2, n <= 1 ? 0f : i / (float) (n - 1));
            };
            shadow(g, x, y, w, rowH, radius, u(4), Math.round(0x34 * e));
            glass(g, x, y, w, rowH, radius, alpha(0x0E1119, Math.round(0xB4 * e)), 0.85f * e);
            // faint accent wash from the dot side, so each pill picks up its color
            if (FANCY) hgrad(g, x + radius, y + S, w - 2 * radius, rowH - 2 * S, alpha(color, 0), alpha(color, Math.round(0x16 * e)));
            int cy = y + rowH / 2, dcx = x + w - pad - dot / 2;
            glow(g, dcx, cy, dot * 2, color, 3, Math.round(16 * e));
            circle(g, dcx, cy, dot / 2, color);
            text(g, x + pad, cy, row.name(), color, false);
            if (row.info() != null)
                text(g, x + pad + textWidth(row.name(), false) + u(5), cy, row.info(), Theme.MUTED, false);
            y += Math.round((rowH + gap) * e);
        }
        return new int[]{listW, Math.max(rowH, y - by - gap)};
    }

    // ------------------------------------------------------------------ nametags
    private static void nametags(GuiGraphicsExtractor g, Minecraft mc, int pw, int ph, int accent) {
        NametagsModule nt = NametagsModule.INSTANCE;
        if (!nt.isEnabled() || mc.level == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double rangeSq = nt.range() * nt.range();
        List<Entity> tagged = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering())
            if (nt.replaces(e) && mc.player.distanceToSqr(e) <= rangeSq) tagged.add(e);
        // farthest first, so nearer tags draw on top
        tagged.sort((a, b) -> Double.compare(mc.player.distanceToSqr(b), mc.player.distanceToSqr(a)));
        int start = Math.max(0, tagged.size() - 30);
        for (int i = start; i < tagged.size(); i++) nametag(g, mc, pw, ph, tagged.get(i), partial, nt);
    }

    private static void nametag(GuiGraphicsExtractor g, Minecraft mc, int pw, int ph, Entity e, float partial, NametagsModule nt) {
        Vec3 head = e.getPosition(partial).add(0, e.getBbHeight() + 0.35, 0);
        float[] p = unblonded.anthrax.render.WorldProjection.project(head, pw, ph);
        if (p == null || p[0] < -u(60) || p[0] > pw + u(60) || p[1] < -u(30) || p[1] > ph + u(30)) return;
        int ax = Math.round(p[0]), ay = Math.round(p[1]);
        boolean friend = FriendsModule.isFriend(e);
        String name = e.getDisplayName().getString();
        LivingEntity living = e instanceof LivingEntity l ? l : null;
        float hp = living == null ? 0 : living.getHealth() + living.getAbsorptionAmount();
        float maxHp = living == null ? 1 : Math.max(1, living.getMaxHealth());
        float frac = Math.min(1f, hp / maxHp);
        int hpColor = frac > 0.6f ? Theme.OK : frac > 0.3f ? 0xFFFFC94A : Theme.DANGER;

        String pingText = null;
        if (nt.ping() && e instanceof Player pl && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(pl.getUUID());
            if (info != null) pingText = info.getLatency() + "ms";
        }
        String hpText = nt.health() && living != null ? String.format("%.1f", hp) : null;
        String distText = nt.distance() ? Math.round(p[2]) + "m" : null;
        int pops = nt.pops() && e instanceof Player pl2 ? TotemPops.pops(pl2) : 0;
        String popText = pops > 0 ? "-" + pops : null;

        int gap = u(6);
        int w = u(10) + textWidth(name, true);
        if (pingText != null) w += gap + textWidth(pingText, false);
        if (hpText != null) w += gap + textWidth(hpText, true);
        if (distText != null) w += gap + textWidth(distText, false);
        if (popText != null) w += gap + textWidth(popText, true);
        w += u(10);
        int h = u(18);
        int x = ax - w / 2, y = ay - h;
        int body = friend ? alpha(FriendsModule.COLOR, 0x40) : alpha(0x0E1119, 0xC0);
        round(g, x, y, w, h, h / 2, body);
        outline(g, x, y, w, h, h / 2, Math.max(1, S / 2), friend ? alpha(FriendsModule.COLOR, 0xB0) : 0x30FFFFFF, 0x12FFFFFF);
        if (living != null && nt.health()) {
            int barW = w - h;
            round(g, x + h / 2, y + h - Math.max(2, S), barW, Math.max(2, S), S, 0x30000000);
            round(g, x + h / 2, y + h - Math.max(2, S), Math.max(S, Math.round(barW * frac)), Math.max(2, S), S, hpColor);
        }
        int tx = x + u(10), cy = y + h / 2 - S / 2;
        if (pingText != null) { text(g, tx, cy, pingText, Theme.MUTED, false); tx += textWidth(pingText, false) + gap; }
        text(g, tx, cy, name, friend ? FriendsModule.COLOR : (e.isCrouching() ? Theme.MUTED : 0xFFFFFFFF), true);
        tx += textWidth(name, true) + gap;
        if (hpText != null) { text(g, tx, cy, hpText, hpColor, true); tx += textWidth(hpText, true) + gap; }
        if (popText != null) { text(g, tx, cy, popText, 0xFFFFC94A, true); tx += textWidth(popText, true) + gap; }
        if (distText != null) text(g, tx, cy, distText, Theme.SUBTEXT, false);

        if (nt.equipment() && living != null) {
            EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
            List<ItemStack> items = new ArrayList<>();
            for (EquipmentSlot s : slots) { ItemStack st = living.getItemBySlot(s); if (!st.isEmpty()) items.add(st); }
            int icon = u(14), iw = items.size() * (icon + S);
            int ix = ax - iw / 2, iy = y - icon - S;
            for (ItemStack st : items) {
                g.pose().pushMatrix();
                g.pose().translate(ix, iy);
                g.pose().scale(icon / 16f, icon / 16f);
                g.item(st, 0, 0);
                g.pose().popMatrix();
                if (st.isDamageableItem()) {
                    float left = 1 - st.getDamageValue() / (float) Math.max(1, st.getMaxDamage());
                    fill(g, ix, iy + icon, icon, Math.max(1, S / 2), 0x80000000);
                    fill(g, ix, iy + icon, Math.round(icon * left), Math.max(1, S / 2), lerp(Theme.DANGER, Theme.OK, left));
                }
                ix += icon + S;
            }
        }
    }

    // ------------------------------------------------------------------ waypoint labels
    private static void waypointLabels(GuiGraphicsExtractor g, Minecraft mc, int pw, int ph) {
        WaypointsModule wp = WaypointsModule.INSTANCE;
        if (!wp.showLabels()) return;
        for (WaypointsModule.Waypoint w : wp.here()) {
            float[] p = unblonded.anthrax.render.WorldProjection.project(new Vec3(w.x + .5, w.y + 1.5, w.z + .5), pw, ph);
            if (p == null) continue;
            int ax = Math.round(p[0]), ay = Math.round(p[1]);
            if (ax < -u(40) || ax > pw + u(40) || ay < -u(40) || ay > ph + u(40)) continue;
            String label = w.name + "   " + Math.round(p[2]) + "m";
            int tw = textWidth(label, true) + u(24), th = u(20);
            int x = ax - tw / 2, y = ay - th - u(4);
            glass(g, x, y, tw, th, th / 2, alpha(0x0E1119, 0xC8), .7f);
            icon(g, x + u(11), y + th / 2, w.death ? Icons.SKULL : Icons.LOCATION_DOT, w.color);
            text(g, x + u(19), y + th / 2, label, Theme.TEXT, true);
        }
    }

    // ------------------------------------------------------------------ armor, effects, keystrokes
    private static int[] armorHud(GuiGraphicsExtractor g, Minecraft mc, int x0, int y) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int icon = u(16), gap = u(4);
        // defaults to the right of the hotbar (182 GUI px wide, centred)
        int x = x0;
        for (EquipmentSlot s : slots) {
            ItemStack st = mc.player.getItemBySlot(s);
            if (st.isEmpty()) continue;
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(icon / 16f, icon / 16f);
            g.item(st, 0, 0);
            g.pose().popMatrix();
            if (st.isDamageableItem()) {
                float left = 1 - st.getDamageValue() / (float) Math.max(1, st.getMaxDamage());
                textC(g, x + icon / 2, y + icon + u(5), Math.round(left * 100) + "%", lerp(Theme.DANGER, Theme.OK, left), false);
            }
            x += icon + gap;
        }
        return x == x0 ? null : new int[]{x - x0 - gap, icon + u(10)};
    }

    private static int[] effects(GuiGraphicsExtractor g, Minecraft mc, int bx, int by, int boxW, int pw) {
        var active = mc.player.getActiveEffects();
        if (active.isEmpty()) return null;
        int rowH = u(16), y = by, listW = 0;
        for (MobEffectInstance eff : active) {
            String name = eff.getEffect().value().getDisplayName().getString() + (eff.getAmplifier() > 0 ? " " + (eff.getAmplifier() + 1) : "");
            listW = Math.max(listW, textWidth(name, false) + u(8) + textWidth("00:00", false) + u(16));
        }
        boolean right = HudLayout.rightSide(bx, listW, pw);
        for (MobEffectInstance eff : active) {
            String name = eff.getEffect().value().getDisplayName().getString() + (eff.getAmplifier() > 0 ? " " + (eff.getAmplifier() + 1) : "");
            String time;
            if (eff.isInfiniteDuration()) time = "**:**";
            else { int secs = eff.getDuration() / 20; time = (secs / 60) + ":" + String.format("%02d", secs % 60); }
            int color = 0xFF000000 | eff.getEffect().value().getColor();
            int w = textWidth(name, false) + u(8) + textWidth(time, false) + u(16);
            int x = right ? bx + listW - w : bx;
            fill(g, x, y, w, rowH, alpha(0x0E1119, 0xB8));
            round(g, x + w - Math.max(2, S) - S, y + S, Math.max(2, S), rowH - 2 * S, S, color);
            text(g, x + u(6), y + rowH / 2, name, lerp(color, 0xFFFFFFFF, 0.4f), false);
            text(g, x + u(6) + textWidth(name, false) + u(8), y + rowH / 2, time, Theme.MUTED, false);
            y += rowH;
        }
        return new int[]{listW, y - by};
    }

    private static final java.util.ArrayDeque<Long> LEFT_CLICKS = new java.util.ArrayDeque<>(), RIGHT_CLICKS = new java.util.ArrayDeque<>();
    private static boolean leftWasDown, rightWasDown;

    private static void trackClicks(Minecraft mc) {
        long window = mc.getWindow().handle();
        boolean l = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        long now = System.currentTimeMillis();
        if (l && !leftWasDown && mc.gui.screen() == null) LEFT_CLICKS.add(now);
        if (r && !rightWasDown && mc.gui.screen() == null) RIGHT_CLICKS.add(now);
        leftWasDown = l;
        rightWasDown = r;
        while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst() > 1000) LEFT_CLICKS.pollFirst();
        while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst() > 1000) RIGHT_CLICKS.pollFirst();
    }

    private static int[] keystrokeSize() {
        int k = u(22), gap = u(3);
        return new int[]{k * 3 + gap * 2, k * 3 + gap * 3 + u(12)};
    }

    private static int[] keystrokes(GuiGraphicsExtractor g, Minecraft mc, int x0, int y0, int accent) {
        var o = mc.options;
        int k = u(22), gap = u(3);
        int gridW = k * 3 + gap * 2;
        key(g, x0 + k + gap, y0, k, k, "W", o.keyUp.isDown(), accent);
        key(g, x0, y0 + k + gap, k, k, "A", o.keyLeft.isDown(), accent);
        key(g, x0 + k + gap, y0 + k + gap, k, k, "S", o.keyDown.isDown(), accent);
        key(g, x0 + 2 * (k + gap), y0 + k + gap, k, k, "D", o.keyRight.isDown(), accent);
        int half = (gridW - gap) / 2, my = y0 + 2 * (k + gap);
        key(g, x0, my, half, k, LEFT_CLICKS.size() + " CPS", o.keyAttack.isDown(), accent);
        key(g, x0 + half + gap, my, half, k, RIGHT_CLICKS.size() + " CPS", o.keyUse.isDown(), accent);
        key(g, x0, my + k + gap, gridW, u(12), "", o.keyJump.isDown(), accent);
        return keystrokeSize();
    }

    private static void key(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean down, int accent) {
        round(g, x, y, w, h, u(5), down ? alpha(accent, 0xC0) : alpha(0x0E1119, 0xB0));
        outline(g, x, y, w, h, u(5), Math.max(1, S / 2), down ? 0x60FFFFFF : 0x22FFFFFF, 0x10FFFFFF);
        if (!label.isEmpty()) textC(g, x + w / 2, y + h / 2, label, down ? 0xFFFFFFFF : Theme.SUBTEXT, true);
    }

    // ------------------------------------------------------------------ info panels
    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        shadow(g, x, y, w, h, u(10), u(6), 0x40);
        glass(g, x, y, w, h, u(10), alpha(0x0E1119, 0xC8), 0.8f);
        round(g, x + u(3), y + u(8), Math.max(2, S), h - u(16), S, accent);
    }

    private static int[] digPanel(GuiGraphicsExtractor g, int x, int y, AirUnderCheckModule dig, int accent) {
        AirUnderCheckModule.Status status = dig.status();
        int color = switch (status) {
            case SAFE -> Theme.OK;
            case WATER -> 0xFF4AA8FF;
            default -> Theme.DANGER;
        };
        String label = switch (status) {
            case SAFE -> "SAFE TO DIG";
            case LAVA -> "LAVA BELOW";
            case WATER -> "WATER BELOW";
            case DROP -> dig.dropDistance() + " BLOCK DROP";
        };
        String yText = "Y " + dig.playerY();
        int h = u(20);
        int w = u(10) + u(6) + u(6) + textWidth(label, true) + u(14) + textWidth(yText, false) + u(10);
        panel(g, x, y, w, h, color);
        int cy = y + h / 2;
        if (status != AirUnderCheckModule.Status.SAFE && FANCY) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0);
            circle(g, x + u(13), cy, u(5), alpha(color, (int) (60 * pulse)));
        }
        circle(g, x + u(13), cy, u(3), color);
        text(g, x + u(22), cy, label, color, true);
        textR(g, x + w - u(10), cy, yText, Theme.MUTED, false);
        return new int[]{w, h};
    }

    /** Gauge animation per base id (ids survive re-clustering, objects do not). */
    private static final Map<Long, float[]> PIN_RINGS = new java.util.HashMap<>();

    /**
     * Base Finder card: the focused base (tracked, or the one you are looking toward) with live gauges,
     * its storage inventory and the strongest activity clue, followed by the next few bases.
     */
    private static int[] basePanel(GuiGraphicsExtractor g, Minecraft mc, int x, int y, BaseFinderModule finder, int accent) {
        List<BaseFinderModule.Base> all = finder.bases();
        BaseFinderModule.Base focus = null;   // the focused base's stats are pinned over it in the world
        int w = u(236), headH = u(22), rowH = u(24);
        List<BaseFinderModule.Base> others = new ArrayList<>();
        for (BaseFinderModule.Base b : all) if (b != focus && others.size() < (focus == null ? 5 : 3)) others.add(b);
        int cardH = 0;
        int h = headH + cardH + Math.max(focus == null ? 1 : 0, others.size()) * rowH + u(6);
        panel(g, x, y, w, h, accent);
        icon(g, x + u(14), y + headH / 2 + S, Icons.MAP_LOCATION_DOT, accent);
        text(g, x + u(24), y + headH / 2 + S, "BASE FINDER", Theme.DIM, true);
        String status = finder.queued() > 0 ? "scanning " + finder.queued() : all.size() + " found   B for intel";
        textR(g, x + w - u(10), y + headH / 2 + S, status, finder.queued() > 0 ? 0xFFFFC94A : Theme.MUTED, false);
        int ry = y + headH;
        if (all.isEmpty()) {
            text(g, x + u(14), ry + rowH / 2, "No player activity detected yet", Theme.MUTED, false);
            return new int[]{w, h};
        }
        for (BaseFinderModule.Base b : others) {
            int cy = ry + rowH / 2;
            arrow(g, mc, x + u(18), cy, b);
            double dist = Math.sqrt(mc.player.distanceToSqr(b.x + .5, mc.player.getY(), b.z + .5));
            int tx = x + u(34);
            text(g, tx, ry + u(8), (b.known ? "* " : "") + b.type, b.color, true);
            textR(g, x + w - u(10), ry + u(8), Math.round(dist) + "m", Theme.TEXT, false);
            text(g, tx, ry + u(18), fit(b.x + ", " + b.z + "   " + b.summary, w - u(44) - u(38), false), Theme.MUTED, false);
            int barW = u(30);
            round(g, x + w - u(10) - barW, ry + u(17), barW, u(3), u(1), 0x26FFFFFF);
            round(g, x + w - u(10) - barW, ry + u(17), Math.max(u(3), barW * b.unraided / 100), u(3), u(1), lerp(Theme.DANGER, Theme.OK, b.unraided / 100f));
            ry += rowH;
        }
        return new int[]{w, h};
    }

    // ------------------------------------------------------------------ cards pinned over bases in the world
    private static void pinnedCards(GuiGraphicsExtractor g, Minecraft mc, int pw, int ph, int accent) {
        BaseFinderModule finder = BaseFinderModule.INSTANCE;
        if (!finder.showPinnedCards()) { PIN_RINGS.clear(); return; }
        List<BaseFinderModule.Base> all = finder.bases();
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (BaseFinderModule.Base b : all) ids.add(b.id);
        PIN_RINGS.keySet().retainAll(ids);
        BaseFinderModule.Base focus = finder.focused(mc);
        int others = 0;
        for (BaseFinderModule.Base b : all) {
            if (b == focus || others >= 6) continue;
            others++;
            pin(g, pw, ph, b, false, accent);
        }
        if (focus != null) pin(g, pw, ph, focus, finder.showIntelCard(), accent);
    }

    private static void pin(GuiGraphicsExtractor g, int pw, int ph, BaseFinderModule.Base b, boolean full, int accent) {
        float[] p = unblonded.anthrax.render.WorldProjection.project(
                new net.minecraft.world.phys.Vec3(b.x + .5, b.topY() + 1.5, b.z + .5), pw, ph);
        if (p == null) return;
        int ax = Math.round(p[0]), ay = Math.round(p[1]);
        if (ax < -u(40) || ax > pw + u(40) || ay < -u(40) || ay > ph + u(40)) return;
        String dist = Math.round(p[2]) + "m";
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 260.0);
        if (FANCY) circle(g, ax, ay, u(4) + Math.round(u(3) * pulse), alpha(b.color, (int) (0x50 * (1 - pulse))));
        circle(g, ax, ay, u(3), b.color);

        if (!full) {
            String label = b.type + "   " + dist;
            int w = textWidth(label, true) + u(26), h = u(20);
            int x = ax - w / 2, y = ay - u(12) - h;
            fill(g, ax - Math.max(1, S / 2), y + h, Math.max(1, S), ay - y - h - u(3), alpha(b.color, 0x90));
            glass(g, x, y, w, h, h / 2, alpha(0x0E1119, 0xC8), .7f);
            circle(g, x + u(11), y + h / 2, u(3), b.color);
            text(g, x + u(19), y + h / 2, label, Theme.TEXT, true);
            return;
        }

        String size = BaseFinderModule.INSTANCE.cardSize();
        boolean small = size.equals("Small"), large = size.equals("Large");
        float[] rings = PIN_RINGS.computeIfAbsent(b.id, k -> new float[3]);
        int[] targets = {b.confidence, b.unraided, b.activity};
        for (int i = 0; i < 3; i++) rings[i] += (targets[i] / 100f - rings[i]) * 0.12f;
        String inv = b.shulkers + " shulkers   " + (b.singleChests + b.doubleChests) + " chests   " + String.format("%,d", b.storageSlots) + " slots";

        int statsW = 0;
        if (small) {
            for (int i = 0; i < 3; i++) statsW += textWidth(Math.round(rings[i] * 100) + "%", true) + u(8);
            statsW += textWidth(" made", false) + textWidth(" unraided", false) + textWidth(" active", false);
        }
        int cw = small ? Math.max(u(140), Math.max(statsW, textWidth(inv, false)) + u(18)) : large ? u(184) : u(160);
        int ch = small ? u(50) : large ? u(104) : u(88);
        int x = Math.max(u(6), Math.min(pw - cw - u(6), ax - cw / 2));
        boolean below = ay - u(14) - ch < u(6);
        int y = below ? ay + u(14) : ay - u(14) - ch;
        int stemTop = below ? ay + u(4) : y + ch, stemBottom = below ? y : ay - u(4);
        fill(g, ax - Math.max(1, S / 2), Math.min(stemTop, stemBottom), Math.max(1, S), Math.abs(stemBottom - stemTop), alpha(b.color, 0xA0));
        shadow(g, x, y, cw, ch, u(12), u(8), 0x50);
        glass(g, x, y, cw, ch, u(12), alpha(0x0E1119, 0xD2), 1f);
        round(g, x + u(10), y + S, cw - u(20), Math.max(2, S), S, alpha(b.color, 0xC0));

        int hy = y + u(12);
        circle(g, x + u(12), hy, u(3), b.color);
        text(g, x + u(20), hy, (BaseFinderModule.INSTANCE.isTracked(b) ? "★ " : "") + b.type, b.color, true);
        textR(g, x + cw - u(10), hy, dist, Theme.TEXT, true);

        if (small) {
            // one line of colored numbers instead of gauges
            int tx = x + u(10), ty = y + u(25);
            String[] labels = {" made", " unraided", " active"};
            int[] colors = {Theme.accent2(), lerp(Theme.DANGER, Theme.OK, rings[1]), 0xFFFFC94A};
            for (int i = 0; i < 3; i++) {
                String v = Math.round(rings[i] * 100) + "%";
                text(g, tx, ty, v, colors[i], true);
                tx += textWidth(v, true);
                text(g, tx, ty, labels[i], Theme.MUTED, false);
                tx += textWidth(labels[i], false) + u(8);
            }
            text(g, x + u(10), y + u(38), fit(inv, cw - u(20), false), Theme.SUBTEXT, false);
            return;
        }

        int ringR = large ? u(12) : u(9), ringT = large ? u(3) : Math.max(2, u(2.5f));
        int gy = large ? y + u(44) : y + u(36);
        String[] labels = {"made", "unraided", "active"};
        int gw = (cw - u(16)) / 3;
        for (int i = 0; i < 3; i++) {
            int gcx = x + u(8) + i * gw + gw / 2;
            int c2 = i == 1 ? lerp(Theme.DANGER, Theme.OK, rings[i]) : i == 2 ? 0xFFFFC94A : Theme.accent2();
            ring(g, gcx, gy, ringR, ringT, rings[i], 0x22FFFFFF, i == 1 ? c2 : i == 2 ? 0xFF6F7BFF : accent, c2);
            textC(g, gcx, gy, Integer.toString(Math.round(rings[i] * 100)), 0xFFFFFFFF, true);
            textC(g, gcx, gy + ringR + u(8), labels[i], Theme.MUTED, false);
        }
        text(g, x + u(10), y + ch - u(23), fit(inv, cw - u(20), false), Theme.SUBTEXT, false);
        String clue = b.raidDamage >= 15 && !b.unraidedWhy.isEmpty()
                ? b.raidLabel + ": " + b.unraidedWhy.getFirst().substring(2)
                : b.activityLabel + (b.activityWhy.isEmpty() ? "" : ": " + b.activityWhy.getFirst());
        text(g, x + u(10), y + ch - u(11), fit(clue, cw - u(20), false), Theme.MUTED, false);
    }

    /** Direction arrow toward a base, rotated relative to the camera. */
    private static void arrow(GuiGraphicsExtractor g, Minecraft mc, int ax, int cy, BaseFinderModule.Base b) {
        circle(g, ax, cy, u(9), alpha(b.color, 0x30));
        g.pose().pushMatrix();
        g.pose().translate(ax, cy);
        g.pose().rotate((float) Math.toRadians(BaseFinderModule.relativeYaw(mc, b) - 45f));
        icon(g, 0, 0, Icons.LOCATION_ARROW, b.color);
        g.pose().popMatrix();
    }

    private static int[] fpsPanel(GuiGraphicsExtractor g, int x, int y, FpsChartModule chart, int fps, int accent, int accent2) {
        int w = u(196), headH = u(20), chartH = u(40), h = headH + chartH + u(10);
        panel(g, x, y, w, h, accent);
        int filled = chart.filled();
        int max = 60, min = Integer.MAX_VALUE;
        long sum = 0;
        for (int i = FpsChartModule.SAMPLES - filled; i < FpsChartModule.SAMPLES; i++) {
            int v = chart.value(i);
            max = Math.max(max, v);
            min = Math.min(min, v);
            sum += v;
        }
        int avg = filled == 0 ? fps : (int) (sum / filled);
        if (filled == 0) min = fps;
        int fpsColor = fps >= 60 ? Theme.OK : fps >= 30 ? 0xFFFFC94A : Theme.DANGER;
        text(g, x + u(10), y + headH / 2 + S, fps + " FPS", fpsColor, true);
        textR(g, x + w - u(10), y + headH / 2 + S, "min " + min + "   avg " + avg, Theme.MUTED, false);
        int cx = x + u(10), cw = w - u(20), cy = y + headH + u(2);
        fill(g, cx, cy, cw, chartH, 0x40000000);
        float scale = max * 1.15f;
        int line60 = cy + chartH - Math.round(chartH * (60f / scale));
        fill(g, cx, line60, cw, 1, alpha(Theme.TEXT, 0x22));
        int samples = FpsChartModule.SAMPLES;
        for (int i = 0; i < samples; i++) {
            int v = chart.value(i);
            if (v <= 0) continue;
            int x0 = cx + cw * i / samples, x1 = cx + cw * (i + 1) / samples;
            int bh = Math.max(1, Math.min(chartH, Math.round(chartH * (v / scale))));
            int c = v < 30 ? Theme.DANGER : v < 60 ? 0xFFFFC94A : lerp(accent, accent2, i / (float) samples);
            vgrad(g, x0, cy + chartH - bh, Math.max(1, x1 - x0), bh, c, alpha(c, 0x50));
        }
        return new int[]{w, h};
    }

    // ------------------------------------------------------------------ coordinates
    private static int[] coordinates(GuiGraphicsExtractor g, Minecraft mc, int bx, int by) {
        var p = mc.player;
        String pos = String.format("XYZ  %.0f  %.0f  %.0f", p.getX(), p.getY(), p.getZ());
        String other = null;
        if (mc.level != null && mc.level.dimension() == Level.OVERWORLD)
            other = String.format("Nether  %.0f  %.0f", p.getX() / 8, p.getZ() / 8);
        else if (mc.level != null && mc.level.dimension() == Level.NETHER)
            other = String.format("Overworld  %.0f  %.0f", p.getX() * 8, p.getZ() * 8);
        String facing = "Facing " + capitalize(p.getDirection().getName());
        int lineH = u(13), x = bx + u(4), y = by + u(2);
        int w = Math.max(textWidth(pos, false), Math.max(other == null ? 0 : textWidth(other, false), textWidth(facing, false))) + u(16);
        int h = lineH * (other == null ? 2 : 3) + u(4);
        round(g, x - u(4), y - u(2), w, h, u(5), alpha(Theme.BG, 0xB0));
        text(g, x + u(4), y + lineH / 2, pos, Theme.TEXT, false);
        if (other != null) text(g, x + u(4), y + lineH + lineH / 2, other, Theme.MUTED, false);
        text(g, x + u(4), y + lineH * (other == null ? 1 : 2) + lineH / 2, facing, Theme.MUTED, false);
        return new int[]{w, h};
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ notifications
    /** HUD toasts inside their movable box: stacks up from the bottom edge on the lower half, down otherwise. */
    private static int[] toastStack(GuiGraphicsExtractor g, int bx, int by, int w, int boxH, int pw, int ph, int accent) {
        int n = Notifications.active().size();
        if (n == 0) return null;
        int h = u(40), gap = u(6), total = n * h + (n - 1) * gap;
        boolean up = HudLayout.bottomSide(by, boxH, ph);
        // anchor to the edge the box hugs, so the stack grows away from it
        int top = up ? by + boxH - total : by;
        toastsAt(g, bx, top, w, HudLayout.rightSide(bx, w, pw), accent);
        return new int[]{w, total};
    }

    /** Shared by the HUD and the menu so toasts look identical everywhere. */
    static void toasts(GuiGraphicsExtractor g, int pw, int ph, int accent, int bottomGap) {
        int n = Notifications.active().size();
        if (n == 0) return;
        int w = u(220), h = u(40), gap = u(6);
        toastsAt(g, pw - u(10) - w, ph - bottomGap - (n * h + (n - 1) * gap), w, true, accent);
    }

    /** Draws the toast stack top-down from (bx, top), newest at the bottom, sliding in from the near side. */
    private static void toastsAt(GuiGraphicsExtractor g, int bx, int top, int w, boolean fromRight, int accent) {
        List<Notifications.Entry> entries = Notifications.active();
        long now = System.currentTimeMillis();
        int h = u(40), gap = u(6);
        for (int i = 0; i < entries.size(); i++) {
            Notifications.Entry t = entries.get(i);
            float age = (now - t.born()) / 1000f;
            float life = Notifications.LIFETIME_MS / 1000f;
            float in = 1f - easeOut(age / 0.28f), out = easeOut((age - (life - 0.35f)) / 0.35f);
            int slide = Math.round((in + out) * (w + u(20)));
            int x = fromRight ? bx + slide : bx - slide;
            int y = top + i * (h + gap);
            int col = Notifications.color(t.kind(), accent);
            shadow(g, x, y, w, h, u(14), u(8), 0x50);
            glass(g, x, y, w, h, u(14), alpha(0x12141D, 0xE6), 1f);
            int iconCx = x + u(19), cy = y + h / 2;
            circle(g, iconCx, cy, u(11), alpha(col, 0x38));
            icon(g, iconCx, cy, switch (t.kind()) {
                case SUCCESS -> Icons.CIRCLE_CHECK;
                case WARNING -> Icons.TRIANGLE_EXCLAMATION;
                case ERROR -> Icons.CIRCLE_XMARK;
                default -> Icons.CIRCLE_INFO;
            }, col);
            int tx = x + u(36), maxW = w - u(36) - u(10);
            text(g, tx, y + u(13), fit(t.title(), maxW, true), col, true);
            text(g, tx, y + u(27), fit(t.message(), maxW, false), Theme.TEXT, false);
            float prog = 1f - Math.min(1f, age / life);
            round(g, x + u(14), y + h - u(5), Math.round((w - u(28)) * prog), u(2), u(1), alpha(col, 0xC0));
        }
    }

    static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float i = 1f - t;
        return 1f - i * i * i;
    }

    private record FitKey(String value, int width, boolean bold, int scale) {}
    private static final Map<FitKey, String> FIT_CACHE = new java.util.LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<FitKey, String> e) { return size() > 1024; }
    };

    /** Truncates with "..." to fit; cached, since the same strings are fitted every frame. */
    static String fit(String value, int maxWidth, boolean bold) {
        if (value == null) return "";
        return FIT_CACHE.computeIfAbsent(new FitKey(value, maxWidth, bold, S), k -> fitUncached(value, maxWidth, bold));
    }

    private static String fitUncached(String value, int maxWidth, boolean bold) {
        if (textWidth(value, bold) <= maxWidth) return value;
        int end = value.length();
        while (end > 0 && textWidth(value.substring(0, end) + "...", bold) > maxWidth) end--;
        return value.substring(0, end) + "...";
    }
}
