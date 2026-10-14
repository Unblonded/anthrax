package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Fading trails behind arrows, tridents, pearls, snowballs, potions and fireworks, coloured by type or palette. */
public final class ProjectileTrailsModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final ProjectileTrailsModule INSTANCE = new ProjectileTrailsModule();
    private final Setting.Mode colourBy = setting(Setting.Mode.standalone("Colour by", "Type", "Type", "Palette"),
            "Type: arrows white, pearls purple, tridents aqua, fireworks orange, other throwables yellow.");
    private final Palette palette = palette("Rainbow");
    private final Setting.Num length = setting(Setting.Num.standalone("Length (s)", 1.2, 0.2, 5, 0.1), "How long trails linger.");
    private final Setting.Num width = setting(Setting.Num.standalone("Width", 2.5, 1, 8, 0.5), "");
    private final Setting.Bool arrows = setting(Setting.Bool.standalone("Arrows", true), "");
    private final Setting.Bool pearls = setting(Setting.Bool.standalone("Ender pearls", true), "");
    private final Setting.Bool tridents = setting(Setting.Bool.standalone("Tridents", true), "");
    private final Setting.Bool fireworks = setting(Setting.Bool.standalone("Fireworks", true), "");
    private final Setting.Bool throwables = setting(Setting.Bool.standalone("Other throwables", true), "Snowballs, eggs, potions, wind charges.");
    private final Setting.Bool stuck = setting(Setting.Bool.standalone("Stop when stuck", true), "No trail for arrows lying in the ground.");

    private record Pt(Vec3 p, long t) {}
    private record Trail(ArrayDeque<Pt> pts, int rgb) {}
    private final Map<Integer, Trail> trails = new HashMap<>();

    private ProjectileTrailsModule() { super("Projectile Trails", "Fading trails behind arrows, pearls, tridents, fireworks and other thrown things"); }

    @Override public boolean active() { return isEnabled(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        long life = (long) (length.get() * 1000);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Projectile) || !wanted(e)) continue;
            if (stuck.get() && e.getDeltaMovement().lengthSqr() < 0.0004) continue;
            Trail tr = trails.computeIfAbsent(e.getId(), k -> new Trail(new ArrayDeque<>(), colour(e)));
            Pt last = tr.pts.peekLast();
            Vec3 p = e.getPosition(partial).add(0, e.getBbHeight() / 2, 0);
            if (last == null || last.p.distanceToSqr(p) > 0.01) tr.pts.addLast(new Pt(p, now));
        }
        float w = (float) (double) width.get();
        var it = trails.values().iterator();
        while (it.hasNext()) {
            Trail tr = it.next();
            while (!tr.pts.isEmpty() && now - tr.pts.peekFirst().t > life) tr.pts.removeFirst();
            if (tr.pts.isEmpty()) { it.remove(); continue; }
            Pt prev = null;
            int i = 0, n = tr.pts.size();
            for (Pt pt : tr.pts) {
                if (prev != null) {
                    float age = (now - pt.t) / (float) life;
                    int rgb = colourBy.get().equals("Palette") ? palette.rgb((double) i / n) : tr.rgb;
                    Gizmos.line(prev.p, pt.p, WorldOverlays.argb(Math.round(220 * (1 - age)), rgb), w * (0.4f + 0.6f * (1 - age)));
                }
                prev = pt;
                i++;
            }
        }
    }

    private boolean wanted(Entity e) {
        if (e instanceof ThrownTrident) return tridents.get();
        if (e instanceof AbstractArrow) return arrows.get();
        if (e instanceof ThrownEnderpearl) return pearls.get();
        if (e instanceof FireworkRocketEntity) return fireworks.get();
        return throwables.get();
    }

    private static int colour(Entity e) {
        if (e instanceof ThrownTrident) return 0x3DE6E6;
        if (e instanceof AbstractArrow) return 0xF0F0F0;
        if (e instanceof ThrownEnderpearl) return 0xB04DFF;
        if (e instanceof FireworkRocketEntity) return 0xFF9A3C;
        return 0xFFE15C;
    }

    @Override protected void onToggle(boolean enabled) { trails.clear(); }
}
