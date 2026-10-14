package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Something dramatic where players or mobs die: lightning, rising souls, a blood burst, totem confetti or a halo. */
public final class DeathEffectsModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final DeathEffectsModule INSTANCE = new DeathEffectsModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Lightning", "Lightning", "Souls", "Blood", "Totem", "Firework", "Halo"),
            "Lightning strikes a harmless bolt only you see (with thunder), Souls rise up, Halo floats a ring up into the sky.");
    private final Setting.Mode who = setting(Setting.Mode.standalone("Who", "Players", "Players", "Players and hostiles", "Everything"), "Which deaths get the effect.");
    private final Setting.Bool self = setting(Setting.Bool.standalone("Your own death", true), "");
    private final Palette palette = palette("Theme");
    private final Setting.Bool sound = setting(Setting.Bool.standalone("Sound", true), "A sound for the non-lightning styles.");
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 64, 8, 128, 8), "");

    private final Set<Integer> dead = new HashSet<>();
    private record Halo(Vec3 at, int rgb, long born) {}
    private final List<Halo> halos = new ArrayList<>();

    private DeathEffectsModule() { super("Death Effects", "Lightning, souls, blood or a halo where players and mobs die"); }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { dead.clear(); return; }
        Set<Integer> still = new HashSet<>();
        double r2 = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity l) || !l.isDeadOrDying()) continue;
            still.add(e.getId());
            if (dead.contains(e.getId()) || e.distanceToSqr(mc.player) > r2) continue;
            if (!counts(mc, e)) continue;
            play(mc, e);
        }
        dead.retainAll(still);
        dead.addAll(still);
    }

    private boolean counts(Minecraft mc, Entity e) {
        if (e == mc.player) return self.get();
        return switch (who.get()) {
            case "Everything" -> true;
            case "Players and hostiles" -> e instanceof Player || e instanceof net.minecraft.world.entity.monster.Enemy;
            default -> e instanceof Player;
        };
    }

    private void play(Minecraft mc, Entity e) {
        Vec3 at = e.position();
        Vec3 mid = at.add(0, e.getBbHeight() / 2, 0);
        switch (style.get()) {
            case "Souls" -> {
                for (int i = 0; i < 18; i++) Fx.particle(i % 3 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL, mid.add((Fx.RND.nextDouble() - 0.5) * 0.8, (Fx.RND.nextDouble() - 0.5), (Fx.RND.nextDouble() - 0.5) * 0.8), 0, 0.06 + Fx.RND.nextDouble() * 0.05, 0);
                if (sound.get()) Fx.at(at, SoundEvents.SOUL_ESCAPE.value(), 1.5f, 1f);
            }
            case "Blood" -> {
                Fx.burst(Fx.dust(0xA0101A, 1.6f), mid, 40, e.getBbWidth(), 0.25);
                if (sound.get()) Fx.at(at, SoundEvents.HONEY_BLOCK_BREAK, 1f, 0.6f);
            }
            case "Totem" -> {
                Fx.burst(ParticleTypes.TOTEM_OF_UNDYING, mid, 50, 0.6, 0.45);
                if (sound.get()) Fx.at(at, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
            }
            case "Firework" -> {
                for (int i = 0; i < 40; i++) Fx.burst(Fx.dust(palette.rgb(i / 40.0), 1.2f), mid.add(0, 1.5, 0), 1, 0.2, 0.35);
                Fx.burst(ParticleTypes.FIREWORK, mid.add(0, 1.5, 0), 30, 0.3, 0.25);
                if (sound.get()) Fx.at(at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 1f);
            }
            case "Halo" -> {
                halos.add(new Halo(at.add(0, e.getBbHeight() + 0.2, 0), palette.rgb(), System.currentTimeMillis()));
                Fx.burst(ParticleTypes.END_ROD, mid, 14, 0.5, 0.06);
                if (sound.get()) Fx.at(at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 1.2f);
            }
            default -> Fx.lightning(at);
        }
    }

    @Override public boolean active() { return isEnabled() && !halos.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        halos.removeIf(h -> now - h.born > 2500);
        for (Halo h : halos) {
            float t = (now - h.born) / 2500f;
            int a = Math.round(230 * (1 - t));
            Vec3 p = h.at.add(0, t * 4, 0);
            Gizmos.circle(p, 0.45f + t * 0.2f, GizmoStyle.stroke(WorldOverlays.argb(a, h.rgb), 3f));
            Gizmos.circle(p, 0.38f + t * 0.2f, GizmoStyle.stroke(WorldOverlays.argb(a / 2, 0xFFFFFF), 1.5f));
        }
    }

    @Override protected void onToggle(boolean enabled) { dead.clear(); halos.clear(); }
}
