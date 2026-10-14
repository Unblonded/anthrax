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
import unblonded.anthrax.modules.cosmetic.RealisticWorldModule;

/** Realistic World: grade the world image right before the hand is drawn (after Motion Blur). */
@Mixin(value = GameRenderer.class, priority = 1100)
public abstract class PostGradeMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderTarget mainRenderTarget;
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V"))
    private void anthrax$grade(DeltaTracker deltaTracker, CallbackInfo ci) {
        RealisticWorldModule m = RealisticWorldModule.INSTANCE;
        if (!m.isEnabled()) return;
        Safe.run(m, () -> {
            var chain = minecraft.getShaderManager().getPostChain(m.effect(), LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) chain.process(mainRenderTarget, resourcePool);
        });
    }
}
