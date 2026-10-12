package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.modules.visual.FontSizeModule;
import unblonded.anthrax.modules.world.BaseFinderModule;
import unblonded.anthrax.modules.world.BaseFinderModule.Base;
import unblonded.anthrax.modules.world.BaseFinderModule.Poi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Base Intel: a liquid-glass dossier for everything Base Finder has detected. The left column lists
 * bases (or natural structures); the right side breaks the selected one down into gauges for how sure
 * we are that players made it, how likely it is to still be unraided, and how active it looks, plus
 * its storage inventory, ownership clues, defenses, age and the raw evidence behind every number.
 */
public final class IntelScreen extends Screen {
    private record Hit(int x, int y, int w, int h, IntConsumer press) {
        boolean contains(int px, int py) { return inside(px, py, x, y, w, h); }
    }

    private static final ItemStack[] STORAGE_ICONS = {
            new ItemStack(Items.CHEST), new ItemStack(Items.TRAPPED_CHEST), new ItemStack(Items.BARREL),
            new ItemStack(Items.SHULKER_BOX), new ItemStack(Items.ENDER_CHEST), new ItemStack(Items.HOPPER),
            new ItemStack(Items.DISPENSER), new ItemStack(Items.FURNACE)};
    private static final String[] STORAGE_LABELS = {"Chests", "Trapped", "Barrels", "Shulkers", "Ender", "Hoppers", "Dispensers", "Furnaces"};

    private final List<Hit> hits = new ArrayList<>();
    private boolean structuresTab;
    /** The Map tab; static so the Intel screen reopens on it. */
    private static boolean mapTab;
    private boolean mapPressed, mapMoved;
    private int mapButton;
    private double mapPressX, mapPressY;
    private int selected;
    private float listScroll, listScrollTarget, detailScroll, detailScrollTarget, detailHeight;
    private int listX, listY, listW, listH, detailX, detailY, detailW, detailH;
    private float gs = 1;
    private int mx, my;
    private long lastNs = System.nanoTime();
    private final long openNs = System.nanoTime();
    private final float[] ringAnim = new float[3];
    private Object animatedFor;
    /** Selected base id, so the selection survives the list re-sorting. */
    private long selectedBaseId = -1;

    public IntelScreen() { super(Component.literal("Base Intel")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (Draw.FX > 0) extractBlurredBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Draw.measureFont = font;
        gs = (float) minecraft.getWindow().getGuiScale();
        int pw = Math.round(width * gs), ph = Math.round(height * gs);
        mx = Math.round(mouseX * gs);
        my = Math.round(mouseY * gs);
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        Theme.tick(System.currentTimeMillis());
        Draw.S = FontSizeModule.apply(Math.min(Math.round(ph / 720f), Math.min(pw / 660, ph / 420)));
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            hits.clear();
            hintText = null;
            draw(g, pw, ph, dt);
            if (hintText != null) {
                int w = textWidth(hintText, false) + u(20), h = u(24);
                int tx = Math.min(pw - w - u(4), mx + u(12)), ty = my + u(16);
                shadow(g, tx, ty, w, h, h / 2, u(8), 0x60);
                glass(g, tx, ty, w, h, h / 2, alpha(0x14161F, 0xE6), 1f);
                text(g, tx + u(10), ty + h / 2, hintText, Theme.TEXT, false);
            }
        } finally {
            g.pose().popMatrix();
        }
    }

    // ------------------------------------------------------------------ layout
    private void draw(GuiGraphicsExtractor g, int pw, int ph, float dt) {
        BaseFinderModule finder = BaseFinderModule.INSTANCE;
        int accent = Theme.accent(), accent2 = Theme.accent2();
        float open = Hud.easeOut((System.nanoTime() - openNs) / 1e9f / 0.4f);
        g.fill(0, 0, pw, ph, alpha(0x05060B, (int) ((FX > 0 ? 0x5C : 0xC0) * open)));
        g.fillGradient(0, 0, pw, ph, alpha(accent, (int) (0x10 * open)), alpha(accent2, (int) (0x18 * open)));

        int ww = Math.min(pw - u(20), u(700)), wh = Math.min(ph - u(20), u(430));
        int wx = (pw - ww) / 2, wy = (ph - wh) / 2 + Math.round((1 - open) * u(20));
        int r = u(24);
        shadow(g, wx, wy, ww, wh, r, u(22), (int) (0x70 * open));
        glass(g, wx, wy, ww, wh, r, alpha(0x0E1119, 0xB8), 1f);

        // ---- header
        int headH = u(52), cy = wy + headH / 2;
        int bead = u(28), bx = wx + u(16);
        bead(g, bx, cy - bead / 2, bead, accent);
        icon(g, bx + bead / 2, cy, Icons.MAP_LOCATION_DOT, 0xFFFFFFFF);
        text(g, bx + bead + u(10), cy - u(6), "Base Intel", Theme.TEXT, true);
        List<Base> bases = finder.bases();
        List<Poi> pois = finder.pois();
        String sub = finder.isEnabled()
                ? finder.layerName() + "   " + bases.size() + " bases   " + pois.size() + " structures   " + finder.scanned() + " chunks scanned"
                + (finder.queued() > 0 ? "   (" + finder.queued() + " queued)" : "")
                : "Base Finder is off: enable it to start scanning";
        text(g, bx + bead + u(10), cy + u(8), sub, finder.isEnabled() ? Theme.MUTED : 0xFFFFC94A, false);

        int segW = u(300), segH = u(26), segX = wx + ww - u(16) - u(26) - u(10) - segW;
        segmented(g, segX, cy - segH / 2, segW, segH,
                new String[]{"Bases  " + bases.size(), "Structures  " + pois.size(), "Map"}, mapTab ? 2 : structuresTab ? 1 : 0, accent,
                tab -> { mapTab = tab == 2; structuresTab = tab == 1; selected = 0; listScrollTarget = 0; detailScrollTarget = 0; });
        int close = u(26), closeX = wx + ww - u(16) - close;
        boolean closeHov = inside(mx, my, closeX, cy - close / 2, close, close);
        glass(g, closeX, cy - close / 2, close, close, close / 2, closeHov ? alpha(Theme.DANGER, 0x90) : 0x14FFFFFF, closeHov ? 1 : .4f);
        icon(g, closeX + close / 2, cy, Icons.XMARK, closeHov ? 0xFFFFFFFF : Theme.SUBTEXT);
        hits.add(new Hit(closeX, cy - close / 2, close, close, b -> onClose()));
        fill(g, wx + r, wy + headH, ww - 2 * r, Math.max(1, S / 2), 0x16FFFFFF);

        if (mapTab) {
            IntelMap.draw(g, wx + u(12), wy + headH + u(10), ww - u(24), wh - headH - u(22), mx, my, accent,
                    (hx, hy, hw, hh, press) -> hits.add(new Hit(hx, hy, hw, hh, press)), dt);
            return;
        }

        // ---- list
        listX = wx + u(12); listY = wy + headH + u(10); listW = u(230); listH = wh - headH - u(22);
        glass(g, listX, listY, listW, listH, u(18), 0x0CFFFFFF, .3f);
        int count = structuresTab ? pois.size() : bases.size();
        selected = Math.max(0, Math.min(selected, Math.max(0, count - 1)));
        drawList(g, bases, pois, dt, accent);

        // ---- detail
        detailX = listX + listW + u(12); detailY = listY; detailW = wx + ww - u(12) - detailX; detailH = listH;
        if (count == 0) {
            int ccx = detailX + detailW / 2, ccy = detailY + detailH / 2;
            icon(g, ccx, ccy - u(20), structuresTab ? Icons.EARTH_AMERICAS : Icons.MAP_LOCATION_DOT, Theme.DIM);
            textC(g, ccx, ccy + u(4), structuresTab ? "No natural structures recognised yet" : "No bases detected yet", Theme.MUTED, true);
            textC(g, ccx, ccy + u(20), finder.isEnabled() ? "Explore: every chunk the server sends you is analysed" : "Turn on Base Finder in the World category",
                    Theme.DIM, false);
            return;
        }
        if (!structuresTab) {
            // keep following the same base even if re-clustering re-sorted the list
            if (selectedBaseId >= 0) for (int i = 0; i < bases.size(); i++) if (bases.get(i).id == selectedBaseId) { selected = i; break; }
            selectedBaseId = bases.get(selected).id;
        }
        Object current = structuresTab ? pois.get(selected) : Long.valueOf(bases.get(selected).id);
        if (!current.equals(animatedFor)) { animatedFor = current; ringAnim[0] = ringAnim[1] = ringAnim[2] = 0; detailScrollTarget = 0; }

        float maxScroll = Math.max(0, detailHeight - detailH);
        detailScrollTarget = Math.max(0, Math.min(detailScrollTarget, maxScroll));
        detailScroll += (detailScrollTarget - detailScroll) * Math.min(1f, dt * 14f);
        g.enableScissor(detailX, detailY, detailX + detailW, detailY + detailH);
        int top = detailY - Math.round(detailScroll);
        int end = structuresTab ? drawPoi(g, pois.get(selected), top, accent) : drawBase(g, bases.get(selected), top, dt, accent, accent2);
        g.disableScissor();
        detailHeight = end - top + u(8);
        if (detailHeight > detailH) {
            int barH = Math.max(u(24), Math.round(detailH * (detailH / detailHeight)));
            int barY = detailY + Math.round((detailH - barH) * (detailScroll / Math.max(1f, detailHeight - detailH)));
            round(g, detailX + detailW - u(4), barY, u(3), barH, u(2), 0x50FFFFFF);
        }
    }

    private final float[] segAnim = {-1};

    /** Pill tab control; the highlight glides between options. */
    private void segmented(GuiGraphicsExtractor g, int x, int y, int w, int h, String[] labels, int current, int accent, IntConsumer pick) {
        round(g, x, y, w, h, h / 2, 0x30000000);
        outline(g, x, y, w, h, h / 2, Math.max(1, S / 2), 0x14FFFFFF, 0x22FFFFFF);
        int n = labels.length, part = w / n;
        if (segAnim[0] < 0) segAnim[0] = current;
        segAnim[0] += (current - segAnim[0]) * .25f;
        glassTint(g, x + Math.round(segAnim[0] * part) + S, y + S, part - 2 * S, h - 2 * S, (h - 2 * S) / 2, accent, .65f);
        for (int i = 0; i < n; i++) {
            boolean on = i == current;
            textC(g, x + i * part + part / 2, y + h / 2, labels[i], on ? 0xFFFFFFFF : Theme.MUTED, on);
            final int idx = i;
            hits.add(new Hit(x + i * part, y, part, h, btn -> pick.accept(idx)));
        }
    }

    private void drawList(GuiGraphicsExtractor g, List<Base> bases, List<Poi> pois, float dt, int accent) {
        Minecraft mc = Minecraft.getInstance();
        int rowH = u(48), pad = u(6);
        int count = structuresTab ? pois.size() : bases.size();
        float content = count * rowH + pad * 2;
        listScrollTarget = Math.max(0, Math.min(listScrollTarget, Math.max(0, content - listH)));
        listScroll += (listScrollTarget - listScroll) * Math.min(1f, dt * 14f);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY + pad - Math.round(listScroll);
        for (int i = 0; i < count; i++, y += rowH) {
            if (y + rowH < listY || y > listY + listH) continue;
            int rx = listX + pad, rw = listW - pad * 2;
            boolean sel = i == selected, hov = inside(mx, my, rx, y, rw, rowH - u(4)) && inside(mx, my, listX, listY, listW, listH);
            if (sel) glassTint(g, rx, y, rw, rowH - u(4), u(14), accent, .5f);
            else if (hov) round(g, rx, y, rw, rowH - u(4), u(14), 0x14FFFFFF);
            int color, cyy = y + (rowH - u(4)) / 2;
            String title, line2, right;
            float gauge;
            boolean flag;
            if (structuresTab) {
                Poi p = pois.get(i);
                color = p.color; title = p.type; flag = p.touched;
                line2 = p.notes.isEmpty() ? p.x + ", " + p.z : p.notes.getFirst();
                right = mc.player == null ? "" : Math.round(Math.sqrt(mc.player.distanceToSqr(p.x, mc.player.getY(), p.z))) + "m";
                gauge = -1;
            } else {
                Base b = bases.get(i);
                color = b.color; title = (BaseFinderModule.INSTANCE.isTracked(b) ? "★ " : "") + b.type; flag = b.known;
                line2 = b.summary;
                right = mc.player == null ? "" : Math.round(Math.sqrt(mc.player.distanceToSqr(b.x, mc.player.getY(), b.z))) + "m";
                gauge = b.unraided / 100f;
            }
            circle(g, rx + u(16), cyy, u(11), alpha(color, 0x38));
            circle(g, rx + u(16), cyy, u(4), color);
            int tx = rx + u(32), tw = rw - u(32) - u(10);
            text(g, tx, cyy - u(8), Hud.fit(title + (flag && !structuresTab ? "  (known)" : ""), tw - u(40), true), sel ? 0xFFFFFFFF : Theme.TEXT, true);
            textR(g, rx + rw - u(10), cyy - u(8), right, Theme.SUBTEXT, false);
            text(g, tx, cyy + u(8), Hud.fit(line2, tw - (gauge >= 0 ? u(40) : 0), false), flag && structuresTab ? 0xFFFFC94A : Theme.MUTED, false);
            if (gauge >= 0) {
                int gw = u(32), gx = rx + rw - u(10) - gw;
                round(g, gx, cyy + u(7), gw, u(3), u(1), 0x26FFFFFF);
                round(g, gx, cyy + u(7), Math.max(u(3), Math.round(gw * gauge)), u(3), u(1), lerp(Theme.DANGER, Theme.OK, gauge));
            }
            final int idx = i;
            hits.add(new Hit(rx, Math.max(y, listY), rw, Math.min(rowH - u(4), listY + listH - Math.max(y, listY)), b -> { selected = idx; if (!structuresTab && idx < bases.size()) selectedBaseId = bases.get(idx).id; }));
        }
        g.disableScissor();
    }

    // ------------------------------------------------------------------ base dossier
    private int drawBase(GuiGraphicsExtractor g, Base b, int y, float dt, int accent, int accent2) {
        Minecraft mc = Minecraft.getInstance();
        BaseFinderModule finder = BaseFinderModule.INSTANCE;
        int x = detailX + u(6), w = detailW - u(16);

        // title + actions
        text(g, x + u(4), y + u(14), b.type, b.color, true);
        double dist = mc.player == null ? 0 : Math.sqrt(mc.player.distanceToSqr(b.x, mc.player.getY(), b.z));
        text(g, x + u(4), y + u(30), b.x + "  " + b.y + "  " + b.z + "     " + Math.round(dist) + "m away     "
                + b.chunks + " chunk" + (b.chunks == 1 ? "" : "s") + (b.known ? "     found before" : ""), Theme.MUTED, false);
        boolean tracked = finder.isTracked(b);
        int bw = u(78), bh = u(24), bx2 = x + w - bw, bx1 = bx2 - u(8) - bw;
        pill(g, bx1, y + u(10), bw, bh, "Copy", Icons.LOCATION_ARROW, false, accent, () -> {
            minecraft.keyboardHandler.setClipboard(b.x + " " + b.y + " " + b.z);
            Notifications.success("Base Intel", "Copied " + b.x + " " + b.y + " " + b.z);
        });
        pill(g, bx2, y + u(10), bw, bh, tracked ? "Tracking" : "Track", Icons.COMPASS, tracked, accent, () -> finder.track(b));
        y += u(46);

        // the three gauges
        int[] targets = {b.confidence, b.unraided, b.activity};
        String[] labels = {"Player-made", "Unraided", b.activityLabel};
        int[][] colors = {{accent, accent2}, {Theme.DANGER, Theme.OK}, {0xFF6F7BFF, 0xFFFFC94A}};
        int tileW = (w - u(16)) / 3, tileH = u(104);
        for (int i = 0; i < 3; i++) {
            ringAnim[i] += (targets[i] / 100f - ringAnim[i]) * Math.min(1f, dt * 6f);
            int tx = x + i * (tileW + u(8));
            glass(g, tx, y, tileW, tileH, u(16), 0x10FFFFFF, .5f);
            int rcx = tx + tileW / 2, rcy = y + u(44), rr = u(28);
            int c1 = colors[i][0], c2 = colors[i][1];
            int fg = i == 1 ? lerp(c1, c2, ringAnim[i]) : c2;
            ring(g, rcx, rcy, rr, u(6), ringAnim[i], 0x22FFFFFF, i == 1 ? fg : c1, fg);
            textC(g, rcx, rcy, Math.round(ringAnim[i] * 100) + "%", 0xFFFFFFFF, true);
            textC(g, rcx, y + tileH - u(16), labels[i], Theme.SUBTEXT, true);
        }
        y += tileH + u(10);

        // storage inventory strip
        int[] counts = {b.singleChests + b.doubleChests, b.trappedChests, b.barrels, b.shulkers, b.enderChests, b.hoppers, b.dispensers, b.furnaces};
        int stripH = u(72);
        glass(g, x, y, w, stripH, u(16), 0x10FFFFFF, .5f);
        text(g, x + u(14), y + u(14), "Storage", Theme.TEXT, true);
        textR(g, x + w - u(14), y + u(14), b.storageSlots > 0 ? "about " + String.format("%,d", b.storageSlots) + " slots" + (b.doubleChests > 0 ? "   " + b.doubleChests + " double chests" : "") : "no containers", Theme.MUTED, false);
        int cell = (w - u(20)) / counts.length;
        for (int i = 0; i < counts.length; i++) {
            int ccx = x + u(10) + i * cell + cell / 2, iy = y + u(30);
            boolean any = counts[i] > 0;
            int iconSize = u(18);
            g.pose().pushMatrix();
            g.pose().translate(ccx - iconSize / 2f, iy);
            g.pose().scale(iconSize / 16f, iconSize / 16f);
            g.item(STORAGE_ICONS[i], 0, 0);
            g.pose().popMatrix();
            if (!any) round(g, ccx - iconSize / 2 - S, iy - S, iconSize + 2 * S, iconSize + 2 * S, u(4), 0x900E1119);
            textC(g, ccx, iy + iconSize + u(10), any ? Integer.toString(counts[i]) : "-", any ? 0xFFFFFFFF : Theme.DIM, any);
            if (inside(mx, my, ccx - cell / 2, iy, cell, iconSize + u(16))) hint(STORAGE_LABELS[i]);
        }
        y += stripH + u(10);

        // two-column fact tiles
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile("Activity evidence", Icons.BOLT, b.activityWhy));
        tiles.add(new Tile("Raid evidence", Icons.SHIELD_HALVED, b.unraidedWhy.isEmpty() ? List.of("No raid signals either way") : b.unraidedWhy));
        List<String> owners = new ArrayList<>();
        if (b.beds > 0) owners.add(b.beds + " bed" + (b.beds == 1 ? "" : "s") + "  (about " + Math.max(1, b.beds) + " player" + (b.beds == 1 ? "" : "s") + ")");
        if (b.respawnAnchors > 0) owners.add(b.respawnAnchors + " charged respawn anchor" + (b.respawnAnchors == 1 ? "" : "s"));
        if (!b.owners.isEmpty()) owners.add(b.pets + " tamed pet" + (b.pets == 1 ? "" : "s") + " owned by " + String.join(", ", b.owners));
        if (!b.heads.isEmpty()) owners.add("Player heads: " + String.join(", ", b.heads));
        if (!b.named.isEmpty()) owners.add("Named mobs: " + String.join(", ", b.named));
        if (b.villagers > 0) owners.add(b.villagers + " villager" + (b.villagers == 1 ? "" : "s"));
        if (!b.playersHere.isEmpty()) owners.add("Here now: " + String.join(", ", b.playersHere));
        if (owners.isEmpty()) owners.add("No names, pets or beds found");
        tiles.add(new Tile("Ownership", Icons.USER, owners));
        List<String> defense = new ArrayList<>();
        if (b.obsidian > 0) defense.add(b.obsidian + " obsidian");
        if (b.ironDoors > 0) defense.add(b.ironDoors + " iron door" + (b.ironDoors == 1 ? "" : "s"));
        if (b.traps > 0) defense.add(b.traps + " pressure plate/tripwire trigger" + (b.traps == 1 ? "" : "s") + " (possible traps)");
        if (b.dispensers > 0) defense.add(b.dispensers + " dispenser" + (b.dispensers == 1 ? "" : "s") + "/dropper" + (b.dispensers == 1 ? "" : "s"));
        if (b.tnt > 0) defense.add(b.tnt + " TNT");
        if (defense.isEmpty()) defense.add("No defenses spotted");
        tiles.add(new Tile("Defenses", Icons.SHIELD_HALVED, defense));
        List<String> age = new ArrayList<>();
        if (b.amethystHours >= 0) age.add(b.amethystHours >= 12 ? "Amethyst clock: 12h+ of player time nearby"
                : String.format("Amethyst clock: about %.1fh of player time nearby (%d faces)", b.amethystHours, b.amethystFaces));
        else age.add("Amethyst clock: no geode within 4 chunks");
        if (b.copperFresh + b.copperAged > 0)
            age.add("Copper: " + b.copperFresh + " fresh, " + b.copperAged + " weathered" + (b.copperWaxed > 0 ? ", " + b.copperWaxed + " waxed" : ""));
        if (b.crops > 0) age.add("Crops: " + b.cropsMature + " of " + b.crops + " ripe");
        if (b.litFurnaces > 0 || b.furnaces > 0) age.add("Furnaces: " + b.litFurnaces + " of " + b.furnaces + " burning");
        if (b.poweredRedstone > 0) age.add(b.poweredRedstone + " powered redstone component" + (b.poweredRedstone == 1 ? "" : "s"));
        if (!b.nearbyStructures.isEmpty()) age.add("Near: " + String.join(", ", b.nearbyStructures));
        age.addAll(b.notes);
        tiles.add(new Tile("Age and area", Icons.COMPASS, age));
        List<String> valuables = new ArrayList<>();
        if (b.beacons > 0) valuables.add(b.beacons + " beacon" + (b.beacons == 1 ? "" : "s"));
        if (b.valuableBlocks - b.beacons > 0) valuables.add((b.valuableBlocks - b.beacons) + " diamond/netherite/emerald block" + (b.valuableBlocks - b.beacons == 1 ? "" : "s"));
        if (b.framesFull + b.framesEmpty > 0) valuables.add(b.framesFull + " filled, " + b.framesEmpty + " empty item frames");
        if (b.standsDressed + b.standsBare > 0) valuables.add(b.standsDressed + " dressed, " + b.standsBare + " bare armor stands");
        if (b.lecternBooks + b.jukeboxDiscs + b.brewingBottles > 0)
            valuables.add(b.lecternBooks + " books, " + b.jukeboxDiscs + " discs, " + b.brewingBottles + " potions in place");
        if (valuables.isEmpty()) valuables.add("Nothing notable on display");
        tiles.add(new Tile("Valuables on display", Icons.GEM, valuables));
        if (!b.signs.isEmpty()) tiles.add(new Tile("Signs", Icons.FILE_LINES, b.signs));

        int colW = (w - u(8)) / 2;
        int[] colY = {y, y};
        for (Tile t : tiles) {
            int col = colY[0] <= colY[1] ? 0 : 1;
            int tx = x + col * (colW + u(8));
            colY[col] += drawTile(g, t, tx, colY[col], colW) + u(8);
        }
        return Math.max(colY[0], colY[1]);
    }

    private record Tile(String title, String icon, List<String> lines) {}

    private int drawTile(GuiGraphicsExtractor g, Tile t, int x, int y, int w) {
        int lineH = u(15), pad = u(12);
        List<String> wrapped = new ArrayList<>();
        for (String line : t.lines) wrapped.addAll(wrap(line, w - pad * 2 - u(8)));
        int h = u(30) + wrapped.size() * lineH + u(6);
        glass(g, x, y, w, h, u(14), 0x10FFFFFF, .45f);
        icon(g, x + pad + u(5), y + u(15), t.icon, Theme.accent());
        text(g, x + pad + u(16), y + u(15), t.title, Theme.TEXT, true);
        int ly = y + u(30) + lineH / 2;
        for (String line : wrapped) {
            int color = line.startsWith("+ ") ? Theme.OK : line.startsWith("- ") ? Theme.DANGER : Theme.SUBTEXT;
            text(g, x + pad, ly, line, color, false);
            ly += lineH;
        }
        return h;
    }

    private record WrapKey(String text, int width, int scale) {}
    private static final java.util.Map<WrapKey, List<String>> WRAP_CACHE = new java.util.LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<WrapKey, List<String>> e) { return size() > 512; }
    };

    /** Greedy word wrap by rendered width; continuation lines are indented. Cached per frame-stable input. */
    private static List<String> wrap(String text, int maxW) {
        return WRAP_CACHE.computeIfAbsent(new WrapKey(text, maxW, S), k -> wrapUncached(text, maxW));
    }

    private static List<String> wrapUncached(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (textWidth(candidate, false) <= maxW || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append("   ").append(word);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        for (int i = 0; i < out.size(); i++) out.set(i, Hud.fit(out.get(i), maxW, false));
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ structure dossier
    private int drawPoi(GuiGraphicsExtractor g, Poi p, int y, int accent) {
        Minecraft mc = Minecraft.getInstance();
        int x = detailX + u(6), w = detailW - u(16);
        text(g, x + u(4), y + u(14), p.type, p.color, true);
        double dist = mc.player == null ? 0 : Math.sqrt(mc.player.distanceToSqr(p.x, mc.player.getY(), p.z));
        text(g, x + u(4), y + u(30), p.x + "  " + p.y + "  " + p.z + "     " + Math.round(dist) + "m away", Theme.MUTED, false);
        int bw = u(78), bh = u(24);
        pill(g, x + w - bw, y + u(10), bw, bh, "Copy", Icons.LOCATION_ARROW, false, accent, () -> {
            minecraft.keyboardHandler.setClipboard(p.x + " " + p.y + " " + p.z);
            Notifications.success("Base Intel", "Copied " + p.x + " " + p.y + " " + p.z);
        });
        y += u(46);
        List<String> lines = new ArrayList<>(p.notes);
        if (lines.isEmpty()) lines.add("Recognised from its signature blocks; nothing indicates players have been here");
        lines.add(p.touched ? "- Signs that players already found it" : "+ No sign of players yet");
        return y + drawTile(g, new Tile("What we can tell", Icons.CIRCLE_INFO, lines), x, y, w);
    }

    // ------------------------------------------------------------------ widgets
    private void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String glyph, boolean on, int accent, Runnable action) {
        boolean hov = inside(mx, my, x, y, w, h);
        if (on) glassTint(g, x, y, w, h, h / 2, accent, hov ? .9f : .7f);
        else glass(g, x, y, w, h, h / 2, hov ? 0x28FFFFFF : 0x14FFFFFF, hov ? 1 : .4f);
        icon(g, x + u(14), y + h / 2, glyph, on || hov ? 0xFFFFFFFF : Theme.SUBTEXT);
        text(g, x + u(26), y + h / 2, label, on || hov ? 0xFFFFFFFF : Theme.SUBTEXT, false);
        hits.add(new Hit(x, y, w, h, b -> action.run()));
    }

    private String hintText;
    private void hint(String s) { hintText = s; }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int px = Math.round((float) event.x() * gs), py = Math.round((float) event.y() * gs);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(px, py)) { h.press().accept(event.button()); return true; }
        }
        if (mapTab && IntelMap.contains(px, py)) {
            // decide on release whether this was a click on a marker or a drag of the map
            mapPressed = true;
            mapMoved = false;
            mapButton = event.button();
            mapPressX = px;
            mapPressY = py;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (mapPressed) {
            double px = event.x() * gs, py = event.y() * gs;
            if (Math.abs(px - mapPressX) + Math.abs(py - mapPressY) > u(4)) mapMoved = true;
            if (mapMoved) IntelMap.drag(dragX * gs, dragY * gs);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (mapPressed) {
            mapPressed = false;
            if (!mapMoved) IntelMap.click((int) Math.round(mapPressX), (int) Math.round(mapPressY), mapButton);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int px = Math.round((float) mouseX * gs), py = Math.round((float) mouseY * gs);
        if (mapTab) {
            if (IntelMap.contains(px, py)) IntelMap.scroll(px, py, scrollY);
            return true;
        }
        if (inside(px, py, listX, listY, listW, listH)) listScrollTarget -= (float) scrollY * u(48);
        else detailScrollTarget -= (float) scrollY * u(48);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        int count = structuresTab ? BaseFinderModule.INSTANCE.pois().size() : BaseFinderModule.INSTANCE.bases().size();
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            selected = Math.max(0, Math.min(count - 1, selected + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            var bases = BaseFinderModule.INSTANCE.bases();
            if (!structuresTab && selected < bases.size()) selectedBaseId = bases.get(selected).id;
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            // Bases -> Structures -> Map
            if (mapTab) { mapTab = false; structuresTab = false; }
            else if (structuresTab) mapTab = true;
            else structuresTab = true;
            selected = 0;
            return true;
        }
        int bind = BaseFinderModule.INSTANCE.intelKey();
        if (bind != unblonded.anthrax.modules.Setting.Key.NONE && event.key() == bind) { onClose(); return true; }
        return super.keyPressed(event);
    }
}
