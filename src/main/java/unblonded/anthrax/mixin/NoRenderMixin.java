package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.NoRenderModule;

@Mixin(ScreenEffectRenderer.class)
public abstract class NoRenderMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void anthrax$fire(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NoRenderModule.isHidden(0)) ci.cancel();
    }
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void anthrax$water(Minecraft client, PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
        if (NoRenderModule.isHidden(1)) ci.cancel();
    }
    @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
    private static void anthrax$suffocation(Player player, CallbackInfoReturnable<BlockState> cir) {
        if (NoRenderModule.isHidden(2)) cir.setReturnValue(null);
    }
}
