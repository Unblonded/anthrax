package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.client.FriendsModule;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** A trail behind you as you move: a glowing ribbon, a line at your feet, or particles. */
public final class TrailsModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final TrailsModule INSTANCE = new TrailsModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Ribbon", "Ribbon", "Line", "Particles"),
            "Ribbon is a see-through band the height you choose, Line follows your feet, Particles drop the particle below.");
    private final Setting.Mode particles = setting(Setting.Mode.standalone("Particles", "End rod", Fx.PARTICLES), "Used by the Particles style.");
    private final Palette palette = palette("Rainbow");
    private final Setting.Num length = setting(Setting.Num.standalone("Length (s)", 1.5, 0.2, 6, 0.1), "How long the trail lingers.");
    private final Setting.Num height = setting(Setting.Num.standalone("Ribbon height", 0.9, 0.1, 2, 0.05), "");
    private final Setting.Num opacity = setting(Setting.Num.standalone("Opacity", 45, 5, 100, 5), "");
    private final Setting.Bool friends = setting(Setting.Bool.standalone("Friends too", false), "");
    private final Setting.Bool onlyMoving = setting(Setting.Bool.standalone("Only while moving", true), "");

    private record Pt(Vec3 p, long t) {}
    private final Map<Integer, ArrayDeque<Pt>> trails = new HashMap<>();

    private TrailsModule() { super("Trails", "Leaves a fading ribbon, line or particle trail behind you as you move"); }

    private boolean applies(Minecraft mc, Player p) { return p == mc.player || (friends.get() && FriendsModule.isFriend(p)); }

    @Override public void tick(Minecraft mc) {
        if (!style.get().equals("Particles") || mc.level == null) return;
        for (Player p : mc.level.players()) {
            if (!applies(mc, p)) continue;
            if (onlyMoving.get() && p.getDeltaMovement().horizontalDistanceSqr() < 0.001) continue;
            Fx.particle(Fx.named(particles.get(), palette.rgb(Fx.RND.nextDouble())), p.getX(), p.getY() + 0.1 + Fx.RND.nextDouble() * 0.4, p.getZ(), 0, 0, 0);
        }
    }

    @Override public boolean active() { return isEnabled() && !style.get().equals("Particles"); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        long life = (long) (length.get() * 1000);
        for (Player p : mc.level.players()) {
            if (!applies(mc, p)) continue;
            ArrayDeque<Pt> q = trails.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            Vec3 pos = p.getPosition(partial);
            Pt last = q.peekLast();
            boolean moved = last == null || last.p.distanceToSqr(pos) > 0.0025;
            if (moved || !onlyMoving.get()) { if (last == null || now - last.t > 12) q.addLast(new Pt(pos, now)); }
        }
        double h = height.get();
        int baseA = (int) Math.round(opacity.get() / 100 * 255);
        var it = trails.values().iterator();
        while (it.hasNext()) {
            ArrayDeque<Pt> q = it.next();
            while (!q.isEmpty() && now - q.peekFirst().t > life) q.removeFirst();
            if (q.isEmpty()) { it.remove(); continue; }
            Pt prev = null;
            int i = 0, n = q.size();
            for (Pt pt : q) {
                if (prev != null) {
                    float age = (now - pt.t) / (float) life;
                    int rgb = palette.rgb((double) i / Math.max(1, n));
                    int a = Math.round(baseA * (1 - age));
                    if (style.get().equals("Line")) Gizmos.line(prev.p.add(0, 0.05, 0), pt.p.add(0, 0.05, 0), WorldOverlays.argb(Math.min(255, a * 2), rgb), 3f);
                    else Gizmos.rect(prev.p, pt.p, pt.p.add(0, h, 0), prev.p.add(0, h, 0), GizmoStyle.fill(WorldOverlays.argb(a, rgb)));
                }
                prev = pt;
                i++;
            }
        }
    }

    @Override protected void onToggle(boolean enabled) { trails.clear(); }
}
