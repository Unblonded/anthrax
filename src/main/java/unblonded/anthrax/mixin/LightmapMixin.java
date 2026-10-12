package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.FullbrightModule;

/** Fullbright: drive the night-vision term of the lightmap without the potion effect. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void anthrax$fullbright(LightmapRenderState state, float partial, CallbackInfo ci) {
        if (!FullbrightModule.INSTANCE.isEnabled()) return;
        state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, FullbrightModule.INSTANCE.strength());
        state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
        state.needsUpdate = true;
    }
}
