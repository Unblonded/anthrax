package unblonded.anthrax.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import unblonded.anthrax.modules.Safe;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.FeatureManager;
import unblonded.anthrax.modules.visual.BlockEspModule;
import unblonded.anthrax.modules.visual.WorldThemeModule;
import unblonded.anthrax.modules.mining.SeedRayModule;

@Mixin(LevelRenderer.class)
public abstract class WorldGizmoMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void anthrax$themeSky(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
                                  CameraRenderState cameraState, Matrix4fc projection, GpuBufferSlice fogBuffer,
                                  Vector4f skyColor, boolean renderEntityOutline, CallbackInfo ci) {
        WorldThemeModule.tintSky(skyColor);
        unblonded.anthrax.modules.cosmetic.SkyboxModule.INSTANCE.tintSky(skyColor);
        unblonded.anthrax.render.WorldProjection.capture(cameraState);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void anthrax$submitEspGizmos(CallbackInfo ci) {
        LevelRenderer self = (LevelRenderer) (Object) this;
        FeatureManager.frame(net.minecraft.client.Minecraft.getInstance());
        // each pass is guarded: a failing module is switched off instead of crashing the frame
        Safe.run(BlockEspModule.INSTANCE, () -> BlockEspModule.renderGizmos(self));
        Safe.run(SeedRayModule.INSTANCE, () -> SeedRayModule.renderGizmos(self));
        Safe.run(unblonded.anthrax.modules.world.BaseFinderModule.INSTANCE, () -> unblonded.anthrax.modules.world.BaseFinderModule.renderGizmos(self));
        Safe.run(unblonded.anthrax.modules.world.WaypointsModule.INSTANCE, () -> unblonded.anthrax.modules.world.WaypointsModule.renderGizmos(self));
        Safe.run("World overlays", () -> unblonded.anthrax.modules.visual.WorldOverlays.render(self));
        Safe.run(WorldThemeModule.INSTANCE, () -> WorldThemeModule.renderParticles(self));
    }
}
