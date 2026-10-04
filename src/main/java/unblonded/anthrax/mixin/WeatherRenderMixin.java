package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.NoRenderModule;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherRenderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void anthrax$weather(Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
        if (NoRenderModule.isHidden(4)) ci.cancel();
    }
}
