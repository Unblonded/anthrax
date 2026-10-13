package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.gui.PlayerIntelPanel;
import unblonded.anthrax.modules.Keybinds;
import unblonded.anthrax.modules.visual.PlayerListModule;

/** Module binds on mouse buttons (middle, side buttons) fire on the raw press, like key binds; HUD clicks in chat. */
@Mixin(MouseHandler.class)
public abstract class MouseButtonMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void anthrax$mouseBinds(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS || window != minecraft.getWindow().handle()) return;
        // Player List rows are clickable while chat is open (the HUD draws in real window pixels)
        if (minecraft.gui.screen() instanceof ChatScreen && info.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && PlayerListModule.INSTANCE.isEnabled()) {
            double gs = minecraft.getWindow().getGuiScale();
            MouseHandler mouse = (MouseHandler) (Object) this;
            if (PlayerIntelPanel.click(mouse.getScaledXPos(minecraft.getWindow()) * gs, mouse.getScaledYPos(minecraft.getWindow()) * gs)) {
                ci.cancel();
                return;
            }
        }
        if (minecraft.gui.screen() != null || minecraft.player == null) return;
        Keybinds.onKeyPress(Keybinds.MOUSE_BASE + info.button());
        // a bind opened a screen: don't pass this same click on into it
        if (minecraft.gui.screen() != null) ci.cancel();
    }
}
