package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** When a player teleports (pearls, commands, chorus fruit), a burst of portal particles and a beam at both ends. */
public final class TeleportEffectsModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final TeleportEffectsModule INSTANCE = new TeleportEffectsModule();
    private final Palette palette = palette("Vaporwave");
    private final Setting.Num minDistance = setting(Setting.Num.standalone("Minimum jump", 6, 3, 30, 1), "How far a player must move in one tick to count as a teleport.");
    private final Setting.Bool beams = setting(Setting.Bool.standalone("Beams", true), "A column of light at the start and end.");
    private final Setting.Bool line = setting(Setting.Bool.standalone("Connect ends", true), "A fading line from where they were to where they went.");
    private final Setting.Bool sound = setting(Setting.Bool.standalone("Sound", true), "");
    private final Setting.Bool others = setting(Setting.Bool.standalone("Other players", true), "");

    private record Jump(Vec3 from, Vec3 to, int rgb, long born) {}
    private final List<Jump> jumps = new ArrayList<>();
    private final Map<Integer, Vec3> last = new HashMap<>();
    private Object lastLevel;

    private TeleportEffectsModule() { super("Teleport Effects", "Portal bursts and light beams wherever players teleport"); }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null) { last.clear(); return; }
        if (lastLevel != mc.level) { last.clear(); lastLevel = mc.level; }   // a dimension change is not a teleport
        double min2 = minDistance.get() * minDistance.get();
        Map<Integer, Vec3> now = new HashMap<>();
        for (Player p : mc.level.players()) {
            if (p != mc.player && !others.get()) continue;
            Vec3 pos = p.position();
            Vec3 before = last.get(p.getId());
            now.put(p.getId(), pos);
            if (before == null || p.isPassenger() || before.distanceToSqr(pos) < min2) continue;
            // fast flight is not a teleport: compare with how fast they were moving
            if (p.getDeltaMovement().lengthSqr() * 4 > before.distanceToSqr(pos)) continue;
            play(before, pos);
        }
        last.clear();
        last.putAll(now);
    }

    private void play(Vec3 from, Vec3 to) {
        for (Vec3 at : new Vec3[]{from, to}) {
            for (int i = 0; i < 40; i++)
                Fx.particle(i % 2 == 0 ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL, at.add((Fx.RND.nextDouble() - 0.5) * 0.8, Fx.RND.nextDouble() * 2, (Fx.RND.nextDouble() - 0.5) * 0.8),
                        (Fx.RND.nextDouble() - 0.5) * 0.6, (Fx.RND.nextDouble() - 0.5) * 0.6, (Fx.RND.nextDouble() - 0.5) * 0.6);
        }
        if (sound.get()) { Fx.at(from, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.2f); Fx.at(to, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.9f); }
        jumps.add(new Jump(from, to, palette.rgb(), System.currentTimeMillis()));
        if (jumps.size() > 20) jumps.removeFirst();
    }

    @Override public boolean active() { return isEnabled() && !jumps.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        jumps.removeIf(j -> now - j.born > 1500);
        for (Jump j : jumps) {
            float t = (now - j.born) / 1500f;
            int a = Math.round(230 * (1 - t));
            if (beams.get())
                for (Vec3 at : new Vec3[]{j.from, j.to}) {
                    double h = 3 + 5 * t;
                    Gizmos.line(at, at.add(0, h, 0), WorldOverlays.argb(a, j.rgb), 6f * (1 - t) + 1);
                    Gizmos.circle(at.add(0, 0.05, 0), (float) (0.4 + t * 1.2), GizmoStyle.stroke(WorldOverlays.argb(a, j.rgb), 2.5f));
                }
            if (line.get()) Gizmos.line(j.from.add(0, 1, 0), j.to.add(0, 1, 0), WorldOverlays.argb(a / 2, j.rgb), 2f);
        }
    }

    @Override protected void onToggle(boolean enabled) { jumps.clear(); last.clear(); }
}
