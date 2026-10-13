package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.ChatAnimationsModule;

/** Chat Animations: the chat starts one line lower when a message arrives and eases up into place. */
@Mixin(ChatComponent.class)
public abstract class ChatAnimationMixin {
    @Inject(method = "addMessageToDisplayQueue", at = @At("TAIL"))
    private void anthrax$messageArrived(GuiMessage message, CallbackInfo ci) {
        ChatAnimationsModule.INSTANCE.onMessage();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
    private void anthrax$slideStart(GuiGraphicsExtractor g, Font font, int a, int b, int c, ChatComponent.DisplayMode mode, boolean d, CallbackInfo ci) {
        float k = ChatAnimationsModule.INSTANCE.remaining();
        g.pose().pushMatrix();
        if (k > 0) {
            double scale = Minecraft.getInstance().options.chatScale().get();
            double spacing = Minecraft.getInstance().options.chatLineSpacing().get();
            g.pose().translate(0, (float) (k * 9.0 * (1.0 + spacing) * scale));
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("RETURN"))
    private void anthrax$slideEnd(GuiGraphicsExtractor g, Font font, int a, int b, int c, ChatComponent.DisplayMode mode, boolean d, CallbackInfo ci) {
        g.pose().popMatrix();
    }
}
