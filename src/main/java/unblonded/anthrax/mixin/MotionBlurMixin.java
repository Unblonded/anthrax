package unblonded.anthrax.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.Safe;
import unblonded.anthrax.modules.visual.MotionBlurModule;

/** Motion Blur: run the blend pass on the world image right before the hand is drawn. */
@Mixin(GameRenderer.class)
public abstract class MotionBlurMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderTarget mainRenderTarget;
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V"))
    private void anthrax$motionBlur(DeltaTracker deltaTracker, CallbackInfo ci) {
        MotionBlurModule m = MotionBlurModule.INSTANCE;
        if (!m.isEnabled()) return;
        Safe.run(m, () -> {
            var chain = minecraft.getShaderManager().getPostChain(m.effect(), LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) chain.process(mainRenderTarget, resourcePool);
        });
    }
}
