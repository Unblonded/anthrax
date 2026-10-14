package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.client.FriendsModule;
import unblonded.anthrax.modules.visual.WorldOverlays;

/** An aura around you: a double helix, floating rings, orbiting orbs or a cloud of particles. */
public final class PlayerAuraModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final PlayerAuraModule INSTANCE = new PlayerAuraModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Helix", "Helix", "Rings", "Orbs", "Halo", "Particles"),
            "Particles uses the particle choice below; the others are drawn shapes.");
    private final Setting.Mode particles = setting(Setting.Mode.standalone("Particles", "Witch", Fx.PARTICLES), "Used by the Particles style.");
    private final Palette palette = palette("Theme");
    private final Setting.Num speed = setting(Setting.Num.standalone("Speed", 1, 0.1, 4, 0.1), "");
    private final Setting.Num radius = setting(Setting.Num.standalone("Radius", 0.7, 0.3, 2, 0.05), "");
    private final Setting.Bool firstPerson = setting(Setting.Bool.standalone("Show in first person", false), "Draw your own aura even when looking through your eyes.");
    private final Setting.Bool friends = setting(Setting.Bool.standalone("Friends too", false), "Give your friends the same aura.");

    private PlayerAuraModule() { super("Player Aura", "A helix, rings, orbiting orbs or particles swirl around you"); }

    @Override public void tick(Minecraft mc) {
        if (!style.get().equals("Particles") || mc.level == null) return;
        for (Player p : mc.level.players()) {
            if (!applies(mc, p)) continue;
            double a = Fx.RND.nextDouble() * Math.PI * 2;
            double r = radius.get();
            Fx.particle(Fx.named(particles.get(), palette.rgb(Fx.RND.nextDouble())),
                    p.getX() + Math.cos(a) * r, p.getY() + Fx.RND.nextDouble() * p.getBbHeight(), p.getZ() + Math.sin(a) * r, 0, 0.01, 0);
        }
    }

    private boolean applies(Minecraft mc, Player p) {
        if (p == mc.player) return firstPerson.get() || !firstPerson(mc);
        return friends.get() && FriendsModule.isFriend(p);
    }

    @Override public boolean active() { return isEnabled() && !style.get().equals("Particles"); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        double t = System.currentTimeMillis() / 1000.0 * speed.get();
        double r = radius.get();
        for (Player p : mc.level.players()) {
            if (!applies(mc, p)) continue;
            Vec3 base = p.getPosition(partial);
            double h = p.getBbHeight();
            switch (style.get()) {
                case "Rings" -> {
                    for (int i = 0; i < 3; i++) {
                        double y = h * (0.5 + 0.45 * Math.sin(t * 1.6 + i * 2.1));
                        Gizmos.circle(base.add(0, y, 0), (float) (r * (0.85 + 0.15 * Math.sin(t * 2 + i))), GizmoStyle.stroke(WorldOverlays.argb(200, palette.rgb(i / 3.0)), 2.5f));
                    }
                }
                case "Orbs" -> {
                    for (int i = 0; i < 6; i++) {
                        double a = t * 2 + i * Math.PI / 3;
                        double y = h * 0.55 + 0.35 * Math.sin(t * 3 + i);
                        Gizmos.point(base.add(Math.cos(a) * r, y, Math.sin(a) * r), WorldOverlays.argb(240, palette.rgb(i / 6.0)), 9f);
                    }
                }
                case "Halo" -> {
                    Vec3 top = base.add(0, h + 0.25 + 0.05 * Math.sin(t * 3), 0);
                    Gizmos.circle(top, 0.32f, GizmoStyle.stroke(WorldOverlays.argb(235, palette.rgb()), 3.5f));
                    Gizmos.circle(top, 0.32f, GizmoStyle.stroke(WorldOverlays.argb(90, 0xFFFFFF), 7f));
                }
                default -> {
                    int seg = 40;
                    for (int s = 0; s < 2; s++) {
                        Vec3 prev = null;
                        for (int i = 0; i <= seg; i++) {
                            double f = (double) i / seg;
                            double a = t * 2 + f * Math.PI * 4 + s * Math.PI;
                            Vec3 pt = base.add(Math.cos(a) * r, f * h * 1.05, Math.sin(a) * r);
                            if (prev != null) Gizmos.line(prev, pt, WorldOverlays.argb(210, palette.rgb(f + s * 0.5)), 2.5f);
                            prev = pt;
                        }
                    }
                }
            }
        }
    }
}
