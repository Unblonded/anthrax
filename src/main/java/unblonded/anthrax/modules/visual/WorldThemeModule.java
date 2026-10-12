package unblonded.anthrax.modules.visual;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

import java.util.List;

/**
 * Client-side world themes: each preset sets a palette, sky and fog tint, screen grade, ambient particles
 * and a set of world effects. The "+" toggles add any effect on top of any theme.
 */
public final class WorldThemeModule extends FeatureModule {
    /**
     * One theme. a/b are the accent colors, top/bottom the screen grade, sky/fog the tints; dark is how much
     * of the Darkness setting applies. extra/themed/glass are effect masks for WorldFxExtra, WorldFxThemes
     * and WorldGlass.
     */
    private record Preset(String name, int a, int b, int top, int bottom, int sky, int fog, float dark, String particles,
                          int extra, int themed, int glass, boolean grid, boolean lightning) {}

    private static final List<Preset> PRESETS = List.of(
            new Preset("Liquid Glass", 0x8FE9FF, 0xC9B2FF, 0x7CC4E6, 0xB4C9F2, 0x7CC4E6, 0xB4C9F2, 0, "Bubbles",
                    0, 0, WorldGlass.CAUSTICS | WorldGlass.BUBBLES | WorldGlass.RIPPLES, false, false),
            new Preset("Cyberpunk Neon", 0x4AF5FF, 0xFF4DCE, 0x17133D, 0x0B3441, 0x17133D, 0x0B3441, 1, "Data",
                    WorldFxExtra.PILLARS, WorldFxThemes.SKYLINE, 0, true, true),
            new Preset("Void / Eclipse", 0x9A73FF, 0x3BE4FF, 0x100A2B, 0x291044, 0x100A2B, 0x291044, 1, "Ash",
                    WorldFxExtra.RIFTS | WorldFxExtra.NEBULA | WorldFxExtra.CONSTELLATION | WorldFxExtra.STARDUST,
                    WorldFxThemes.ECLIPSE | WorldFxThemes.WISPS, 0, false, false),
            new Preset("Synthwave", 0xFF4FD8, 0x45E3FF, 0x3A0F4F, 0x5A1A3A, 0x4A1460, 0x7A2A5A, .6f, "Fireflies",
                    WorldFxExtra.COMETS, WorldFxThemes.SUN, 0, true, false),
            new Preset("Aurora Borealis", 0x5CFFB0, 0x7A8CFF, 0x0B2A33, 0x101538, 0x0E2A3A, 0x13203F, .8f, "Snow",
                    WorldFxExtra.AURORA | WorldFxExtra.STARDUST | WorldFxExtra.CONSTELLATION, 0, 0, false, false),
            new Preset("Arcane", 0xC77DFF, 0xFFC94A, 0x2A1240, 0x3A2A10, 0x2A1446, 0x241032, .8f, "Fireflies",
                    WorldFxExtra.RUNES | WorldFxExtra.ORBITALS | WorldFxExtra.SHARDS, WorldFxThemes.WISPS, 0, false, false),
            new Preset("Digital Rain", 0x3CFF7A, 0x0FAF4A, 0x03140A, 0x021006, 0x021A0C, 0x03200F, 1, "Data",
                    WorldFxExtra.RADAR, WorldFxThemes.RAIN, 0, false, false),
            new Preset("Abyss", 0x2FD3FF, 0x7A5CFF, 0x021A33, 0x000814, 0x03203D, 0x021627, 1, "Bubbles",
                    0, WorldFxThemes.JELLY, WorldGlass.CAUSTICS | WorldGlass.BUBBLES, false, false),
            new Preset("Inferno", 0xFF6A2A, 0xFFC14A, 0x3A0A02, 0x1A0400, 0x4A1205, 0x3A0D04, .7f, "Embers",
                    WorldFxExtra.PILLARS, WorldFxThemes.METEORS, 0, false, true),
            new Preset("Starfall", 0x9FC4FF, 0xFF9AE6, 0x0A0F2A, 0x060818, 0x0B1030, 0x0A0D24, 1, "Fireflies",
                    WorldFxExtra.CONSTELLATION | WorldFxExtra.NEBULA | WorldFxExtra.COMETS | WorldFxExtra.STARDUST,
                    WorldFxThemes.METEORS, 0, false, false),
            new Preset("Sakura", 0xFF9AC8, 0xFFE2A8, 0x4A2232, 0x3A2A30, 0xFFB7D5, 0xF5C6D8, .2f, "Petals",
                    0, WorldFxThemes.LANTERNS, 0, false, false),
            new Preset("Blueprint", 0x6FC3FF, 0xFFFFFF, 0x0A2850, 0x051A38, 0x0E3A6E, 0x0A2E58, .5f, "Data",
                    WorldFxExtra.RADAR | WorldFxExtra.ENTITIES, 0, 0, true, false));

    /** Curated looks (see lookPreset). */
    private static final List<String> LOOKS = List.of("Custom", "Clean", "Cozy Night", "Moody", "Dreamy", "Retro",
            "Deep Sea", "Cinematic", "Neon Lite");

    // Must come after PRESETS and LOOKS: the constructor builds its settings from them.
    public static final WorldThemeModule INSTANCE = new WorldThemeModule();

    /**
     * Curated looks: picking one sets the theme and every other setting to a calm, tasteful combination.
     * Declared first on purpose: config loading goes in declaration order, so the look applies first and
     * any tweaks saved after it load on top. "Custom" leaves everything alone.
     */
    private final String[] look = {"Custom"};
    private final Setting.Mode lookPreset = setting(new Setting.Mode("Look preset", LOOKS, () -> look[0], v -> {
        boolean changed = !v.equals(look[0]);
        look[0] = v;
        if (changed) applyLook(v);
    }), "Pick a ready-made look; it sets every other setting here to a calm combination. Custom changes nothing.");

    private final Setting.Mode theme = setting(Setting.Mode.standalone("World theme", "Liquid Glass",
            PRESETS.stream().map(Preset::name).toArray(String[]::new)));
    private final Setting.Mode colorMode = setting(Setting.Mode.standalone("Color mode", "Theme", "Theme", "Spectrum", "Aurora"), "Theme uses the theme's colors; Spectrum and Aurora cycle through colors over time.");
    private final Setting.Num strength = setting(Setting.Num.standalone("Effect strength", 38, 0, 100, 1), "How strong the color grade, tints and effects are.");
    private final Setting.Bool presetEffects = setting(Setting.Bool.standalone("Theme effects", true), "The world effects that come with the chosen theme. Turn off to use only your + extras.");

    // screen
    private final Setting.Mode grade = setting(Setting.Mode.standalone("Color grade", "Theme",
            "Theme", "Neon", "Noir", "Sepia", "Teal & Orange", "Vapor", "Bleach"));
    private final Setting.Bool screenGrade = setting(Setting.Bool.standalone("Screen color grade", true));
    private final Setting.Bool skyFog = setting(Setting.Bool.standalone("Tint sky and fog", true));
    private final Setting.Num darkness = setting(Setting.Num.standalone("Darkness", 68, 0, 100, 1), "Darkens the world for themes that use it.");
    private final Setting.Bool vignette = setting(Setting.Bool.standalone("Vignette", true));
    private final Setting.Bool scanlines = setting(Setting.Bool.standalone("Scanlines", false));
    private final Setting.Bool grain = setting(Setting.Bool.standalone("Film grain", false));
    private final Setting.Bool cinematic = setting(Setting.Bool.standalone("Cinematic bars", false));

    // particles
    private final Setting.Bool particles = setting(Setting.Bool.standalone("Ambient particles", true));
    private final Setting.Mode particleStyle = setting(Setting.Mode.standalone("Particle style", "Theme",
            "Theme", "Fireflies", "Embers", "Snow", "Petals", "Bubbles", "Ash", "Data"));
    private final Setting.Num density = setting(Setting.Num.standalone("Particle density", 24, 4, 64, 1));

    // effects
    private final Setting.Bool targetGlow = setting(Setting.Bool.standalone("Target outline glow", true));
    private final Setting.Bool caustics = setting(Setting.Bool.standalone("Glass caustics", true));
    private final Setting.Bool ripples = setting(Setting.Bool.standalone("Glass ripples", true));
    private final Setting.Bool xGrid = setting(Setting.Bool.standalone("+ Neon grid", false));
    private final Setting.Bool xLightning = setting(Setting.Bool.standalone("+ Lightning", false));
    private final Setting.Bool xAurora = setting(Setting.Bool.standalone("+ Aurora", false));
    private final Setting.Bool xConstellation = setting(Setting.Bool.standalone("+ Constellation", false));
    private final Setting.Bool xNebula = setting(Setting.Bool.standalone("+ Nebula", false));
    private final Setting.Bool xComets = setting(Setting.Bool.standalone("+ Comets", false));
    private final Setting.Bool xMeteors = setting(Setting.Bool.standalone("+ Meteor shower", false));
    private final Setting.Bool xSun = setting(Setting.Bool.standalone("+ Synthwave sun", false));
    private final Setting.Bool xEclipse = setting(Setting.Bool.standalone("+ Eclipse", false));
    private final Setting.Bool xSkyline = setting(Setting.Bool.standalone("+ Holo skyline", false));
    private final Setting.Bool xPillars = setting(Setting.Bool.standalone("+ Light pillars", false));
    private final Setting.Bool xLanterns = setting(Setting.Bool.standalone("+ Sky lanterns", false));
    private final Setting.Bool xJelly = setting(Setting.Bool.standalone("+ Jellyfish", false));
    private final Setting.Bool xRain = setting(Setting.Bool.standalone("+ Digital rain", false));
    private final Setting.Bool xWisps = setting(Setting.Bool.standalone("+ Spirit wisps", false));
    private final Setting.Bool xOrbitals = setting(Setting.Bool.standalone("+ Orbital rings", false));
    private final Setting.Bool xRunes = setting(Setting.Bool.standalone("+ Ground runes", false));
    private final Setting.Bool xShards = setting(Setting.Bool.standalone("+ Crystal shards", false));
    private final Setting.Bool xRifts = setting(Setting.Bool.standalone("+ Rift gates", false));
    private final Setting.Bool xStardust = setting(Setting.Bool.standalone("+ Stardust", false));
    private final Setting.Bool xRadar = setting(Setting.Bool.standalone("+ Radar sweep", false));
    private final Setting.Bool xEntities = setting(Setting.Bool.standalone("+ Entity holo boxes", false));

    /** Every value a look sets; all "+" extras are cleared first so looks never stack up. */
    private void applyLook(String name) {
        if (name.equals("Custom")) return;
        for (Setting.Bool x : List.of(xGrid, xLightning, xAurora, xConstellation, xNebula, xComets, xMeteors, xSun, xEclipse,
                xSkyline, xPillars, xLanterns, xJelly, xRain, xWisps, xOrbitals, xRunes, xShards, xRifts, xStardust, xRadar, xEntities))
            x.set(false);
        scanlines.set(false);
        grain.set(false);
        cinematic.set(false);
        colorMode.set("Theme");
        screenGrade.set(true);
        skyFog.set(true);
        vignette.set(true);
        targetGlow.set(false);
        caustics.set(true);
        ripples.set(false);
        switch (name) {
            case "Clean" -> look("Liquid Glass", 22, 0, "Theme", true, false, 0, xNone());
            case "Cozy Night" -> look("Aurora Borealis", 25, 35, "Theme", false, true, 10, xAurora, xConstellation);
            case "Moody" -> { look("Void / Eclipse", 20, 45, "Noir", false, true, 8, xEclipse); grain.set(true); }
            case "Dreamy" -> look("Sakura", 22, 10, "Theme", false, true, 12, xLanterns);
            case "Retro" -> look("Synthwave", 28, 30, "Vapor", false, false, 0, xSun);
            case "Deep Sea" -> look("Abyss", 25, 40, "Theme", true, true, 10, xNone());
            case "Cinematic" -> {
                look("Starfall", 18, 30, "Teal & Orange", false, false, 0, xConstellation);
                cinematic.set(true);
                grain.set(true);
            }
            case "Neon Lite" -> { look("Cyberpunk Neon", 22, 30, "Theme", false, true, 8, xSkyline); targetGlow.set(true); }
            default -> {}
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) unblonded.anthrax.gui.Notifications.info("World Themes", "Applied the " + name + " look");
    }

    private static Setting.Bool[] xNone() { return new Setting.Bool[0]; }

    private void look(String themeName, double str, double dark, String gradeName, boolean themeFx, boolean parts,
                      double dens, Setting.Bool... extras) {
        theme.set(themeName);
        strength.set(str);
        darkness.set(dark);
        grade.set(gradeName);
        presetEffects.set(themeFx);
        particles.set(parts);
        particleStyle.set("Theme");
        if (dens > 0) density.set(dens);
        for (Setting.Bool x : extras) x.set(true);
    }

    private Preset preset() {
        String name = theme.get();
        for (Preset p : PRESETS) if (p.name.equals(name)) return p;
        return PRESETS.getFirst();
    }

    private boolean glass() { return preset().name.equals("Liquid Glass"); }

    /** Theme accent (opaque), animated in the Spectrum/Aurora color modes. */
    private int accent(boolean primary) {
        String mode = colorMode.get();
        double t = System.currentTimeMillis() % 3_600_000L * .001;
        if (mode.equals("Spectrum")) return 0xFF000000 | WorldFxExtra.hsl(t * 45 + (primary ? 0 : 150), .9f, .6f);
        if (mode.equals("Aurora")) return 0xFF000000 | WorldFxExtra.hsl(150 + 40 * Math.sin(t * .4) + (primary ? 0 : 110), .85f, .6f);
        Preset p = preset();
        return 0xFF000000 | (primary ? p.a : p.b);
    }

    private static int scale(int rgb, float f) {
        return (Math.round(((rgb >> 16) & 255) * f) << 16) | (Math.round(((rgb >> 8) & 255) * f) << 8) | Math.round((rgb & 255) * f);
    }

    private WorldThemeModule() {
        super("World Themes", "Twelve world themes: palettes, sky, particles and animated world effects", "Visuals", false);
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("anthrax", "world_theme_grade"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (!isEnabled() || client.player == null || client.gui.screen() != null) return;
            unblonded.anthrax.modules.Safe.run(this, () -> screenPass(graphics, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()));
        });
    }

    // ------------------------------------------------------------------ screen pass
    private void screenPass(GuiGraphicsExtractor g, int w, int h) {
        float str = (float) strength.get() / 100f;
        float dark = (float) darkness.get() / 100f * preset().dark;
        if (glass()) frost(g, w, h, str, (float) darkness.get() / 100f);
        else {
            if (screenGrade.get()) {
                int[] colors = palette();
                int alpha = Math.round(90 * str);
                g.fillGradient(0, 0, w, h, (alpha << 24) | (colors[0] & 0xFFFFFF), (alpha << 24) | (colors[1] & 0xFFFFFF));
            }
            if (dark > 0) g.fill(0, 0, w, h, ((int) (105 * dark) << 24));
            if (vignette.get()) vignette(g, w, h, dark);
        }
        double t = System.currentTimeMillis() % 3_600_000L * .001;
        if (scanlines.get()) {
            int a = Math.round(18 + 30 * str);
            for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, a << 24);
            // a soft bright band rolling down the screen
            int band = (int) ((t * 60) % (h + 80)) - 40;
            g.fillGradient(0, band - 20, w, band, 0x00FFFFFF, (Math.round(14 * str) << 24) | (accent(true) & 0xFFFFFF));
            g.fillGradient(0, band, w, band + 20, (Math.round(14 * str) << 24) | (accent(true) & 0xFFFFFF), 0x00FFFFFF);
        }
        if (grain.get()) {
            long seed = (long) (t * 24);
            int dots = Math.min(1600, w * h / 220);
            for (int i = 0; i < dots; i++) {
                long k = seed * 7919 + i * 104729L;
                int x = (int) (frac(k) * w), y = (int) (frac(k + 1) * h);
                boolean light = frac(k + 2) > .5;
                int a = (int) (20 + 30 * frac(k + 3) * (.4 + .6 * str));
                g.fill(x, y, x + 1, y + 1, (a << 24) | (light ? 0xFFFFFF : 0x000000));
            }
        }
        if (cinematic.get()) {
            int bar = Math.max(4, h / 10);
            g.fill(0, 0, w, bar, 0xF0000000);
            g.fill(0, h - bar, w, h, 0xF0000000);
            g.fillGradient(0, bar, w, bar + 6, 0x60000000, 0x00000000);
            g.fillGradient(0, h - bar - 6, w, h - bar, 0x00000000, 0x60000000);
        }
    }

    private void vignette(GuiGraphicsExtractor g, int w, int h, float dark) {
        int steps = 14, th = Math.max(1, Math.max(w, h) / 6 / steps);
        int rgb = scale(accent(false), .22f) & 0xFFFFFF;
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int col = ((int) (150 * k * k * (.5f + .5f * dark)) << 24) | rgb;
            g.fill(0, i * th, w, (i + 1) * th, col);
            g.fill(0, h - (i + 1) * th, w, h - i * th, col);
            g.fill(i * th, 0, (i + 1) * th, h, col);
            g.fill(w - (i + 1) * th, 0, w - i * th, h, col);
        }
    }

    /** Frosted-glass screen pass: cool top sheen, soft edge refraction fringes, light vignette. */
    private void frost(GuiGraphicsExtractor g, int w, int h, float str, float dark) {
        int tintA = accent(true) & 0xFFFFFF, tintB = accent(false) & 0xFFFFFF;
        if (screenGrade.get()) {
            int top = Math.round(46 * str);
            g.fillGradient(0, 0, w, h / 3, (top << 24) | 0xEAF8FF, 0x00EAF8FF);
            g.fillGradient(0, h * 2 / 3, w, h, 0x00000000, (Math.round(30 * str) << 24) | scale(tintB, .5f));
        }
        if (dark > 0) g.fill(0, 0, w, h, (int) (45 * dark) << 24);
        if (vignette.get()) {
            // Chromatic fringe: cyan on the left/top, violet on the right/bottom, like light bending at a lens edge.
            int edge = Math.max(4, Math.min(w, h) / 14);
            int a = Math.round(70 * str);
            g.fillGradient(0, 0, w, edge, (a << 24) | tintA, tintA);
            g.fillGradient(0, h - edge, w, h, tintB, (a << 24) | tintB);
            for (int i = 0; i < edge; i += 2) {
                float k = 1f - i / (float) edge;
                int col = (int) (a * k * k) << 24;
                g.fill(i, 0, i + 2, h, col | tintA);
                g.fill(w - i - 2, 0, w - i, h, col | tintB);
            }
        }
    }

    // ------------------------------------------------------------------ sky and fog
    public static void tintSky(Vector4f skyColor) {
        WorldThemeModule module = INSTANCE;
        if (!module.isEnabled() || !module.skyFog.get() || skyColor == null) return;
        float blend = .34f * (float) module.strength.get() / 100f;
        float[] tint = rgb(module.skyTint(true));
        float d = 1f - .82f * (float) module.darkness.get() / 100f * module.preset().dark;
        skyColor.set(mix(skyColor.x, tint[0], blend) * d, mix(skyColor.y, tint[1], blend) * d,
                mix(skyColor.z, tint[2], blend) * d, skyColor.w);
    }

    public static void tintFog(FogData fog, FogType fogType) {
        WorldThemeModule module = INSTANCE;
        if (!module.isEnabled() || !module.skyFog.get() || fog == null || fog.color == null
                || fogType != FogType.NONE || NoRenderModule.isHidden(3)) return;
        float blend = .58f * (float) module.strength.get() / 100f;
        float[] tint = rgb(module.skyTint(false));
        float d = 1f - .82f * (float) module.darkness.get() / 100f * module.preset().dark;
        fog.color.set(mix(fog.color.x, tint[0], blend) * d, mix(fog.color.y, tint[1], blend) * d,
                mix(fog.color.z, tint[2], blend) * d, fog.color.w);
    }

    private int skyTint(boolean sky) {
        if (!colorMode.get().equals("Theme") || !grade.get().equals("Theme")) return palette()[sky ? 0 : 1];
        Preset p = preset();
        return sky ? p.sky : p.fog;
    }

    // ------------------------------------------------------------------ world effects
    public static void renderParticles(LevelRenderer renderer) {
        WorldThemeModule module = INSTANCE;
        Minecraft client = Minecraft.getInstance();
        if (!module.isEnabled() || client.player == null || client.level == null
                || client.gui.screen() != null) return;
        Preset pr = module.preset();
        boolean preset = module.presetEffects.get();
        float str = (float) module.strength.get() / 100f;
        int a = module.accent(true), b = module.accent(false);
        boolean rainbow = !module.colorMode.get().equals("Theme");

        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            if (module.particles.get()) module.particles(client);

            int glassMask = preset ? pr.glass : 0;
            if (module.glass()) {
                glassMask &= ~((module.caustics.get() ? 0 : WorldGlass.CAUSTICS) | (module.ripples.get() ? 0 : WorldGlass.RIPPLES)
                        | (module.particles.get() ? 0 : WorldGlass.BUBBLES));
            }
            if (glassMask != 0) WorldGlass.draw(client, a & 0xFFFFFF, b & 0xFFFFFF, str, glassMask);

            boolean grid = (preset && pr.grid) || module.xGrid.get();
            boolean bolts = (preset && pr.lightning) || module.xLightning.get();
            if (grid || bolts || module.targetGlow.get())
                WorldFx.draw(client, a, b, str, grid, bolts, module.targetGlow.get());

            int extra = (preset ? pr.extra : 0) | module.extraMask();
            if (extra != 0) WorldFxExtra.draw(client, a, b, str, rainbow, extra);
            int themed = (preset ? pr.themed : 0) | module.themedMask();
            if (themed != 0) WorldFxThemes.draw(client, a & 0xFFFFFF, b & 0xFFFFFF, str, rainbow, themed);
        }
        renderer.addMainThreadGizmos(collector.drainGizmos());
    }

    private int extraMask() {
        int m = 0;
        if (xAurora.get()) m |= WorldFxExtra.AURORA;
        if (xConstellation.get()) m |= WorldFxExtra.CONSTELLATION;
        if (xNebula.get()) m |= WorldFxExtra.NEBULA;
        if (xComets.get()) m |= WorldFxExtra.COMETS;
        if (xPillars.get()) m |= WorldFxExtra.PILLARS;
        if (xOrbitals.get()) m |= WorldFxExtra.ORBITALS;
        if (xRunes.get()) m |= WorldFxExtra.RUNES;
        if (xShards.get()) m |= WorldFxExtra.SHARDS;
        if (xRifts.get()) m |= WorldFxExtra.RIFTS;
        if (xStardust.get()) m |= WorldFxExtra.STARDUST;
        if (xRadar.get()) m |= WorldFxExtra.RADAR;
        if (xEntities.get()) m |= WorldFxExtra.ENTITIES;
        return m;
    }

    private int themedMask() {
        int m = 0;
        if (xMeteors.get()) m |= WorldFxThemes.METEORS;
        if (xSun.get()) m |= WorldFxThemes.SUN;
        if (xEclipse.get()) m |= WorldFxThemes.ECLIPSE;
        if (xSkyline.get()) m |= WorldFxThemes.SKYLINE;
        if (xLanterns.get()) m |= WorldFxThemes.LANTERNS;
        if (xJelly.get()) m |= WorldFxThemes.JELLY;
        if (xRain.get()) m |= WorldFxThemes.RAIN;
        if (xWisps.get()) m |= WorldFxThemes.WISPS;
        return m;
    }

    // ------------------------------------------------------------------ ambient particles
    private void particles(Minecraft client) {
        String style = particleStyle.get().equals("Theme") ? preset().particles : particleStyle.get();
        if (glass() && style.equals("Bubbles")) return;   // the glass theme draws its own refracting bubbles
        // direction (+1 up / -1 down), vertical speed, point size, sideways sway
        double dir = 1, vy, sway = .4;
        float size = 2f;
        switch (style) {
            case "Snow" -> { dir = -1; vy = .9; size = 2.6f; }
            case "Embers" -> vy = 1.2;
            case "Petals" -> { dir = -1; vy = .7; size = 3.2f; sway = 1.3; }
            case "Bubbles" -> { vy = .8; size = 3f; sway = .25; }
            case "Ash" -> { dir = -1; vy = .35; size = 1.8f; sway = .8; }
            case "Data" -> { vy = 2.2; size = 2.2f; sway = .05; }
            default -> vy = .05;
        }
        final double R = 12, H = 10;
        int count = (int) density.get();
        double t = System.currentTimeMillis() % 3_600_000L * .001;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 p = client.player.getPosition(partialTick);
        for (int i = 0; i < count; i++) {
            double h1 = hash(i * 3), h2 = hash(i * 3 + 1), h3 = hash(i * 3 + 2);
            double dx = wrap(h1 * 2 * R + Math.sin(t * .7 + h3 * 6.283) * sway - p.x, 2 * R) - R;
            double dz = wrap(h2 * 2 * R + Math.cos(t * .6 + h1 * 6.283) * sway - p.z, 2 * R) - R;
            double yb = h3 * H + dir * vy * (.7 + h1 * .6) * t + Math.sin(t * 1.3 + h2 * 6.283) * .25;
            double dy = wrap(yb - p.y, H) - H / 2;
            double d2 = (dx * dx + dz * dz) / (R * R);
            double v = dy / (H / 2);
            float fade = (float) (Math.max(0, 1 - d2) * Math.max(0, 1 - v * v));
            if (style.equals("Data")) fade *= (frac(i * 7L + (long) (t * 10)) > .25) ? 1 : .2f;   // flickering bits
            if (style.equals("Fireflies")) fade *= .45f + .55f * (float) Math.sin(t * 2.4 + h1 * 9);
            int base = particleColor(style, (i & 1) == 0);
            int alpha = (int) ((base >>> 24) * fade);
            if (alpha < 4) continue;
            Gizmos.point(new Vec3(p.x + dx, p.y + dy, p.z + dz), (alpha << 24) | (base & 0xFFFFFF), size);
        }
    }

    private int particleColor(String style, boolean primary) {
        return switch (style) {
            case "Snow" -> primary ? 0xDDEBFFFF : 0xAAB7E9FF;
            case "Embers" -> primary ? 0xFFFF9A45 : 0xFFFF4A83;
            case "Petals" -> primary ? 0xF0FFB3D1 : 0xF0FFE0EC;
            case "Bubbles" -> primary ? 0xB0BFF4FF : 0x90E0FFFF;
            case "Ash" -> primary ? 0xC0A8A8B0 : 0xA0606068;
            case "Data" -> (accent(primary) & 0xFFFFFF) | 0xF0000000;
            default -> (accent(primary) & 0xFFFFFF) | 0xE6000000;
        };
    }

    private static double frac(long n) { return hash((int) (n ^ (n >>> 32))); }

    private static double wrap(double v, double period) {
        double m = v % period;
        return m < 0 ? m + period : m;
    }

    private static double hash(int n) {
        long x = (long) n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29; x *= 0xBF58476D1CE4E5B9L; x ^= x >>> 32;
        return (x & 0xFFFFFF) / (double) 0x1000000;
    }

    private int[] palette() {
        return switch (grade.get()) {
            case "Neon" -> new int[]{0x19134A, 0x073F48};
            case "Noir" -> new int[]{0x171923, 0x08090E};
            case "Sepia" -> new int[]{0x4A3422, 0x725337};
            case "Teal & Orange" -> new int[]{0x0E3B43, 0x5A3212};
            case "Vapor" -> new int[]{0x3A1450, 0x0E3A4A};
            case "Bleach" -> new int[]{0x4A4A50, 0x707074};
            default -> {
                if (!colorMode.get().equals("Theme")) yield new int[]{scale(accent(true), .3f), scale(accent(false), .26f)};
                Preset p = preset();
                yield new int[]{p.top, p.bottom};
            }
        };
    }

    private static float[] rgb(int color) {
        return new float[]{((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f};
    }

    private static float mix(float from, float to, float amount) { return from + (to - from) * amount; }
}
