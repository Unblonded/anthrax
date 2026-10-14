package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A ring that spreads across the ground from where you jump. */
public final class JumpCirclesModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final JumpCirclesModule INSTANCE = new JumpCirclesModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Single", "Single", "Double", "Filled", "Ripple"), "Ripple sends three rings one after another.");
    private final Palette palette = palette("Theme");
    private final Setting.Num size = setting(Setting.Num.standalone("Size", 1.2, 0.4, 3, 0.1), "How far the ring spreads, in blocks.");
    private final Setting.Num duration = setting(Setting.Num.standalone("Duration (ms)", 700, 200, 2000, 50), "");
    private final Setting.Num width = setting(Setting.Num.standalone("Line width", 3, 1, 8, 0.5), "");
    private final Setting.Bool others = setting(Setting.Bool.standalone("Other players", false), "Circles when other players jump too.");

    private record Circle(Vec3 at, int rgb, long born) {}
    private final List<Circle> circles = new ArrayList<>();
    private final Map<Integer, Boolean> ground = new HashMap<>();

    private JumpCirclesModule() { super("Jump Circles", "A ring spreads across the ground every time you jump"); }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null) { ground.clear(); return; }
        for (Player p : mc.level.players()) {
            if (p != mc.player && !others.get()) continue;
            boolean on = p.onGround();
            Boolean was = ground.put(p.getId(), on);
            if (was != null && was && !on && p.getDeltaMovement().y > 0.1)
                circles.add(new Circle(p.position().add(0, 0.02, 0), palette.rgb(circles.size() * 0.13), System.currentTimeMillis()));
        }
        if (circles.size() > 40) circles.removeFirst();
    }

    @Override public boolean active() { return isEnabled() && !circles.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        double dur = duration.get();
        circles.removeIf(c -> now - c.born > dur);
        float w = (float) (double) width.get();
        for (Circle c : circles) {
            float t = (float) ((now - c.born) / dur);
            double ease = 1 - Math.pow(1 - t, 3);
            float r = (float) (0.15 + ease * size.get());
            int a = Math.round(230 * (1 - t));
            switch (style.get()) {
                case "Double" -> {
                    Gizmos.circle(c.at, r, GizmoStyle.stroke(WorldOverlays.argb(a, c.rgb), w));
                    Gizmos.circle(c.at, r * 0.7f, GizmoStyle.stroke(WorldOverlays.argb(a / 2, c.rgb), w * 0.7f));
                }
                case "Filled" -> Gizmos.circle(c.at, r, GizmoStyle.strokeAndFill(WorldOverlays.argb(a, c.rgb), w, WorldOverlays.argb(a / 4, c.rgb)));
                case "Ripple" -> {
                    for (int k = 0; k < 3; k++) {
                        float tt = Math.max(0, t - k * 0.18f);
                        float rr = (float) (0.15 + (1 - Math.pow(1 - tt, 3)) * size.get());
                        Gizmos.circle(c.at, rr, GizmoStyle.stroke(WorldOverlays.argb(Math.round(230 * (1 - tt) * (1 - k * 0.25f)), c.rgb), w));
                    }
                }
                default -> Gizmos.circle(c.at, r, GizmoStyle.stroke(WorldOverlays.argb(a, c.rgb), w));
            }
        }
    }

    @Override protected void onToggle(boolean enabled) { circles.clear(); ground.clear(); }
}
