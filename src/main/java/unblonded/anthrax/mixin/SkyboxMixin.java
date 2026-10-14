package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.cosmetic.SkyboxModule;

/** Skybox: sky colours and stars, and hiding the sun and moon. */
@Mixin(SkyRenderer.class)
public abstract class SkyboxMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void anthrax$sky(ClientLevel level, float partial, Camera camera, SkyRenderState state, CallbackInfo ci) {
        SkyboxModule.INSTANCE.sky(state);
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void anthrax$noSun(float rainBrightness, PoseStack pose, CallbackInfo ci) {
        if (SkyboxModule.INSTANCE.hideSun()) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void anthrax$noMoon(MoonPhase phase, float rainBrightness, PoseStack pose, CallbackInfo ci) {
        if (SkyboxModule.INSTANCE.hideSun()) ci.cancel();
    }
}
