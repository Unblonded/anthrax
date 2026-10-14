package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;

import java.util.HashMap;
import java.util.Map;

/** When you get hurt: a coloured pulse around the screen edges; when anything nearby is hurt: a burst of particles. */
public final class DamageEffectsModule extends CosmeticModule {
    public static final DamageEffectsModule INSTANCE = new DamageEffectsModule();
    private final Setting.Bool vignette = setting(Setting.Bool.standalone("Screen pulse", true), "Edges of the screen flash when you take damage.");
    private final Setting.Num strength = setting(Setting.Num.standalone("Pulse strength", 60, 10, 100, 5), "");
    private final Setting.Bool scaleByDamage = setting(Setting.Bool.standalone("Stronger for big hits", true), "Bigger hits give a stronger pulse.");
    private final Palette palette = palette("Blood");
    private final Setting.Bool particlesSelf = setting(Setting.Bool.standalone("Particles on you", false), "A burst on yourself when hurt (visible in third person).");
    private final Setting.Bool particlesOthers = setting(Setting.Bool.standalone("Particles on others", true), "A burst on any mob or player that gets hurt near you.");
    private final Setting.Mode particles = setting(Setting.Mode.standalone("Particles", "Blood", Fx.PARTICLES), "");
    private final Setting.Num amount = setting(Setting.Num.standalone("Amount", 12, 2, 40, 1), "");

    private final Map<Integer, Integer> lastHurt = new HashMap<>();
    private float lastHealth = -1;
    private long pulseAt;
    private float pulsePower;

    private DamageEffectsModule() { super("Damage Effects", "A screen-edge pulse when you are hurt and particle bursts when anything nearby takes damage"); }

    @Override public void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { lastHurt.clear(); return; }
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (lastHealth >= 0 && hp < lastHealth - 0.01f) {
            float dmg = lastHealth - hp;
            pulseAt = System.currentTimeMillis();
            pulsePower = scaleByDamage.get() ? Math.min(1f, 0.35f + dmg / 10f) : 1f;
        }
        lastHealth = hp;
        Map<Integer, Integer> now = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity l) || e.distanceToSqr(mc.player) > 48 * 48) continue;
            int before = lastHurt.getOrDefault(e.getId(), 0);
            now.put(e.getId(), l.hurtTime);
            if (l.hurtTime > before && l.hurtTime >= l.hurtDuration - 1) {
                boolean self = e == mc.player;
                if (self ? particlesSelf.get() : particlesOthers.get()) {
                    Vec3 at = e.position().add(0, e.getBbHeight() * 0.6, 0);
                    for (int i = 0; i < (int) amount.get(); i++)
                        Fx.burst(Fx.named(particles.get(), palette.rgb(i / amount.get())), at, 1, e.getBbWidth(), 0.12);
                }
            }
        }
        lastHurt.clear();
        lastHurt.putAll(now);
    }

    /** Pulse strength 0..1 for the HUD, fading over half a second. */
    public float pulse() {
        if (!isEnabled() || !vignette.get()) return 0;
        float t = (System.currentTimeMillis() - pulseAt) / 550f;
        if (t >= 1) return 0;
        return pulsePower * (float) (strength.get() / 100) * (1 - t) * (1 - t);
    }

    public int pulseRgb() { return palette.rgb(); }

    @Override protected void onToggle(boolean enabled) { lastHurt.clear(); lastHealth = -1; }
}
