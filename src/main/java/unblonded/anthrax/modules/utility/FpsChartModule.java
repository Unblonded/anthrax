package unblonded.anthrax.modules.utility;

import net.minecraft.client.Minecraft;
import unblonded.anthrax.gui.AnthraxScreen;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Rolling frame-rate graph. Sampling and drawing happen in the HUD (see Hud), once per frame. */
public final class FpsChartModule extends FeatureModule {
    public static final FpsChartModule INSTANCE = new FpsChartModule();
    public static final int SAMPLES = 100;

    private final Setting.Num sampleRate = setting(Setting.Num.standalone("Sample rate (frames)", 5, 1, 100, 1));
    private final Setting.Bool inGame = setting(Setting.Bool.standalone("Show in game", true));
    private final int[] values = new int[SAMPLES];
    private int cursor, frames, filled;

    private FpsChartModule() {
        super("FPS Chart", "Plot the recent frame rate", "Utility", false);
    }

    /** Whether the chart should be drawn this frame. */
    public boolean visible(Minecraft mc) {
        return isEnabled() && mc.player != null && (inGame.get() || mc.gui.screen() instanceof AnthraxScreen);
    }

    /** Called once per rendered frame while visible. */
    public void sample(int fps) {
        if (++frames < (int) sampleRate.get()) return;
        frames = 0;
        values[cursor] = fps;
        cursor = (cursor + 1) % SAMPLES;
        filled = Math.min(SAMPLES, filled + 1);
    }

    /** Sample {@code i} in age order, 0 = oldest. */
    public int value(int i) { return values[(cursor + i) % SAMPLES]; }
    public int filled() { return filled; }

    @Override public String hudInfo() { return Minecraft.getInstance().getFps() + " fps"; }
}
