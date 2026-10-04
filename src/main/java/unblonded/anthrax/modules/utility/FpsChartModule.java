package unblonded.anthrax.modules.utility;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import unblonded.anthrax.gui.AnthraxScreen;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Rolling FPS graph with Packet Edit's frame sampling and in-game visibility option. */
public final class FpsChartModule extends FeatureModule {
    private final Setting.Num sampleRate = setting(Setting.Num.standalone("Sample rate (frames)", 5, 1, 100, 1));
    private final Setting.Bool inGame = setting(Setting.Bool.standalone("Show in game", true));
    private final int[] values = new int[100];
    private int cursor, frames;

    public FpsChartModule() {
        super("FPS Chart", "Plot the recent frame rate", "Utility", false);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("anthrax", "fps_chart"), (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!isEnabled() || mc.player == null || (!inGame.get() && !(mc.gui.screen() instanceof AnthraxScreen))) return;
            if (++frames >= (int)sampleRate.get()) { values[cursor++ % values.length] = mc.getFps(); frames = 0; }
            int x = 8, y = 48, w = 102, h = 44;
            graphics.fill(x - 4, y - 4, x + w + 4, y + h + 15, 0xB0101020);
            graphics.textRenderer().accept(x, y - 1, Component.literal("FPS " + mc.getFps()));
            for (int i=0; i<values.length; i++) {
                int fps = values[(cursor + i) % values.length];
                int barHeight = Math.max(1, Math.min(h, fps * h / 300));
                graphics.fill(x + i, y + h - barHeight, x + i + 1, y + h, fps < 60 ? 0xFFFF6655 : 0xFF66DDFF);
            }
        });
    }
}
