package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.NoRenderModule;
import unblonded.anthrax.modules.visual.WorldThemeModule;

@Mixin(FogRenderer.class)
public abstract class FogRenderMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void anthrax$themeFog(Camera camera, int viewDistance, DeltaTracker deltaTracker, float partialTick,
                                  ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        WorldThemeModule.tintFog(cir.getReturnValue(), camera.getFluidInCamera());
    }

    @Inject(method = "getFogType", at = @At("HEAD"), cancellable = true)
    private void anthrax$noFog(Camera camera, CallbackInfoReturnable<FogType> cir) {
        if (NoRenderModule.isHidden(3)) cir.setReturnValue(FogType.NONE);
    }
}
