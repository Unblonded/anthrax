package unblonded.anthrax.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.FreecamModule;

/** While freecam is on, movement keys move the camera, not the player. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void anthrax$freecamFreeze(CallbackInfo ci) {
        if (!FreecamModule.INSTANCE.active()) return;
        keyPresses = Input.EMPTY;
        moveVector = Vec2.ZERO;
    }
}
