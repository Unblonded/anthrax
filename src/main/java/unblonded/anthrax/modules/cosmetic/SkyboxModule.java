package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

/**
 * Replaces the overworld sky with a preset: sky and horizon colours, fog, stars and an optional extra star field
 * with twinkling. Hooks: SkyboxMixin (sky state, sun and moon), FogRenderMixin (fog) and WorldGizmoMixin (clear colour).
 */
public final class SkyboxModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final SkyboxModule INSTANCE = new SkyboxModule();
    private final Setting.Mode preset = setting(Setting.Mode.standalone("Sky", "Sunset", "Sunset", "Midnight", "Deep space", "Nebula", "Arctic", "Blood moon", "Pastel", "Custom"),
            "Deep space and Nebula keep the stars out all day.");
    private final Setting.Num hue = setting(Setting.Num.standalone("Custom hue", 210, 0, 360, 1), "Sky colour for Custom.");
    private final Setting.Bool fog = setting(Setting.Bool.standalone("Colour the fog", true), "Blend the distant fog into the sky.");
    private final Setting.Bool hideSun = setting(Setting.Bool.standalone("Hide sun and moon", false), "");
    private final Setting.Bool extraStars = setting(Setting.Bool.standalone("Extra stars", true), "A denser, twinkling star field for the night-style skies.");
    private final Setting.Num starCount = setting(Setting.Num.standalone("Star count", 400, 50, 1500, 50), "");

    private SkyboxModule() { super("Skybox", "Swap the sky for a sunset, deep space, nebula, arctic or blood-moon look"); }

    private record Look(int sky, int horizon, int fog, float stars) {}

    private Look look() {
        return switch (preset.get()) {
            case "Midnight" -> new Look(0x0A1030, 0x1E2A5A, 0x141C40, 1f);
            case "Deep space" -> new Look(0x02020A, 0x0B0B1E, 0x05050F, 1f);
            case "Nebula" -> new Look(0x1A0833, 0x7A2B8F, 0x2A1045, 1f);
            case "Arctic" -> new Look(0x8FD8FF, 0xE8F8FF, 0xCDEBFA, 0.2f);
            case "Blood moon" -> new Look(0x2A0408, 0x8A1620, 0x3A080C, 0.6f);
            case "Pastel" -> new Look(0xF5B8E0, 0xBFE3FF, 0xF3D4EC, 0f);
            case "Custom" -> new Look(hsl((float) hue.get(), 0.7f, 0.45f), hsl((float) hue.get() + 30, 0.75f, 0.65f), hsl((float) hue.get(), 0.6f, 0.5f), 0.4f);
            default -> new Look(0x3A2A6B, 0xFF8A4C, 0xE07A5F, 0.15f);   // Sunset
        };
    }

    private boolean overworld(Minecraft mc) { return mc.level != null && mc.level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD; }

    /** SkyRenderer.extractRenderState TAIL. */
    public void sky(SkyRenderState s) {
        if (!isEnabled() || s.skybox != DimensionType.Skybox.OVERWORLD) return;
        Look l = look();
        s.skyColor = 0xFF000000 | l.sky;
        s.sunriseAndSunsetColor = (0xB0 << 24) | l.horizon;
        s.starBrightness = Math.max(s.starBrightness, l.stars);
    }

    public boolean hideSun() { return isEnabled() && hideSun.get(); }

    /** The clear colour behind everything. */
    public void tintSky(Vector4f c) {
        if (!isEnabled() || c == null || !overworld(Minecraft.getInstance())) return;
        int rgb = look().sky;
        c.set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, c.w);
    }

    public void tintFog(FogData f) {
        if (!isEnabled() || !fog.get() || f == null || f.color == null || !overworld(Minecraft.getInstance())) return;
        int rgb = look().fog;
        f.color.set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, f.color.w);
    }

    @Override public boolean active() { return isEnabled() && extraStars.get() && look().stars >= 0.6f && overworld(Minecraft.getInstance()); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        int n = (int) starCount.get();
        double t = System.currentTimeMillis() / 1000.0;
        java.util.Random r = new java.util.Random(0xA17A5L);
        double radius = Math.min(150, mc.options.getEffectiveRenderDistance() * 16 * 0.9);
        for (int i = 0; i < n; i++) {
            // even spread over the upper sky
            double y = 0.05 + r.nextDouble() * 0.95, a = r.nextDouble() * Math.PI * 2, h = Math.sqrt(1 - y * y);
            double tw = 0.55 + 0.45 * Math.sin(t * (1 + r.nextDouble() * 3) + i);
            float size = 1.5f + r.nextFloat() * 2.5f;
            int rgb = r.nextInt(5) == 0 ? 0xBFD4FF : r.nextInt(7) == 0 ? 0xFFE2B0 : 0xFFFFFF;
            Gizmos.point(eye.add(Math.cos(a) * h * radius, y * radius, Math.sin(a) * h * radius), WorldOverlays.argb((int) (tw * 230), rgb), size);
        }
    }
}
