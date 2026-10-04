package unblonded.anthrax.modules.visual;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Atmospheric client-side palette and lightweight ambient world particles. */
public final class WorldThemeModule extends FeatureModule {
    public static final WorldThemeModule INSTANCE = new WorldThemeModule();

    private final Setting.Mode theme = setting(Setting.Mode.standalone(
            "World theme", "Cyberpunk Neon", "Cyberpunk Neon", "Void / Eclipse"));
    private final Setting.Mode grade = setting(Setting.Mode.standalone(
            "Color grade", "Theme", "Theme", "Neon", "Noir", "Sepia"));
    private final Setting.Bool screenGrade = setting(Setting.Bool.standalone("Screen color grade", true));
    private final Setting.Bool skyFog = setting(Setting.Bool.standalone("Tint sky and fog", true));
    private final Setting.Bool particles = setting(Setting.Bool.standalone("Ambient particles", true));
    private final Setting.Mode particleStyle = setting(Setting.Mode.standalone(
            "Particle style", "Fireflies", "Fireflies", "Embers", "Snow"));
    private final Setting.Num density = setting(Setting.Num.standalone("Particle density", 24, 4, 48, 1));
    private final Setting.Num strength = setting(Setting.Num.standalone("Effect strength", 38, 0, 100, 1));

    // settings
    private final Setting.Bool lightning = setting(Setting.Bool.standalone("Lightning", true));
    private final Setting.Bool targetGlow = setting(Setting.Bool.standalone("Target outline glow", true));

    private final Setting.Mode colorMode = setting(Setting.Mode.standalone(
            "Color mode", "Theme", "Theme", "Spectrum", "Aurora"));
    private final Setting.Num darkness = setting(Setting.Num.standalone("Darkness", 68, 0, 100, 1));
    private final Setting.Bool vignette = setting(Setting.Bool.standalone("Vignette", true));
    private final Setting.Bool shards = setting(Setting.Bool.standalone("Floating shards", true));
    private final Setting.Bool constellation = setting(Setting.Bool.standalone("Constellation", true));


    // theme accents (no alpha, used by WorldFx)
    private int accent(boolean primary) {
        String mode = colorMode.get();
        double t = System.currentTimeMillis() * .001;
        if (mode.equals("Spectrum")) return 0xFF000000 | WorldFxExtra.hsl(t * 45 + (primary ? 0 : 150), .9f, .6f);
        if (mode.equals("Aurora")) return 0xFF000000 | WorldFxExtra.hsl(150 + 40 * Math.sin(t * .4) + (primary ? 0 : 110), .85f, .6f);
        return theme.get().equals("Void / Eclipse")
                ? (primary ? 0xFF9A73FF : 0xFF3BE4FF)
                : (primary ? 0xFF4AF5FF : 0xFFFF4DCE);
    }

    private static int scale(int rgb, float f) {
        return (Math.round(((rgb >> 16) & 255) * f) << 16) | (Math.round(((rgb >> 8) & 255) * f) << 8) | Math.round((rgb & 255) * f);
    }

    private WorldThemeModule() {
        super("World Themes", "Cyberpunk and eclipse palettes with ambient particles", "Visuals", false);
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("anthrax", "world_theme_grade"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (!isEnabled() || client.player == null || client.gui.screen() != null) return;
            int w = client.getWindow().getGuiScaledWidth(), h = client.getWindow().getGuiScaledHeight();
            float str = (float) strength.get() / 100f;
            float dark = (float) darkness.get() / 100f;

            if (screenGrade.get()) {
                int[] colors = palette();
                int alpha = Math.round(90 * str);
                graphics.fillGradient(0, 0, w, h, (alpha << 24) | (colors[0] & 0xFFFFFF), (alpha << 24) | (colors[1] & 0xFFFFFF));
            }
            if (dark > 0) graphics.fill(0, 0, w, h, ((int) (105 * dark) << 24));
            if (vignette.get()) {
                int steps = 14, th = Math.max(1, Math.max(w, h) / 6 / steps);
                int rgb = scale(accent(false), .22f) & 0xFFFFFF;
                for (int i = 0; i < steps; i++) {
                    float k = 1f - i / (float) steps;
                    int col = ((int) (150 * k * k * (.5f + .5f * dark)) << 24) | rgb;
                    graphics.fill(0, i * th, w, (i + 1) * th, col);
                    graphics.fill(0, h - (i + 1) * th, w, h - i * th, col);
                    graphics.fill(i * th, 0, (i + 1) * th, h, col);
                    graphics.fill(w - (i + 1) * th, 0, w - i * th, h, col);
                }
            }
        });
    }

    public static void tintSky(Vector4f skyColor) {
        WorldThemeModule module = INSTANCE;
        if (!module.isEnabled() || !module.skyFog.get() || skyColor == null) return;
        float blend = .34f * (float) module.strength.get() / 100f;
        float[] tint = rgb(module.palette()[0]);
        float d = 1f - .82f * (float) module.darkness.get() / 100f;
        skyColor.set(mix(skyColor.x, tint[0], blend) * d, mix(skyColor.y, tint[1], blend) * d,
                mix(skyColor.z, tint[2], blend) * d, skyColor.w);
    }

    public static void tintFog(FogData fog, FogType fogType) {
        WorldThemeModule module = INSTANCE;
        if (!module.isEnabled() || !module.skyFog.get() || fog == null || fog.color == null
                || fogType != FogType.NONE || NoRenderModule.isHidden(3)) return;
        float blend = .58f * (float) module.strength.get() / 100f;
        float[] tint = rgb(module.palette()[1]);
        float d = 1f - .82f * (float) module.darkness.get() / 100f;
        fog.color.set(mix(fog.color.x, tint[0], blend) * d, mix(fog.color.y, tint[1], blend) * d,
                mix(fog.color.z, tint[2], blend) * d, fog.color.w);
    }

    public static void renderParticles(LevelRenderer renderer) {
        WorldThemeModule module = INSTANCE;
        Minecraft client = Minecraft.getInstance();
        if (!module.isEnabled() || client.player == null || client.level == null
                || client.gui.screen() != null) return;

        String style = module.particleStyle.get();
        boolean snow = style.equals("Snow"), embers = style.equals("Embers");
        double dir = snow ? -1 : 1;
        double vy = snow ? 0.9 : embers ? 1.2 : 0.05;
        float size = snow ? 2.6f : 2.0f;
        final double R = 12, H = 10;
        int count = module.particles.get() ? (int) module.density.get() : 0;
        double t = System.currentTimeMillis() * .001;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 p = client.player.getPosition(partialTick);
        int colorA = module.particleColor(true), colorB = module.particleColor(false);

        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            // ---- particles
            for (int i = 0; i < count; i++) {
                double h1 = hash(i * 3), h2 = hash(i * 3 + 1), h3 = hash(i * 3 + 2);

                double dx = wrap(h1 * 2 * R + Math.sin(t * .7 + h3 * 6.283) * .4 - p.x, 2 * R) - R;
                double dz = wrap(h2 * 2 * R + Math.cos(t * .6 + h1 * 6.283) * .4 - p.z, 2 * R) - R;
                double yb = h3 * H + dir * vy * t + Math.sin(t * 1.3 + h2 * 6.283) * .25;
                double dy = wrap(yb - p.y, H) - H / 2;

                double d2 = (dx * dx + dz * dz) / (R * R);
                double v = dy / (H / 2);
                float fade = (float) (Math.max(0, 1 - d2) * Math.max(0, 1 - v * v));

                int base = (i & 1) == 0 ? colorA : colorB;
                int a = (int) ((base >>> 24) * fade);
                if (a < 4) continue;
                Gizmos.point(new Vec3(p.x + dx, p.y + dy, p.z + dz), (a << 24) | (base & 0xFFFFFF), size);
            }

            // ---- world fx (grid wave, rings, lightning, target glow)
            WorldFx.draw(client, module.accent(true), module.accent(false),
                    (float) module.strength.get() / 100f,
                    false, module.lightning.get(), module.targetGlow.get());

            int mask = (module.shards.get() ? WorldFxExtra.SHARDS : 0)
                    | (module.constellation.get() ? WorldFxExtra.CONSTELLATION : 0);
            WorldFxExtra.draw(client, module.accent(true), module.accent(false),
                    (float) module.strength.get() / 100f, !module.colorMode.get().equals("Theme"), mask);
        }
        renderer.addMainThreadGizmos(collector.drainGizmos());
    }

    private static double wrap(double v, double period) {
        double m = v % period;
        return m < 0 ? m + period : m;
    }

    private static double hash(int n) {
        long x = (long) n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29; x *= 0xBF58476D1CE4E5B9L; x ^= x >>> 32;
        return (x & 0xFFFFFF) / (double) 0x1000000;
    }

    private int particleColor(boolean primary) {
        if (particleStyle.get().equals("Snow")) return primary ? 0xDDEBFFFF : 0xAAB7E9FF;
        if (particleStyle.get().equals("Embers")) return primary ? 0xFFFF9A45 : 0xFFFF4A83;
        return (accent(primary) & 0xFFFFFF) | 0xE6000000;
    }

    private int[] palette() {
        return switch (grade.get()) {
            case "Neon" -> new int[]{0x19134A, 0x073F48};
            case "Noir" -> new int[]{0x171923, 0x08090E};
            case "Sepia" -> new int[]{0x4A3422, 0x725337};
            default -> {
                if (!colorMode.get().equals("Theme")) yield new int[]{scale(accent(true), .3f), scale(accent(false), .26f)};
                yield theme.get().equals("Void / Eclipse")
                        ? new int[]{0x100A2B, 0x291044}
                        : new int[]{0x17133D, 0x0B3441};
            }
        };
    }

    private static float[] rgb(int color) {
        return new float[]{((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f};
    }

    private static float mix(float from, float to, float amount) { return from + (to - from) * amount; }
}
