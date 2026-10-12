package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.Keybinds;

/** Module binds on mouse buttons (middle, side buttons) fire on the raw press, like key binds. */
@Mixin(MouseHandler.class)
public abstract class MouseButtonMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void anthrax$mouseBinds(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS || window != minecraft.getWindow().handle()) return;
        if (minecraft.gui.screen() != null || minecraft.player == null) return;
        Keybinds.onKeyPress(Keybinds.MOUSE_BASE + info.button());
    }
}
