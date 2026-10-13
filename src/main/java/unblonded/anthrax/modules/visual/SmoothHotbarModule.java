package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** The hotbar selection slides to the new slot instead of jumping (see SmoothHotbarMixin). */
public final class SmoothHotbarModule extends FeatureModule {
    public static final SmoothHotbarModule INSTANCE = new SmoothHotbarModule();
    private final Setting.Num speed = setting(Setting.Num.standalone("Speed", 18, 4, 40, 1), "How fast the selection slides.");
    private float shown = -1;
    private long last = System.nanoTime();

    private SmoothHotbarModule() { super("Smooth Hotbar", "The hotbar selection slides to the new slot instead of jumping", "Visuals", false); }

    /** Animated slot position (fractional), advanced once per drawn frame. */
    public float slot(int selected) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        if (shown < 0 || Math.abs(shown - selected) > 8.5f) shown = selected;
        shown += (selected - shown) * Math.min(1f, dt * (float) (double) speed.get());
        if (Math.abs(selected - shown) < 0.01f) shown = selected;
        return shown;
    }
}
