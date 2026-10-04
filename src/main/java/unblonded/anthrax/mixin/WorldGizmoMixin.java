package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.BlockEspModule;
import unblonded.anthrax.modules.mining.SeedRayModule;

@Mixin(LevelRenderer.class)
public abstract class WorldGizmoMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void anthrax$submitEspGizmos(CallbackInfo ci) {
        BlockEspModule.renderGizmos((LevelRenderer) (Object) this);
        SeedRayModule.renderGizmos((LevelRenderer) (Object) this);
    }
}
