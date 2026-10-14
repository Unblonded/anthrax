package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.List;

/** Bigger explosions: expanding shockwave rings, a screen flash when you are close, sparks and a smoke column. */
public final class ExplosionEffectsModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final ExplosionEffectsModule INSTANCE = new ExplosionEffectsModule();
    private final Setting.Bool rings = setting(Setting.Bool.standalone("Shockwave rings", true), "Rings that race outwards along the ground.");
    private final Palette palette = palette("Fire");
    private final Setting.Num ringSize = setting(Setting.Num.standalone("Ring size", 3, 1, 8, 0.5), "How far the rings travel, times the blast radius.");
    private final Setting.Bool flash = setting(Setting.Bool.standalone("Screen flash", true), "A white flash when an explosion goes off near you.");
    private final Setting.Num flashStrength = setting(Setting.Num.standalone("Flash strength", 50, 5, 100, 5), "");
    private final Setting.Bool sparks = setting(Setting.Bool.standalone("Sparks", true), "Flying sparks and embers.");
    private final Setting.Bool smoke = setting(Setting.Bool.standalone("Smoke column", true), "A column of smoke rising from the blast.");

    private record Blast(Vec3 at, float radius, int rgb, long born) {}
    private final List<Blast> blasts = new ArrayList<>();
    private long flashAt;
    private float flashPower;

    private ExplosionEffectsModule() { super("Explosion Effects", "Shockwave rings, a screen flash, sparks and smoke on every explosion"); }

    /** From the explosion packet (CosmeticPacketMixin). */
    public void onExplosion(Vec3 at, float radius) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        float r = Math.max(1, radius);
        blasts.add(new Blast(at, r, palette.rgb(), System.currentTimeMillis()));
        if (blasts.size() > 24) blasts.removeFirst();
        if (flash.get() && mc.player != null) {
            double d = mc.player.position().distanceTo(at);
            float power = (float) Math.max(0, 1 - d / (r * 8));
            if (power > flashPower * remaining()) { flashPower = power; flashAt = System.currentTimeMillis(); }
        }
        if (sparks.get()) {
            Fx.burst(ParticleTypes.LAVA, at, (int) (6 * r), r * 0.5, 0.3);
            Fx.burst(ParticleTypes.FLAME, at, (int) (10 * r), r * 0.6, 0.25);
            Fx.burst(ParticleTypes.ELECTRIC_SPARK, at, (int) (8 * r), r * 0.5, 0.5);
        }
        if (smoke.get())
            for (int i = 0; i < 12 * r; i++)
                Fx.particle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.add((Fx.RND.nextDouble() - 0.5) * r, Fx.RND.nextDouble(), (Fx.RND.nextDouble() - 0.5) * r), 0, 0.05 + Fx.RND.nextDouble() * 0.06, 0);
    }

    private float remaining() { return Math.max(0, 1 - (System.currentTimeMillis() - flashAt) / 400f); }

    /** Flash strength 0..1 for the HUD. */
    public float flash() {
        if (!isEnabled() || !flash.get()) return 0;
        float r = remaining();
        return flashPower * r * r * (float) (flashStrength.get() / 100);
    }

    @Override public boolean active() { return isEnabled() && rings.get() && !blasts.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        blasts.removeIf(b -> now - b.born > 900);
        for (Blast b : blasts) {
            float t = (now - b.born) / 900f;
            double ease = 1 - Math.pow(1 - t, 3);
            for (int k = 0; k < 3; k++) {
                double tt = Math.max(0, ease - k * 0.12);
                float rad = (float) (0.5 + tt * b.radius * ringSize.get());
                int a = (int) Math.round(230 * (1 - t) * (1 - k * 0.25));
                Gizmos.circle(b.at.add(0, 0.1 + k * 0.25, 0), rad, GizmoStyle.stroke(WorldOverlays.argb(a, k == 0 ? 0xFFFFFF : b.rgb), 4f - k));
            }
        }
    }

    @Override protected void onToggle(boolean enabled) { blasts.clear(); flashPower = 0; }
}
