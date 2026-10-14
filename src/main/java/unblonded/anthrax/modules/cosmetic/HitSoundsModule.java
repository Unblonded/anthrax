package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import unblonded.anthrax.modules.Setting;

/** A satisfying sound when you hit something, and another when you kill it. Only you hear them. */
public final class HitSoundsModule extends CosmeticModule {
    public static final HitSoundsModule INSTANCE = new HitSoundsModule();
    private final Setting.Mode sound = setting(Setting.Mode.standalone("Hit sound", "Ding", Fx.SOUNDS), "");
    private final Setting.Num volume = setting(Setting.Num.standalone("Volume", 0.7, 0.05, 1, 0.05), "");
    private final Setting.Num pitch = setting(Setting.Num.standalone("Pitch", 1.0, 0.5, 2, 0.05), "");
    private final Setting.Bool randomPitch = setting(Setting.Bool.standalone("Vary pitch", true), "Each hit is slightly different.");
    private final Setting.Bool killSound = setting(Setting.Bool.standalone("Kill sound", true), "");
    private final Setting.Mode kill = setting(Setting.Mode.standalone("Kill sound type", "Level up", Fx.SOUNDS), "");

    private LivingEntity pending;
    private long pendingAt;

    private HitSoundsModule() { super("Hit Sounds", "Plays a sound when you hit something and another on kills. Only you hear them"); }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        float p = (float) (double) pitch.get() * (randomPitch.get() ? 0.92f + Fx.RND.nextFloat() * 0.16f : 1f);
        Fx.ui(Fx.namedSound(sound.get()), p, (float) (double) volume.get());
        if (target instanceof LivingEntity l) { pending = l; pendingAt = System.currentTimeMillis(); }
    }

    @Override public void tick(Minecraft mc) {
        if (pending == null) return;
        if (pending.isDeadOrDying()) {
            if (killSound.get()) Fx.ui(Fx.namedSound(kill.get()), 1f, (float) (double) volume.get());
            pending = null;
        } else if (System.currentTimeMillis() - pendingAt > 600) pending = null;
    }
}
