package unblonded.anthrax.modules.visual;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import unblonded.anthrax.gui.CrosshairRenderer;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Animated, reactive crosshair in place of the vanilla one (drawing lives in {@link CrosshairRenderer}). */
public final class CosmicCrosshairModule extends FeatureModule {
    public static final CosmicCrosshairModule INSTANCE = new CosmicCrosshairModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Orbit", "Orbit", "Reticle", "Pulse", "Nova", "Minimal"));
    private final Setting.Mode color = setting(Setting.Mode.standalone("Color", "Theme", "Theme", "Rainbow", "White", "Cyan", "Pink", "Lime", "Gold"));
    private final Setting.Num size = setting(Setting.Num.standalone("Crosshair size", 10, 4, 30, 1));
    private final Setting.Num thickness = setting(Setting.Num.standalone("Thickness", 2, 1, 4, .5));
    private final Setting.Num speed = setting(Setting.Num.standalone("Animation speed", 1, 0, 3, .1));
    private final Setting.Num opacity = setting(Setting.Num.standalone("Opacity", 100, 20, 100, 5));
    private final Setting.Bool spread = setting(Setting.Bool.standalone("Spread when moving", true), "The crosshair opens up while you walk, sprint or jump.");
    private final Setting.Bool cooldown = setting(Setting.Bool.standalone("Attack cooldown ring", true));
    private final Setting.Bool hitMarker = setting(Setting.Bool.standalone("Hit marker", true));
    private final Setting.Bool targetLock = setting(Setting.Bool.standalone("Lock onto targets", true), "Tighten and turn red when you aim at a mob or player.");
    private final Setting.Bool outline = setting(Setting.Bool.standalone("Dark outline", true));
    private final Setting.Bool glow = setting(Setting.Bool.standalone("Glow", true));

    private CosmicCrosshairModule() {
        super("Show Cosmic Crosshair", "Animated crosshair that reacts to movement, cooldown, hits and targets", "Visuals", false);
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!isEnabled() || mc.player == null || mc.gui.screen() != null) { vanilla.extractRenderState(graphics, delta); return; }
            if (!mc.options.getCameraType().isFirstPerson()) return;   // vanilla hides it in third person too
            unblonded.anthrax.modules.Safe.run(this, () -> CrosshairRenderer.render(graphics, mc));
        });
    }

    public String style() { return style.get(); }
    public String color() { return color.get(); }
    public float size() { return (float) size.get(); }
    public float thickness() { return (float) thickness.get(); }
    public float speed() { return (float) speed.get(); }
    public float opacity() { return (float) opacity.get() / 100f; }
    public boolean spread() { return spread.get(); }
    public boolean cooldown() { return cooldown.get(); }
    public boolean hitMarker() { return hitMarker.get(); }
    public boolean targetLock() { return targetLock.get(); }
    public boolean outline() { return outline.get(); }
    public boolean glow() { return glow.get(); }
}
