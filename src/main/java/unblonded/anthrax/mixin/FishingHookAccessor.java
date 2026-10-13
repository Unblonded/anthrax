package unblonded.anthrax.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the synced "fish is biting" flag so Auto Fish can reel in at the right moment. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("DATA_BITING")
    static EntityDataAccessor<Boolean> anthrax$biting() { throw new AssertionError(); }
}
