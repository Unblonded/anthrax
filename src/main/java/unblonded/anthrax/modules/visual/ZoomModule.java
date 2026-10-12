package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import unblonded.anthrax.Anthrax;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * OptiFine-style zoom: hold the zoom key (default C, rebindable in Controls). Smoothly eases in and
 * out, the scroll wheel adjusts the level while zooming, and mouse sensitivity scales with the zoom.
 */
public final class ZoomModule extends FeatureModule {
    public static final ZoomModule INSTANCE = new ZoomModule();

    private final Setting.Num level = setting(Setting.Num.standalone("Zoom level", 4, 1.5, 50, 0.5));
    private final Setting.Bool smooth = setting(Setting.Bool.standalone("Smooth transition", true));
    private final Setting.Bool scroll = setting(Setting.Bool.standalone("Scroll to adjust", true));
    private final Setting.Bool cinematic = setting(Setting.Bool.standalone("Cinematic camera while zoomed", false));
    private float current = 1f, scrollLevel = -1f;
    private long lastNs = System.nanoTime();
    private boolean cinematicApplied;

    private ZoomModule() {
        super("Zoom", "Hold C to zoom; scroll to adjust", "Visuals", true);
    }

    @Override protected boolean hiddenFromList() { return true; }

    public boolean zooming() {
        return isEnabled() && Anthrax.zoomKey != null && Anthrax.zoomKey.isDown() && Minecraft.getInstance().gui.screen() == null;
    }

    /** Called from the FOV hook every frame; returns the divisor applied to the field of view. */
    public float factor() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        boolean z = zooming();
        if (!z) scrollLevel = -1f;
        float target = z ? (scrollLevel > 0 ? scrollLevel : (float) level.get()) : 1f;
        current = smooth.get() ? current + (target - current) * Math.min(1f, dt * 14f) : target;
        if (Math.abs(current - target) < 0.01f) current = target;
        Minecraft mc = Minecraft.getInstance();
        if (cinematic.get() && z != cinematicApplied) { mc.options.smoothCamera = z; cinematicApplied = z; }
        return current;
    }

    /** Mouse turn multiplier so aiming stays precise when zoomed in. */
    public float sensitivity() { return current <= 1.01f ? 1f : 1f / current; }

    /** Returns true if the scroll was used for zooming. */
    public boolean onScroll(double amount) {
        if (!zooming() || !scroll.get()) return false;
        float base = scrollLevel > 0 ? scrollLevel : (float) level.get();
        scrollLevel = (float) Math.max(1.5, Math.min(50, base * (amount > 0 ? 1.25 : 0.8)));
        return true;
    }
}
