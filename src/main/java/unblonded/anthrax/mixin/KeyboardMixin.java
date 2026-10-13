package unblonded.anthrax.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.Keybinds;

/** Module keybinds fire on the raw key press so even very short taps are never missed. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Shadow @Final private Minecraft minecraft;
    /** Set when a bind opened a screen, so the character from that same key press is not typed into it. */
    @Unique private boolean anthrax$swallowChar;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void anthrax$moduleBinds(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS || window != minecraft.getWindow().handle()) return;
        anthrax$swallowChar = false;
        if (minecraft.gui.screen() != null || minecraft.player == null) return;
        Keybinds.onKeyPress(event.key());
        // A bind opened a screen: stop vanilla handing this same press to it, where the screen's own
        // "press again to close" check would shut it straight away.
        if (minecraft.gui.screen() != null) {
            anthrax$swallowChar = true;
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void anthrax$swallowBindChar(long window, CharacterEvent event, CallbackInfo ci) {
        if (!anthrax$swallowChar) return;
        anthrax$swallowChar = false;
        ci.cancel();
    }
}
