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

/** Footprints left on the ground where you (and optionally others) walk, fading after a while. */
public final class FootstepsModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final FootstepsModule INSTANCE = new FootstepsModule();
    private final Palette palette = palette("White");
    private final Setting.Num lifetime = setting(Setting.Num.standalone("Lifetime (s)", 6, 1, 30, 1), "How long footprints stay.");
    private final Setting.Num opacity = setting(Setting.Num.standalone("Opacity", 60, 10, 100, 5), "");
    private final Setting.Num stride = setting(Setting.Num.standalone("Stride", 0.7, 0.3, 1.5, 0.05), "Distance between steps, in blocks.");
    private final Setting.Bool others = setting(Setting.Bool.standalone("Other players", false), "Footprints for everyone near you.");

    private record Step(Vec3 at, double yaw, boolean left, int rgb, long born) {}
    private final List<Step> steps = new ArrayList<>();
    private final Map<Integer, Vec3> lastStep = new HashMap<>();
    private final Map<Integer, Boolean> foot = new HashMap<>();

    private FootstepsModule() { super("Footsteps", "Leaves fading footprints on the ground where you walk"); }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null) { steps.clear(); lastStep.clear(); return; }
        for (Player p : mc.level.players()) {
            if (p != mc.player && !others.get()) continue;
            if (!p.onGround() || p.isSpectator() || p.isInWater()) continue;
            Vec3 pos = p.position();
            Vec3 last = lastStep.get(p.getId());
            if (last != null && last.distanceToSqr(pos) < stride.get() * stride.get()) continue;
            lastStep.put(p.getId(), pos);
            if (last == null) continue;
            boolean left = !foot.getOrDefault(p.getId(), false);
            foot.put(p.getId(), left);
            Vec3 dir = pos.subtract(last);
            double yaw = Math.atan2(dir.z, dir.x);
            steps.add(new Step(pos.add(0, 0.015, 0), yaw, left, palette.rgb(steps.size() * 0.07), System.currentTimeMillis()));
        }
        if (steps.size() > 400) steps.subList(0, steps.size() - 400).clear();
    }

    @Override public boolean active() { return isEnabled() && !steps.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        long life = (long) (lifetime.get() * 1000);
        steps.removeIf(s -> now - s.born > life);
        for (Step s : steps) {
            float age = (now - s.born) / (float) life;
            int a = (int) Math.round(opacity.get() / 100 * 255 * (1 - age * age));
            double fx = Math.cos(s.yaw), fz = Math.sin(s.yaw);          // forward
            double sx = -fz, sz = fx;                                       // side
            double off = s.left ? 0.14 : -0.14;
            Vec3 c = s.at.add(sx * off, 0, sz * off);
            // a sole (longer) and a heel, both as flat quads
            quad(c.add(fx * 0.06, 0, fz * 0.06), fx, fz, sx, sz, 0.11, 0.065, a, s.rgb);
            quad(c.add(-fx * 0.12, 0, -fz * 0.12), fx, fz, sx, sz, 0.05, 0.055, a, s.rgb);
        }
    }

    private static void quad(Vec3 c, double fx, double fz, double sx, double sz, double len, double wid, int a, int rgb) {
        Vec3 p1 = c.add(fx * len + sx * wid, 0, fz * len + sz * wid);
        Vec3 p2 = c.add(fx * len - sx * wid, 0, fz * len - sz * wid);
        Vec3 p3 = c.add(-fx * len - sx * wid, 0, -fz * len - sz * wid);
        Vec3 p4 = c.add(-fx * len + sx * wid, 0, -fz * len + sz * wid);
        Gizmos.rect(p1, p2, p3, p4, GizmoStyle.fill(WorldOverlays.argb(a, rgb)));
    }

    @Override protected void onToggle(boolean enabled) { steps.clear(); lastStep.clear(); foot.clear(); }
}
