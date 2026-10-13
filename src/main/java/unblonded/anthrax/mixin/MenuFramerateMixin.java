package unblonded.anthrax.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.gui.AnthraxTitleScreen;

/**
 * Vanilla caps every menu outside a world at 60 FPS. The Anthrax title screen is all animation (spinning
 * panorama, sliding glass, hover shimmer), so it runs at the player's own frame limit instead; AFK and
 * minimised throttling still apply.
 */
@Mixin(FramerateLimitTracker.class)
public abstract class MenuFramerateMixin {
    @Inject(method = "getThrottleReason", at = @At("RETURN"), cancellable = true)
    private void anthrax$uncapTitle(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
        if (cir.getReturnValue() == FramerateLimitTracker.FramerateThrottleReason.OUT_OF_LEVEL_MENU
                && Minecraft.getInstance().gui.screen() instanceof AnthraxTitleScreen)
            cir.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
    }
}
