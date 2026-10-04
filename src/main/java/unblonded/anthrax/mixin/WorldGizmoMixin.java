package unblonded.anthrax.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void anthrax$submitEspGizmos(CallbackInfo ci) {
        BlockEspModule.renderGizmos((LevelRenderer) (Object) this);
        SeedRayModule.renderGizmos((LevelRenderer) (Object) this);
        WorldThemeModule.renderParticles((LevelRenderer) (Object) this);
    }
}
