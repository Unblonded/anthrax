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
        // No Render: push the fog out of view instead of faking the fog type (the game needs a real type
        // to pick a fog colour, and crashes without one)
        FogData fog = cir.getReturnValue();
        if (NoRenderModule.isHidden(3) && fog != null) {
            fog.environmentalStart = fog.renderDistanceStart = 1.0e6f;
            fog.environmentalEnd = fog.renderDistanceEnd = 2.0e6f;
        }
        if (camera.getFluidInCamera() == FogType.NONE) unblonded.anthrax.modules.cosmetic.SkyboxModule.INSTANCE.tintFog(cir.getReturnValue());
    }

}
