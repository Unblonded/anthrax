package unblonded.anthrax.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.NoRenderModule;

@Mixin(Hud.class)
public abstract class NoVignetteMixin {
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void anthrax$vignette(GuiGraphicsExtractor graphics, Entity entity, CallbackInfo ci) {
        if (NoRenderModule.isHidden(5)) ci.cancel();
    }
}
