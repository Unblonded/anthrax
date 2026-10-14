package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Two vapour trails from the wingtips while gliding with an elytra, fading behind you. */
public final class ContrailsModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final ContrailsModule INSTANCE = new ContrailsModule();
    private final Palette palette = palette("White");
    private final Setting.Num length = setting(Setting.Num.standalone("Length (s)", 1.5, 0.3, 5, 0.1), "How long the trails linger.");
    private final Setting.Num width = setting(Setting.Num.standalone("Width", 3, 1, 8, 0.5), "Line thickness.");
    private final Setting.Num span = setting(Setting.Num.standalone("Wingspan", 1.0, 0.4, 2.0, 0.1), "How far apart the two trails start.");
    private final Setting.Bool others = setting(Setting.Bool.standalone("Other players", true), "Trails on everyone gliding near you.");
    private final Setting.Bool fast = setting(Setting.Bool.standalone("Only when fast", false), "Start trails only above roughly 20 blocks per second.");

    private record Pt(Vec3 l, Vec3 r, long t) {}
    private final Map<Integer, ArrayDeque<Pt>> trails = new HashMap<>();

    private ContrailsModule() { super("Contrails", "Vapour trails stream from the wingtips while gliding with an elytra"); }

    @Override public boolean active() { return isEnabled(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        long life = (long) (length.get() * 1000);
        for (Player p : mc.level.players()) {
            if (p != mc.player && !others.get()) continue;
            if (!p.isFallFlying()) continue;
            Vec3 v = p.getDeltaMovement();
            if (fast.get() && v.length() < 1.0) continue;
            Vec3 pos = p.getPosition(partial);
            double yaw = Math.toRadians(p.getViewYRot(partial));
            Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(span.get());
            Vec3 c = pos.add(0, 0.35, 0);
            ArrayDeque<Pt> q = trails.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            Pt last = q.peekLast();
            if (last == null || now - last.t > 15) q.addLast(new Pt(c.add(side), c.subtract(side), now));
        }
        int rgb = palette.rgb();
        float w = (float) (double) width.get();
        var it = trails.entrySet().iterator();
        while (it.hasNext()) {
            ArrayDeque<Pt> q = it.next().getValue();
            while (!q.isEmpty() && now - q.peekFirst().t > life) q.removeFirst();
            if (q.isEmpty()) { it.remove(); continue; }
            Pt prev = null;
            for (Pt pt : q) {
                if (prev != null) {
                    float age = (now - pt.t) / (float) life;
                    int a = Math.round(200 * (1 - age));
                    Gizmos.line(prev.l, pt.l, WorldOverlays.argb(a, rgb), w);
                    Gizmos.line(prev.r, pt.r, WorldOverlays.argb(a, rgb), w);
                }
                prev = pt;
            }
        }
    }

    @Override protected void onToggle(boolean enabled) { trails.clear(); }
}
