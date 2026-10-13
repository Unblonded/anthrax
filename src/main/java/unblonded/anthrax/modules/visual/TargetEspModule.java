package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.combat.CombatUtil;

/** Marks your current fight target with a moving ring, orbiting trails or a halo. */
public final class TargetEspModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final TargetEspModule INSTANCE = new TargetEspModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Ring", "Ring", "Orbit", "Halo"),
            "Ring slides up and down the target, Orbit circles points around them with trails, Halo floats a ring above their head.");
    private final Setting.Mode color = setting(Setting.Mode.standalone("Colour", "Health", "Health", "Red", "Accent white"),
            "Health fades from green to red as they lose health.");
    private final Setting.Num speed = setting(Setting.Num.standalone("Speed", 1.0, 0.2, 3.0, 0.1), "Animation speed.");
    private final Setting.Num width = setting(Setting.Num.standalone("Line width", 2.5, 1, 6, 0.5), "Thickness of the marker.");

    private TargetEspModule() { super("Target ESP", "Marks your current target with a moving ring, orbiting trails or a halo", "Visuals", false); }

    @Override public boolean active() { return isEnabled() && CombatUtil.target() != null; }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        var t = CombatUtil.target();
        if (t == null) return;
        Vec3 pos = t.getPosition(partial);
        double h = t.getBbHeight(), r = t.getBbWidth() * 0.9 + 0.15;
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        int rgb = switch (color.get()) {
            case "Red" -> 0xFF4D6D;
            case "Accent white" -> 0xFFFFFF;
            default -> {
                float f = Math.max(0, Math.min(1, t.getHealth() / Math.max(1, t.getMaxHealth())));
                yield (Math.round(255 * (1 - f)) << 16) | (Math.round(220 * f + 35) << 8) | 0x40;
            }
        };
        float w = (float) (double) width.get();
        switch (style.get()) {
            case "Orbit" -> {
                for (int i = 0; i < 3; i++) {
                    double a0 = time * 3 + i * Math.PI * 2 / 3;
                    double y = h * (0.5 + 0.4 * Math.sin(time * 2 + i));
                    for (int k = 0; k < 6; k++) {
                        double a = a0 - k * 0.12, b = a0 - (k + 1) * 0.12;
                        int alpha = 0xE0 - k * 0x22;
                        Gizmos.line(pos.add(Math.cos(a) * r, y, Math.sin(a) * r), pos.add(Math.cos(b) * r, y, Math.sin(b) * r),
                                WorldOverlays.argb(alpha, rgb), w).setAlwaysOnTop();
                    }
                }
            }
            case "Halo" -> Gizmos.circle(pos.add(0, h + 0.25 + 0.05 * Math.sin(time * 3), 0), (float) (r * 0.6),
                    GizmoStyle.stroke(WorldOverlays.argb(0xE0, rgb), w)).setAlwaysOnTop();
            default -> {
                double y = h * (0.5 + 0.5 * Math.sin(time * 2));
                Gizmos.circle(pos.add(0, y, 0), (float) r, GizmoStyle.stroke(WorldOverlays.argb(0xE0, rgb), w)).setAlwaysOnTop();
                Gizmos.circle(pos.add(0, Math.max(0, y - 0.12), 0), (float) r, GizmoStyle.stroke(WorldOverlays.argb(0x50, rgb), w)).setAlwaysOnTop();
            }
        }
    }
}
