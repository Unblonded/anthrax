package unblonded.anthrax.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.modules.visual.FontSizeModule;
import unblonded.anthrax.modules.visual.MapModule;
import unblonded.anthrax.modules.world.WaypointsModule;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Full-screen explored map: drag to pan, scroll to zoom, right-click to drop a waypoint, C to recenter on you.
 * Draws the recorded terrain regions, your path, waypoints and you.
 */
public final class MapScreen extends Screen {
    private static double centerX = Double.NaN, centerZ, zoom = 1;   // zoom: blocks per screen pixel
    private static boolean follow = true;
    private float gs = 1;
    private int mx, my, pw, ph;
    private boolean dragging;
    private double dragX, dragY;

    public MapScreen() { super(Component.literal("Map")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (Draw.FX > 0) extractBlurredBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Draw.measureFont = font;
        gs = (float) minecraft.getWindow().getGuiScale();
        pw = Math.round(width * gs);
        ph = Math.round(height * gs);
        mx = Math.round(mouseX * gs);
        my = Math.round(mouseY * gs);
        Theme.tick(System.currentTimeMillis());
        int savedS = Draw.S;
        Draw.S = FontSizeModule.apply(Math.min(Math.round(ph / 720f), Math.min(pw / 600, ph / 400)));
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try { draw(g); } finally { g.pose().popMatrix(); Draw.S = savedS; }
    }

    private void draw(GuiGraphicsExtractor g) {
        var p = minecraft.player;
        if (p == null) return;
        if (follow || Double.isNaN(centerX)) { centerX = p.getX(); centerZ = p.getZ(); }
        int accent = Theme.accent();
        int pad = u(SP_L), top = u(52);
        int x0 = pad, y0 = top, w = pw - 2 * pad, h = ph - top - pad;
        canvas(g, u(SP_S), u(SP_S), pw - 2 * u(SP_S), ph - 2 * u(SP_S), u(R_WINDOW));
        text(g, pad + u(4), u(30), "Map", Theme.TEXT, true);
        String info = Math.round(p.getX()) + "  " + Math.round(p.getY()) + "  " + Math.round(p.getZ());
        text(g, pad + u(44), u(30), info, Theme.MUTED, false);
        textR(g, pw - pad - u(4), u(30), "Drag to pan   Scroll to zoom   Right-click: waypoint   C: center   Esc: close", Theme.DIM, false);

        round(g, x0, y0, w, h, u(R_CARD), 0xE0080A10);
        g.enableScissor(x0, y0, x0 + w, y0 + h);
        double minX = centerX - w / 2.0 * zoom, minZ = centerZ - h / 2.0 * zoom;
        for (MapModule.Region r : MapModule.INSTANCE.regionsIn(minX, minZ, minX + w * zoom, minZ + h * zoom)) {
            int sx = x0 + (int) Math.floor((r.rx * MapModule.REGION - minX) / zoom);
            int sy = y0 + (int) Math.floor((r.rz * MapModule.REGION - minZ) / zoom);
            int size = (int) Math.ceil(MapModule.REGION / zoom);
            g.blit(RenderPipelines.GUI_TEXTURED, r.id, sx, sy, 0f, 0f, size, size, MapModule.REGION, MapModule.REGION, MapModule.REGION, MapModule.REGION);
        }
        // path
        BlockPos prev = null;
        for (BlockPos b : MapModule.INSTANCE.trail()) {
            if (prev != null && prev.distSqr(b) < 64 * 64) {
                int ax = x0 + (int) ((prev.getX() - minX) / zoom), az = y0 + (int) ((prev.getZ() - minZ) / zoom);
                int bx = x0 + (int) ((b.getX() - minX) / zoom), bz = y0 + (int) ((b.getZ() - minZ) / zoom);
                line(g, ax, az, bx, bz, (float) Math.hypot(bx - ax, bz - az), Math.max(1, S), alpha(accent, 0xA0));
            }
            prev = b;
        }
        // waypoints
        for (var wp : WaypointsModule.INSTANCE.here()) {
            int wx = x0 + (int) ((wp.x - minX) / zoom), wz = y0 + (int) ((wp.z - minZ) / zoom);
            circle(g, wx, wz, u(4), 0xFF000000 | wp.color);
            text(g, wx + u(7), wz, wp.name, 0xFFFFFFFF, false);
        }
        // you
        int px = x0 + (int) ((p.getX() - minX) / zoom), pz = y0 + (int) ((p.getZ() - minZ) / zoom);
        g.pose().pushMatrix();
        g.pose().translate(px, pz);
        g.pose().rotate((float) Math.toRadians(p.getYRot() + 180 - 45));
        icon(g, 0, 0, Icons.LOCATION_ARROW, 0xFFFFFFFF);
        g.pose().popMatrix();
        g.disableScissor();
        // cursor coordinates
        if (inside(mx, my, x0, y0, w, h)) {
            int bx = (int) Math.floor(minX + (mx - x0) * zoom), bz = (int) Math.floor(minZ + (my - y0) * zoom);
            String c = bx + ", " + bz;
            int cw = textWidth(c, false) + u(16);
            round(g, x0 + u(8), y0 + h - u(30), cw, u(22), u(11), 0xC0101218);
            text(g, x0 + u(16), y0 + h - u(19), c, Theme.SUBTEXT, false);
        }
        textR(g, x0 + w - u(10), y0 + h - u(19), String.format("%.2f blocks/px", zoom), Theme.DIM, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && minecraft.player != null) {
            double minX = centerX - (pw - 2 * u(SP_L)) / 2.0 * zoom, minZ = centerZ - (ph - u(52) - u(SP_L)) / 2.0 * zoom;
            int bx = (int) Math.floor(minX + (event.x() * gs - u(SP_L)) * zoom), bz = (int) Math.floor(minZ + (event.y() * gs - u(52)) * zoom);
            int y = minecraft.level == null ? 64 : minecraft.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bx, bz);
            WaypointsModule.INSTANCE.add("Map " + bx + " " + bz, bx, y, bz);
            Notifications.success("Map", "Waypoint added at " + bx + " " + bz);
            return true;
        }
        if (event.button() == 0) { dragging = true; dragX = event.x(); dragY = event.y(); follow = false; return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return super.mouseDragged(event, dx, dy);
        centerX -= dx * gs * zoom;
        centerZ -= dy * gs * zoom;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) { dragging = false; return super.mouseReleased(event); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        zoom = Math.max(0.125, Math.min(32, zoom * (scrollY > 0 ? 0.8 : 1.25)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_C) { follow = true; return true; }
        if (event.key() == MapModule.INSTANCE.mapKey()) { onClose(); return true; }
        return super.keyPressed(event);
    }
}
