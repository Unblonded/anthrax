package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import unblonded.anthrax.modules.visual.FreecamModule;

/**
 * The dark lower half of the sky is drawn when the player's eyes are below the horizon. With freecam deep
 * underground and the player on the surface, the bright (sunset) sky showed through every gap you see when
 * the camera is inside rock, so caves looked full of holes. Decide it from the camera instead.
 */
@Mixin(SkyRenderer.class)
public abstract class FreecamSkyMixin {
    @Redirect(method = "shouldRenderDarkDisc", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 anthrax$freecamEye(LocalPlayer player, float partial) {
        if (FreecamModule.INSTANCE.active()) return Minecraft.getInstance().gameRenderer.mainCamera().position();
        return player.getEyePosition(partial);
    }
}
