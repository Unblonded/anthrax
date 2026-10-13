package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import unblonded.anthrax.modules.visual.BlockHighlightModule;
import unblonded.anthrax.modules.visual.DamageNumbersModule;
import unblonded.anthrax.modules.visual.MapModule;
import unblonded.anthrax.modules.visual.MusicModule;
import unblonded.anthrax.modules.world.WaypointsModule;
import unblonded.anthrax.render.WorldProjection;

import static unblonded.anthrax.gui.Draw.*;

/** HUD pieces for the render modules: damage numbers, block info card, now playing, lyrics and the minimap. */
final class HudExtras {
    private HudExtras() {}

    // ------------------------------------------------------------------ damage numbers
    static void damageNumbers(GuiGraphicsExtractor g, int pw, int ph) {
        DamageNumbersModule m = DamageNumbersModule.INSTANCE;
        if (!m.isEnabled() || m.popups().isEmpty()) return;
        long now = System.currentTimeMillis();
        for (var p : m.popups()) {
            float t = (float) ((now - p.born) / m.lifetimeMs());
            if (t >= 1) continue;
            var at = p.pos.add(p.drift * t * 0.6, t * 0.9, 0);
            float[] s = WorldProjection.project(at, pw, ph);
            if (s == null) continue;
            float amount = m.hearts() ? p.amount / 2 : p.amount;
            String txt = (p.heal ? "+" : "") + (amount >= 10 ? String.valueOf(Math.round(amount)) : String.format("%.1f", amount));
            int color = p.heal ? 0xFF55FF88 : amount >= 8 ? 0xFFFF3B5C : amount >= 4 ? 0xFFFF9A3C : 0xFFFFE15C;
            int saved = Draw.S;
            if (m.scaled()) Draw.S = Math.min(4, saved + (amount >= 8 ? 2 : amount >= 4 ? 1 : 0));
            // pop in, then fade out over the last third
            int alpha = t < 0.66f ? 255 : Math.round(255 * (1 - (t - 0.66f) / 0.34f));
            if (alpha > 20) textC(g, Math.round(s[0]), Math.round(s[1]), txt, alpha(color, alpha), true);
            Draw.S = saved;
        }
    }

    // ------------------------------------------------------------------ block info card
    static void blockCard(GuiGraphicsExtractor g, Minecraft mc, int pw, int ph) {
        if (!BlockHighlightModule.INSTANCE.showCard() || mc.gui.screen() != null) return;
        BlockPos pos = BlockHighlightModule.target(mc);
        if (pos == null) return;
        var state = mc.level.getBlockState(pos);
        String name = state.getBlock().getName().getString();
        float hardness = state.getDestroySpeed(mc.level, pos);
        String tool = state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) ? "Pickaxe"
                : state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE) ? "Axe"
                : state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL) ? "Shovel"
                : state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE) ? "Hoe" : "Hand";
        String sub = pos.getX() + " " + pos.getY() + " " + pos.getZ() + "   " + (hardness < 0 ? "Unbreakable" : "Hardness " + trim(hardness) + "   " + tool);
        int w = Math.max(textWidth(name, true), textWidth(sub, false)) + u(28), h = u(40);
        int x = pw / 2 - w / 2, y = ph / 2 + u(22);
        glass(g, x, y, w, h, u(12), alpha(0x0E1119, 0xC8), 0.8f);
        round(g, x + u(10), y + u(10), u(4), h - u(20), u(2), 0xFF000000 | BlockHighlightModule.INSTANCE.rgb());
        text(g, x + u(20), y + u(14), name, Theme.TEXT, true);
        text(g, x + u(20), y + u(28), sub, Theme.MUTED, false);
    }

    private static String trim(float v) { return v == Math.rint(v) ? String.valueOf((int) v) : String.format("%.1f", v); }

    // ------------------------------------------------------------------ music
    static int[] musicCard(GuiGraphicsExtractor g, int x, int y, int accent) {
        MusicModule m = MusicModule.INSTANCE;
        int w = u(220);
        boolean track = m.hasTrack();
        String lyric = track && m.showCardLyric() ? m.lyric() : "";
        int h = track ? u(lyric.isBlank() ? 46 : 62) : u(28);
        glass(g, x, y, w, h, u(12), alpha(0x0E1119, 0xC8), 0.8f);
        icon(g, x + u(16), y + u(15), m.playing() ? Icons.MUSIC : Icons.PAUSE, accent);
        if (!track) { text(g, x + u(30), y + u(14), m.status().isBlank() ? "Waiting for media" : m.status(), Theme.SUBTEXT, false); return new int[]{w, h}; }
        text(g, x + u(30), y + u(14), Hud.fit(m.title(), w - u(40), true), Theme.TEXT, true);
        text(g, x + u(30), y + u(27), Hud.fit(m.artist(), w - u(40), false), Theme.MUTED, false);
        if (m.duration() > 0) {
            int bx = x + u(12), bw = w - u(24), by = y + u(37);
            round(g, bx, by, bw, u(3), u(2), 0x30FFFFFF);
            int fw = Math.round(bw * Math.min(1f, m.position() / (float) m.duration()));
            if (fw > 0) round(g, bx, by, Math.max(u(3), fw), u(3), u(2), accent);
        }
        if (!lyric.isBlank()) text(g, x + u(12), y + u(52), Hud.fit(lyric, w - u(24), false), Theme.SUBTEXT, false);
        return new int[]{w, h};
    }

    static void crosshairLyric(GuiGraphicsExtractor g, Minecraft mc, int pw, int ph) {
        MusicModule m = MusicModule.INSTANCE;
        if (!m.showCrosshairLyric() || mc.gui.screen() != null) return;
        String l = m.lyric();
        if (l.isBlank()) return;
        int w = textWidth(l, false) + u(20), h = u(20), y = ph / 2 + u(BlockHighlightModule.INSTANCE.showCard() ? 70 : 24);
        round(g, pw / 2 - w / 2, y, w, h, h / 2, 0x900A0C12);
        textC(g, pw / 2, y + h / 2, l, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------ minimap
    static int[] minimap(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int accent) {
        MapModule m = MapModule.INSTANCE;
        var p = mc.player;
        int size = u(120);
        double zoom = m.minimapZoom() / Math.max(1, Draw.S / 2.0);
        glass(g, x, y, size, size, u(12), 0xE0080A10, 0.8f);
        int inset = u(4), inner = size - 2 * inset;
        g.enableScissor(x + inset, y + inset, x + inset + inner, y + inset + inner);
        double minX = p.getX() - inner / 2.0 * zoom, minZ = p.getZ() - inner / 2.0 * zoom;
        for (MapModule.Region r : m.regionsIn(minX, minZ, minX + inner * zoom, minZ + inner * zoom)) {
            int sx = x + inset + (int) Math.floor((r.rx * MapModule.REGION - minX) / zoom);
            int sy = y + inset + (int) Math.floor((r.rz * MapModule.REGION - minZ) / zoom);
            int s = (int) Math.ceil(MapModule.REGION / zoom);
            g.blit(RenderPipelines.GUI_TEXTURED, r.id, sx, sy, 0f, 0f, s, s, MapModule.REGION, MapModule.REGION, MapModule.REGION, MapModule.REGION);
        }
        for (var wp : WaypointsModule.INSTANCE.here()) {
            int wx = x + inset + (int) ((wp.x - minX) / zoom), wz = y + inset + (int) ((wp.z - minZ) / zoom);
            circle(g, wx, wz, u(3), 0xFF000000 | wp.color);
        }
        g.disableScissor();
        int cx = x + size / 2, cy = y + size / 2;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((float) Math.toRadians(p.getYRot() + 180 - 45));
        icon(g, 0, 0, Icons.LOCATION_ARROW, 0xFFFFFFFF);
        g.pose().popMatrix();
        textC(g, cx, y + u(10), "N", alpha(accent, 0xE0), true);
        return new int[]{size, size};
    }
}
