package unblonded.anthrax.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import unblonded.anthrax.gui.AnthraxTitleScreen;

/** Swaps the vanilla title screen for the Anthrax one wherever the game opens it (startup, disconnect, quit to title). */
@Mixin(Gui.class)
public abstract class TitleScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen anthrax$customTitle(Screen screen) {
        return AnthraxTitleScreen.replace(screen);
    }
}
