package unblonded.anthrax.modules.cosmetic;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;

/** A burst of particles on whatever you hit. */
public final class HitParticlesModule extends CosmeticModule {
    public static final HitParticlesModule INSTANCE = new HitParticlesModule();
    private final Setting.Mode particles = setting(Setting.Mode.standalone("Particles", "Magic", Fx.PARTICLES), "");
    private final Palette palette = palette("Theme");
    private final Setting.Num amount = setting(Setting.Num.standalone("Amount", 14, 2, 60, 1), "");
    private final Setting.Num spread = setting(Setting.Num.standalone("Speed", 0.25, 0.02, 1, 0.01), "How fast the particles fly out.");

    private HitParticlesModule() { super("Hit Particles", "Bursts particles of your choice on everything you hit"); }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        int n = (int) amount.get();
        for (int i = 0; i < n; i++) Fx.burst(Fx.named(particles.get(), palette.rgb(i / (double) n)), at, 1, target.getBbWidth(), spread.get());
    }
}
