package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;

/** Particles under your feet while you are in the air: a puff on take-off and a trail while you jump or fall. */
public final class AirEffectsModule extends CosmeticModule {
    public static final AirEffectsModule INSTANCE = new AirEffectsModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Particles", "Clouds", Fx.PARTICLES), "What trails under you.");
    private final Palette palette = palette("Theme");
    private final Setting.Num amount = setting(Setting.Num.standalone("Amount", 2, 1, 8, 1), "Particles per tick while airborne.");
    private final Setting.Bool takeoff = setting(Setting.Bool.standalone("Take-off puff", true), "A ring of particles where you leave the ground.");
    private final Setting.Bool landing = setting(Setting.Bool.standalone("Landing puff", true), "A ring of particles where you land.");
    private final Setting.Bool elytra = setting(Setting.Bool.standalone("While gliding", false), "Keep trailing while flying with an elytra.");
    private boolean wasGround = true;

    private AirEffectsModule() { super("Air Effects", "Particles trail under your feet while you jump or fall, with puffs on take-off and landing"); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        boolean ground = p.onGround() || p.isInWater() || p.onClimbable() || p.isPassenger();
        Vec3 feet = p.position();
        if (wasGround && !ground && takeoff.get()) ring(feet, 10, 0.12);
        if (!wasGround && ground && landing.get()) ring(feet, 14, 0.18);
        wasGround = ground;
        if (ground || (p.isFallFlying() && !elytra.get()) || p.getAbilities().flying) return;
        for (int i = 0; i < (int) amount.get(); i++) {
            double ox = (Fx.RND.nextDouble() - 0.5) * 0.5, oz = (Fx.RND.nextDouble() - 0.5) * 0.5;
            Fx.particle(Fx.named(style.get(), palette.rgb(Fx.RND.nextDouble())), feet.add(ox, 0.05, oz), 0, -0.02, 0);
        }
    }

    private void ring(Vec3 at, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Fx.particle(Fx.named(style.get(), palette.rgb((double) i / n)), at.add(Math.cos(a) * 0.3, 0.05, Math.sin(a) * 0.3),
                    Math.cos(a) * speed, 0.01, Math.sin(a) * speed);
        }
    }
}
