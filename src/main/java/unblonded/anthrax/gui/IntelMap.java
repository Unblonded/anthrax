package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import unblonded.anthrax.modules.world.BaseFinderModule;
import unblonded.anthrax.modules.world.BaseFinderModule.Base;
import unblonded.anthrax.modules.world.BaseFinderModule.LoggedBase;
import unblonded.anthrax.modules.world.BaseFinderModule.Poi;
import unblonded.anthrax.modules.world.WaypointsModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Top-down intel map for the Base Intel screen: every base ever logged on this server and dimension
 * (from the persistent base log), live finds, natural structures, waypoints and the player, on a zoomable,
 * draggable grid. Filters per type, an activity heat overlay, hover cards, click a marker to waypoint it.
 * View state is static, so the map reopens where you left it.
 */
final class IntelMap {
    private IntelMap() {}

    interface HitSink { void add(int x, int y, int w, int h, IntConsumer press); }

    private enum Kind { HISTORY, LIVE, POI, WAYPOINT }
    private record Marker(Kind kind, int sx, int sy, int color, String title, List<String> lines, int x, int y, int z) {}

    private static double zoom = 4, zoomTarget = 4;          // blocks per screen pixel
    private static double centerX, centerZ;
    private static boolean follow = true, centered;
    private static boolean showHistory = true, showStructures = true, showWaypoints = true, heat;
    private static final Set<String> HIDDEN = new HashSet<>();
    private static List<Marker> markers = List.of();
    private static int rx, ry, rw, rh;

    // ------------------------------------------------------------------ drawing
    static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, int accent, HitSink hits, float dt) {
        rx = x; ry = y; rw = w; rh = h;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (follow || !centered)) {
            double k = centered ? Math.min(1, dt * 10) : 1;
            centerX += (mc.player.getX() - centerX) * k;
            centerZ += (mc.player.getZ() - centerZ) * k;
            centered = true;
        }
        zoom += (zoomTarget - zoom) * Math.min(1, dt * 12);

        round(g, x, y, w, h, u(16), 0xC006080E);
        outline(g, x, y, w, h, u(16), Math.max(1, S / 2), 0x22FFFFFF, 0x10FFFFFF);
        g.enableScissor(x + S, y + S, x + w - S, y + h - S);
        int step = grid(g, x, y, w, h, accent);

        List<Marker> list = collect(mc);
        if (heat) for (Marker m : list) {
            if (m.kind == Kind.WAYPOINT || m.kind == Kind.POI) continue;
            int r = (int) Math.max(u(12), Math.min(u(140), 180 / zoom));
            if (FANCY) glow(g, m.sx, m.sy, r, m.color, 3, m.kind == Kind.LIVE ? 26 : 16);
            else circle(g, m.sx, m.sy, r / 2, alpha(m.color, 0x20));
        }
        double t = System.currentTimeMillis() * .001;
        for (Marker m : list) drawMarker(g, m, t);
        player(g, mc, accent);
        markers = list;
        g.disableScissor();

        chrome(g, x, y, w, h, accent, hits, list, step);
        Marker hover = nearest(mx, my, u(9));
        if (hover != null) card(g, hover, mx, my);
    }

    /** Grid lines at a spacing that suits the zoom, stronger every 4th, with coordinate labels. */
    private static int grid(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        int[] steps = {16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768};
        int step = steps[steps.length - 1];
        for (int s : steps) if (s / zoom >= u(42)) { step = s; break; }
        double left = centerX - (w / 2.0) * zoom, top = centerZ - (h / 2.0) * zoom;
        int t = Math.max(1, S / 2);
        for (long gx = (long) Math.floor(left / step) * step; gx <= left + w * zoom; gx += step) {
            int sx = sx(gx);
            boolean major = Math.floorMod(gx / step, 4) == 0, axis = gx == 0;
            fill(g, sx, y, t, h, axis ? alpha(accent, 0x50) : major ? 0x1CFFFFFF : 0x0CFFFFFF);
            if (major || axis) text(g, sx + u(3), y + u(9), Long.toString(gx), Theme.DIM, false);
        }
        for (long gz = (long) Math.floor(top / step) * step; gz <= top + h * zoom; gz += step) {
            int sy = sy(gz);
            boolean major = Math.floorMod(gz / step, 4) == 0, axis = gz == 0;
            fill(g, x, sy, w, t, axis ? alpha(accent, 0x50) : major ? 0x1CFFFFFF : 0x0CFFFFFF);
            if (major || axis) text(g, x + u(4), sy - u(6), Long.toString(gz), Theme.DIM, false);
        }
        return step;
    }

    private static List<Marker> collect(Minecraft mc) {
        List<Marker> out = new ArrayList<>();
        BaseFinderModule finder = BaseFinderModule.INSTANCE;
        List<Base> live = finder.bases();
        if (showHistory) {
            for (LoggedBase b : BaseFinderModule.history()) {
                if (HIDDEN.contains(b.type())) continue;
                boolean dup = false;
                for (Base l : live) if (l.type.equals(b.type()) && sq(l.x - b.x()) + sq(l.z - b.z()) < 64 * 64) { dup = true; break; }
                if (dup) continue;
                out.add(new Marker(Kind.HISTORY, sx(b.x()), sy(b.z()), b.color(), b.type(),
                        List.of(b.x() + "  " + b.y() + "  " + b.z() + distance(mc, b.x(), b.z()),
                                "Logged " + b.time().replace('T', ' ').substring(0, Math.min(16, b.time().length())),
                                "Score " + b.score() + "   confidence " + b.confidence() + "%", b.summary()),
                        b.x(), b.y(), b.z()));
            }
        }
        if (showStructures) for (Poi p : finder.pois())
            out.add(new Marker(Kind.POI, sx(p.x), sy(p.z), p.color, p.type,
                    List.of(p.x + "  " + p.y + "  " + p.z + distance(mc, p.x, p.z), p.touched ? "Players have been here" : "Looks untouched"),
                    p.x, p.y, p.z));
        for (Base b : live) {
            if (HIDDEN.contains(b.type)) continue;
            out.add(new Marker(Kind.LIVE, sx(b.x), sy(b.z), b.color, b.type + (b.known ? "  (known)" : "  (new)"),
                    List.of(b.x + "  " + b.y + "  " + b.z + distance(mc, b.x, b.z),
                            "Live now   score " + Math.round(b.score) + "   confidence " + b.confidence + "%", b.summary),
                    b.x, b.y, b.z));
        }
        if (showWaypoints) for (WaypointsModule.Waypoint wp : WaypointsModule.INSTANCE.here())
            out.add(new Marker(Kind.WAYPOINT, sx(wp.x), sy(wp.z), wp.color, wp.name,
                    List.of(wp.x + "  " + wp.y + "  " + wp.z + distance(mc, wp.x, wp.z), wp.death ? "Death point" : "Waypoint"),
                    wp.x, wp.y, wp.z));
        return out;
    }

    private static void drawMarker(GuiGraphicsExtractor g, Marker m, double t) {
        if (m.sx < rx - u(20) || m.sx > rx + rw + u(20) || m.sy < ry - u(20) || m.sy > ry + rh + u(20)) return;
        switch (m.kind) {
            case HISTORY -> {
                circle(g, m.sx, m.sy, u(4), 0x90000000);
                circle(g, m.sx, m.sy, u(3), alpha(m.color, 0xB0));
            }
            case LIVE -> {
                float pulse = (float) ((t * .8) % 1);
                circle(g, m.sx, m.sy, u(4) + Math.round(u(8) * pulse), alpha(m.color, Math.round(0x70 * (1 - pulse))));
                circle(g, m.sx, m.sy, u(6), 0xC0000000);
                circle(g, m.sx, m.sy, u(5), m.color);
                circle(g, m.sx, m.sy, Math.max(1, u(2)), 0xFFFFFFFF);
                if (zoom < 6) text(g, m.sx + u(9), m.sy, m.title, m.color, true);
            }
            case POI -> {
                g.pose().pushMatrix();
                g.pose().translate(m.sx, m.sy);
                g.pose().rotate((float) (Math.PI / 4));
                int d = u(3);
                fill(g, -d - S / 2, -d - S / 2, 2 * d + S, 2 * d + S, 0x90000000);
                fill(g, -d, -d, 2 * d, 2 * d, alpha(m.color, 0xC0));
                g.pose().popMatrix();
            }
            case WAYPOINT -> {
                icon(g, m.sx, m.sy - u(5), Icons.LOCATION_DOT, m.color);
                if (zoom < 10) text(g, m.sx + u(8), m.sy - u(5), m.title, m.color, false);
            }
        }
    }

    private static void player(GuiGraphicsExtractor g, Minecraft mc, int accent) {
        if (mc.player == null) return;
        int px = sx(mc.player.getX()), py = sy(mc.player.getZ());
        float yaw = mc.player.getYRot();
        // faint view cone
        for (int side = -1; side <= 1; side += 2) {
            double a = Math.toRadians(yaw + side * 35);
            int ex = px + (int) Math.round(-Math.sin(a) * u(60)), ey = py + (int) Math.round(Math.cos(a) * u(60));
            line(g, px, py, ex, ey, u(60), Math.max(1, S / 2), alpha(accent, 0x40));
        }
        circle(g, px, py, u(9), alpha(accent, 0x30));
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        // the arrow glyph points up-right; yaw 0 faces +z, which is down on a north-up map
        g.pose().rotate((float) Math.toRadians(yaw + 180 - 45));
        icon(g, 0, 0, Icons.LOCATION_ARROW, 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    /** Filter chips, view buttons, counts, scale bar and the controls hint. */
    private static void chrome(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, HitSink hits, List<Marker> list, int step) {
        int cx = x + u(10), cy = y + u(10), ch = u(20);
        cx = chip(g, cx, cy, ch, "History", Icons.CLOCK_ROTATE_LEFT, showHistory, accent, hits, () -> showHistory = !showHistory);
        cx = chip(g, cx, cy, ch, "Structures", Icons.EARTH_AMERICAS, showStructures, accent, hits, () -> showStructures = !showStructures);
        cx = chip(g, cx, cy, ch, "Waypoints", Icons.LOCATION_DOT, showWaypoints, accent, hits, () -> showWaypoints = !showWaypoints);
        chip(g, cx, cy, ch, "Heat", Icons.FIRE, heat, accent, hits, () -> heat = !heat);
        // one chip per base type present, so whole categories can be hidden
        Map<String, Integer> types = new LinkedHashMap<>();
        for (LoggedBase b : BaseFinderModule.history()) types.putIfAbsent(b.type(), b.color());
        for (Base b : BaseFinderModule.INSTANCE.bases()) types.putIfAbsent(b.type, b.color);
        int tx = x + u(10), ty = cy + ch + u(6);
        for (Map.Entry<String, Integer> e : types.entrySet()) {
            String type = e.getKey();
            int tw = textWidth(type, false) + u(24);
            if (tx + tw > x + w - u(110)) { tx = x + u(10); ty += ch + u(4); }
            boolean on = !HIDDEN.contains(type);
            round(g, tx, ty, tw, ch, ch / 2, on ? alpha(e.getValue(), 0x30) : 0x40000000);
            circle(g, tx + u(9), ty + ch / 2, u(3), on ? e.getValue() : alpha(e.getValue(), 0x50));
            text(g, tx + u(16), ty + ch / 2, type, on ? Theme.TEXT : Theme.DIM, false);
            hits.add(tx, ty, tw, ch, b -> { if (!HIDDEN.remove(type)) HIDDEN.add(type); });
            tx += tw + u(4);
        }

        // view buttons, top right
        int bs = u(24), bx = x + w - u(10) - bs, by = y + u(10);
        button(g, bx, by, bs, Icons.PLUS, accent, hits, () -> zoomTarget = Math.max(.08, zoomTarget / 1.6));
        button(g, bx, by + bs + u(4), bs, Icons.MINUS, accent, hits, () -> zoomTarget = Math.min(96, zoomTarget * 1.6));
        button(g, bx, by + 2 * (bs + u(4)), bs, Icons.CROSSHAIRS, follow ? accent : 0, hits, () -> follow = true);

        // counts, bottom left
        int logged = 0, liveCount = 0, wps = 0;
        for (Marker m : list) { if (m.kind == Kind.HISTORY) logged++; else if (m.kind == Kind.LIVE) liveCount++; else if (m.kind == Kind.WAYPOINT) wps++; }
        String counts = logged + " logged   " + liveCount + " live   " + wps + " waypoints";
        int lh = u(20), lw = textWidth(counts, false) + u(16);
        round(g, x + u(10), y + h - u(10) - lh, lw, lh, lh / 2, 0x90000000);
        text(g, x + u(18), y + h - u(10) - lh / 2, counts, Theme.SUBTEXT, false);

        // scale bar, bottom right
        int barPx = (int) Math.round(step / zoom);
        int sbx = x + w - u(14) - barPx, sby = y + h - u(18);
        fill(g, sbx, sby, barPx, Math.max(1, S), Theme.SUBTEXT);
        fill(g, sbx, sby - u(3), Math.max(1, S / 2), u(4), Theme.SUBTEXT);
        fill(g, sbx + barPx, sby - u(3), Math.max(1, S / 2), u(4), Theme.SUBTEXT);
        textC(g, sbx + barPx / 2, sby - u(8), step + " blocks", Theme.SUBTEXT, false);

        String hint = "Scroll zoom  ·  Drag pan  ·  Click marker: waypoint  ·  Right-click: copy";
        textC(g, x + w / 2, y + h - u(16), Hud.fit(hint, w - lw - barPx - u(60), false), Theme.DIM, false);
    }

    private static int chip(GuiGraphicsExtractor g, int x, int y, int h, String label, String glyph, boolean on, int accent, HitSink hits, Runnable toggle) {
        int w = textWidth(label, false) + u(30);
        if (on) glassTint(g, x, y, w, h, h / 2, accent, .6f);
        else round(g, x, y, w, h, h / 2, 0x60000000);
        icon(g, x + u(11), y + h / 2, glyph, on ? 0xFFFFFFFF : Theme.MUTED);
        text(g, x + u(21), y + h / 2, label, on ? 0xFFFFFFFF : Theme.MUTED, false);
        hits.add(x, y, w, h, b -> toggle.run());
        return x + w + u(4);
    }

    private static void button(GuiGraphicsExtractor g, int x, int y, int s, String glyph, int on, HitSink hits, Runnable action) {
        if (on != 0) glassTint(g, x, y, s, s, s / 2, on, .7f);
        else glass(g, x, y, s, s, s / 2, 0x30000000, .5f);
        icon(g, x + s / 2, y + s / 2, glyph, 0xFFFFFFFF);
        hits.add(x, y, s, s, b -> action.run());
    }

    private static void card(GuiGraphicsExtractor g, Marker m, int mx, int my) {
        int w = u(10);
        w = Math.max(w, textWidth(m.title, true));
        for (String l : m.lines) if (!l.isBlank()) w = Math.max(w, Math.min(u(260), textWidth(l, false)));
        w += u(24);
        int lines = 1;
        for (String l : m.lines) if (!l.isBlank()) lines++;
        int h = u(14) + lines * u(13);
        int x = Math.min(rx + rw - w - u(4), mx + u(14)), y = Math.max(ry + u(4), my - h - u(8));
        shadow(g, x, y, w, h, u(10), u(8), 0x60);
        glass(g, x, y, w, h, u(10), alpha(0x12141D, 0xEC), 1f);
        fill(g, x + u(8), y + u(9), Math.max(2, S), h - u(18), m.color);
        int ty = y + u(13);
        text(g, x + u(16), ty, m.title, m.color, true);
        for (String l : m.lines) {
            if (l.isBlank()) continue;
            ty += u(13);
            text(g, x + u(16), ty, Hud.fit(l, u(260), false), Theme.SUBTEXT, false);
        }
    }

    // ------------------------------------------------------------------ input
    static boolean contains(int px, int py) { return inside(px, py, rx, ry, rw, rh); }

    /** Zooms toward the cursor, so the point under it stays put. */
    static void scroll(int px, int py, double amount) {
        double wx = centerX + (px - (rx + rw / 2.0)) * zoomTarget, wz = centerZ + (py - (ry + rh / 2.0)) * zoomTarget;
        double next = Math.max(.08, Math.min(96, zoomTarget * (amount > 0 ? 1 / 1.25 : 1.25)));
        if (!follow) {
            centerX = wx - (px - (rx + rw / 2.0)) * next;
            centerZ = wz - (py - (ry + rh / 2.0)) * next;
        }
        zoomTarget = next;
        zoom = next;
    }

    static void drag(double dx, double dy) {
        follow = false;
        centerX -= dx * zoom;
        centerZ -= dy * zoom;
    }

    /** A click (not a drag) on the map: left sets a waypoint at the marker, right copies its coordinates. */
    static void click(int px, int py, int button) {
        Marker m = nearest(px, py, u(9));
        if (m == null || m.kind == Kind.WAYPOINT && button == 0) return;
        String coords = m.x + " " + m.y + " " + m.z;
        if (button == 1) {
            Minecraft.getInstance().keyboardHandler.setClipboard(coords);
            Notifications.success("Intel map", "Copied " + coords);
        } else {
            String name = m.title.replace("  (known)", "").replace("  (new)", "") + " " + m.x + "," + m.z;
            WaypointsModule.INSTANCE.add(name.replace(' ', '_'), m.x, m.y, m.z);
        }
    }

    private static Marker nearest(int px, int py, int radius) {
        if (!contains(px, py)) return null;
        Marker best = null;
        long bd = (long) radius * radius;
        for (Marker m : markers) {
            long d = sq(m.sx - px) + sq(m.sy - py);
            if (d <= bd) { bd = d; best = m; }
        }
        return best;
    }

    // ------------------------------------------------------------------ helpers
    private static int sx(double wx) { return (int) Math.round(rx + rw / 2.0 + (wx - centerX) / zoom); }
    private static int sy(double wz) { return (int) Math.round(ry + rh / 2.0 + (wz - centerZ) / zoom); }
    private static long sq(long v) { return v * v; }

    private static String distance(Minecraft mc, int x, int z) {
        if (mc.player == null) return "";
        return "   " + Math.round(Math.sqrt(sq(Math.round(mc.player.getX()) - x) + sq(Math.round(mc.player.getZ()) - z))) + "m";
    }
}
