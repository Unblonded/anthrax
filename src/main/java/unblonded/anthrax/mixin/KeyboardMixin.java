package unblonded.anthrax.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.Keybinds;

/** Module keybinds fire on the raw key press so even very short taps are never missed. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void anthrax$moduleBinds(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS || window != minecraft.getWindow().handle()) return;
        if (minecraft.gui.screen() != null || minecraft.player == null) return;
        Keybinds.onKeyPress(event.key());
    }
}
