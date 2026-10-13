package unblonded.anthrax.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Speed Mine: the delay between blocks and the current break progress. */
@Mixin(MultiPlayerGameMode.class)
public interface GameModeAccessor {
    @Accessor("destroyDelay") int anthrax$getDestroyDelay();
    @Accessor("destroyDelay") void anthrax$setDestroyDelay(int value);
    @Accessor("destroyProgress") float anthrax$getDestroyProgress();
    @Accessor("destroyProgress") void anthrax$setDestroyProgress(float value);
}
