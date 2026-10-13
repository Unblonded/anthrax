package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * A short motion blur on fast turns and movement (see MotionBlurMixin). Blends each frame with the previous ones
 * before your hand and the HUD are drawn, so those stay sharp. The per-frame blend is picked from your frame rate,
 * so the trail lasts the same time at 60 or 400 FPS (post effect settings are fixed per file, hence the ladder).
 */
public final class MotionBlurModule extends FeatureModule {
    public static final MotionBlurModule INSTANCE = new MotionBlurModule();
    /** Per-frame blend of each motion_blur_NN effect file. */
    private static final float[] LEVELS = {0.3f, 0.45f, 0.55f, 0.65f, 0.72f, 0.78f, 0.83f, 0.87f, 0.9f, 0.925f, 0.945f, 0.96f, 0.97f, 0.98f, 0.987f};
    private final Setting.Mode strength = setting(Setting.Mode.standalone("Strength", "Medium", "Low", "Medium", "High"), "How long the trail lasts.");
    private int level = -1;
    private long levelChangedAt;

    private MotionBlurModule() { super("Motion Blur", "Adds a short motion blur to fast camera turns and movement. Your hand and the HUD are not blurred", "Visuals", false); }

    public Identifier effect() {
        double at60 = switch (strength.get()) { case "Low" -> 0.45; case "High" -> 0.80; default -> 0.65; };
        int fps = Math.max(20, Minecraft.getInstance().getFps());
        double want = Math.pow(at60, 60.0 / fps);
        int best = 0;
        for (int i = 1; i < LEVELS.length; i++) if (Math.abs(LEVELS[i] - want) < Math.abs(LEVELS[best] - want)) best = i;
        // each effect keeps its own trail buffer, so only switch when the frame rate has really moved
        long now = System.currentTimeMillis();
        if (level < 0 || (Math.abs(best - level) >= 2 && now - levelChangedAt > 1000) || (best != level && now - levelChangedAt > 4000)) {
            level = best;
            levelChangedAt = now;
        }
        return Identifier.fromNamespaceAndPath("anthrax", String.format("motion_blur_%02d", level));
    }
}
