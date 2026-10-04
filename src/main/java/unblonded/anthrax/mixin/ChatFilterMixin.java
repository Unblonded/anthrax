package unblonded.anthrax.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.utility.ChatFilterModule;

@Mixin(ChatComponent.class)
public abstract class ChatFilterMixin {
    @Inject(method = "addPlayerMessage", at = @At("HEAD"), cancellable = true)
    private void anthrax$filterPlayer(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (ChatFilterModule.shouldHide(message)) ci.cancel();
    }

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
    private void anthrax$filterServer(Component message, CallbackInfo ci) {
        if (ChatFilterModule.shouldHide(message)) ci.cancel();
    }

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
    private void anthrax$filterClient(Component message, CallbackInfo ci) {
        if (ChatFilterModule.shouldHide(message)) ci.cancel();
    }
}
