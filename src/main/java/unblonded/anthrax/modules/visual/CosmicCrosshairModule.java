package unblonded.anthrax.modules.visual;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Animated cosmic rings/rays in place of the vanilla crosshair while enabled. */
public final class CosmicCrosshairModule extends FeatureModule {
    public static final CosmicCrosshairModule INSTANCE = new CosmicCrosshairModule();
    private final Setting.Num size = setting(Setting.Num.standalone("Size", 20, 1, 100, 1));
    private final Setting.Bool lines = setting(Setting.Bool.standalone("Show crosshair lines", false));
    private CosmicCrosshairModule() {
        super("Show Cosmic Crosshair", "Replace the vanilla crosshair with animated rings", "Visuals", false);
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!isEnabled() || mc.player == null || mc.gui.screen() != null) { vanilla.extractRenderState(graphics, delta); return; }
            draw(graphics, mc);
        });
    }

    private void draw(net.minecraft.client.gui.GuiGraphicsExtractor g, Minecraft mc) {
        float t = System.currentTimeMillis() / 1000f, radius = (float)size.get();
        int cx = mc.getWindow().getGuiScaledWidth()/2, cy = mc.getWindow().getGuiScaledHeight()/2;
        for (int ring=0; ring<5; ring++) {
            float rr = radius * (.4f + ring*.15f), pulse = .7f + .3f*(float)Math.sin(t*3+ring);
            for (int i=0; i<36; i++) {
                float a = t*1.2f + (float)(Math.PI*2*i/36);
                float wave = 1 + .15f*(float)Math.sin(a*6+t*4);
                int x = cx + Math.round((float)Math.cos(a)*rr*wave), y = cy + Math.round((float)Math.sin(a)*rr*wave);
                int color = hsv(.28f + .3f*(float)(Math.sin(t+ring*.5)*.5+.5), .9f, pulse);
                g.fill(x-1, y-1, x+2, y+2, color);
            }
        }
        for (int i=0; i<8; i++) {
            float a = t*.6f + (float)(Math.PI*2*i/8), r = radius*1.5f*(.5f+.5f*(float)Math.sin(t*2+i));
            int x2 = cx + Math.round((float)Math.cos(a)*r), y2 = cy + Math.round((float)Math.sin(a)*r);
            line(g, cx, cy, x2, y2, hsv(.3f+.1f*(float)Math.sin(t*2+i), .9f, .9f));
        }
        if (lines.get()) {
            int len = Math.round(radius*.7f), c = 0xF2FFFFFF;
            g.fill(cx-len, cy-1, cx+len, cy+1, c); g.fill(cx-1, cy-len, cx+1, cy+len, c);
        }
    }

    private static int hsv(float hue, float sat, float val) { return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF); }
    private static void line(net.minecraft.client.gui.GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int dx=x2-x1, dy=y2-y1, steps=Math.max(Math.abs(dx),Math.abs(dy));
        for (int i=0; i<=steps; i++) { int x=x1+(steps==0?0:dx*i/steps), y=y1+(steps==0?0:dy*i/steps); g.fill(x,y,x+2,y+2,color); }
    }
}
