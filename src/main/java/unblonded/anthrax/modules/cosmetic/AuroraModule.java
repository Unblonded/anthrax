package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.ThemeColors;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

/** Northern lights: soft, waving curtains of colour high in the sky, strongest at night. */
public final class AuroraModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final AuroraModule INSTANCE = new AuroraModule();
    private final Setting.Mode colours = setting(Setting.Mode.standalone("Colours", "Classic", "Classic", "Violet", "Crimson", "Ocean", "Rainbow", "Theme"),
            "Classic is green fading to violet at the top.");
    private final Setting.Num brightness = setting(Setting.Num.standalone("Brightness", 55, 10, 100, 5), "How strong the curtains are.");
    private final Setting.Num height = setting(Setting.Num.standalone("Height", 45, 20, 90, 5), "How tall the curtains are, in blocks.");
    private final Setting.Num speed = setting(Setting.Num.standalone("Wave speed", 1, 0.1, 4, 0.1), "How fast the curtains ripple.");
    private final Setting.Num bands = setting(Setting.Num.standalone("Bands", 2, 1, 4, 1), "How many curtains hang across the sky.");
    private final Setting.Bool nightOnly = setting(Setting.Bool.standalone("Only at night", true), "Fades out during the day.");
    private final Setting.Bool overworldOnly = setting(Setting.Bool.standalone("Overworld only", true), "Hidden in the Nether and the End.");

    private AuroraModule() { super("Aurora", "Draws waving northern-light curtains across the night sky"); }

    @Override public boolean active() { return isEnabled(); }

    /** 1 at night, 0 by day, fading over dusk and dawn. */
    private static float night(Minecraft mc) {
        long t = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
        if (t >= 13500 && t <= 22500) return 1;
        if (t > 12000 && t < 13500) return (t - 12000) / 1500f;
        if (t > 22500) return 1 - (t - 22500) / 1500f;
        return 0;
    }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        if (overworldOnly.get() && (mc.level.dimensionType().hasCeiling() || mc.level.dimensionType().hasFixedTime())) return;
        float vis = nightOnly.get() ? night(mc) : 1;
        if (vis <= 0.01f) return;
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        double base = eye.y + 55, h = height.get();
        int cols = 48, rows = 6, nb = (int) bands.get();
        float strength = vis * (float) (double) brightness.get() / 100f;
        for (int b = 0; b < nb; b++) {
            double centre = -Math.PI / 2 + (b - (nb - 1) / 2.0) * 0.55;   // north is -Z
            double r = 110 + b * 14;
            Vec3[][] grid = new Vec3[cols + 1][rows + 1];
            for (int c = 0; c <= cols; c++) {
                double f = (double) c / cols;
                double ang = centre + (f - 0.5) * 2.2 + 0.08 * Math.sin(time * 0.7 + f * 9 + b);
                double wob = 6 * Math.sin(time * 1.3 + f * 14 + b * 2) + 3 * Math.sin(time * 2.1 + f * 31);
                for (int k = 0; k <= rows; k++) {
                    double y = base + b * 6 + h * k / rows + 3 * Math.sin(time + f * 6 + k);
                    double rr = r + wob * (0.4 + 0.6 * k / rows);
                    grid[c][k] = new Vec3(eye.x + Math.cos(ang) * rr, y, eye.z + Math.sin(ang) * rr);
                }
            }
            for (int c = 0; c < cols; c++) {
                double f = (c + 0.5) / cols;
                double edge = Math.sin(Math.PI * f);   // fade at the curtain ends
                double flicker = 0.75 + 0.25 * Math.sin(time * 3 + c * 0.9 + b);
                for (int k = 0; k < rows; k++) {
                    double up = (k + 0.5) / rows;
                    double a = strength * edge * flicker * (k == 0 ? 0.55 : 1 - up * 0.85);
                    int alpha = (int) Math.round(a * 150);
                    if (alpha < 3) continue;
                    Gizmos.rect(grid[c][k], grid[c + 1][k], grid[c + 1][k + 1], grid[c][k + 1], GizmoStyle.fill(WorldOverlays.argb(alpha, colour(up, f, time))));
                }
            }
        }
    }

    private int colour(double up, double along, double time) {
        return switch (colours.get()) {
            case "Violet" -> mix(0x7A3CFF, 0xFF5CE1, (float) up);
            case "Crimson" -> mix(0xFF2E4D, 0xFFB03A, (float) up);
            case "Ocean" -> mix(0x1EE3CF, 0x2D6BFF, (float) up);
            case "Rainbow" -> hsl((float) (along * 300 + time * 30), 0.9f, 0.6f);
            case "Theme" -> mix(ThemeColors.accent() & 0xFFFFFF, ThemeColors.accent2() & 0xFFFFFF, (float) up);
            default -> up < 0.5 ? mix(0x3DFF8A, 0x2EF7D0, (float) (up * 2)) : mix(0x2EF7D0, 0xA25CFF, (float) ((up - 0.5) * 2));
        };
    }
}
