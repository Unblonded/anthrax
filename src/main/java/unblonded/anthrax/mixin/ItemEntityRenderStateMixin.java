package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import unblonded.anthrax.render.ItemPhysicsState;
import org.spongepowered.asm.mixin.Unique;

/** Item Physics: adds the on-ground flag to the item render state. */
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsState {
    @Unique private boolean anthrax$onGround;

    @Override public boolean anthrax$onGround() { return anthrax$onGround; }
    @Override public void anthrax$setOnGround(boolean value) { anthrax$onGround = value; }
}
