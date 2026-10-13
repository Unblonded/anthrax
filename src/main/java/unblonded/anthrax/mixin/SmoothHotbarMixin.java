package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import unblonded.anthrax.modules.visual.SmoothHotbarModule;

/** Smooth Hotbar: the selection frame (second sprite drawn in the hotbar) slides between slots. */
@Mixin(Hud.class)
public abstract class SmoothHotbarMixin {
    @ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
            ordinal = 1), index = 2)
    private int anthrax$slideSelection(int x) {
        var mod = SmoothHotbarModule.INSTANCE;
        var p = Minecraft.getInstance().player;
        if (!mod.isEnabled() || p == null) return x;
        int selected = p.getInventory().getSelectedSlot();
        return x - selected * 20 + Math.round(mod.slot(selected) * 20);
    }
}
