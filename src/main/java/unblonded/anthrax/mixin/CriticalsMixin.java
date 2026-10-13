package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.combat.CriticalsModule;

/** Criticals: runs before every attack; jump modes hold the hit back until the hop comes down. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CriticalsMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void anthrax$criticals(Player player, Entity target, CallbackInfo ci) {
        CriticalsModule m = CriticalsModule.INSTANCE;
        if (!m.isEnabled() || player != Minecraft.getInstance().player) return;
        if (m.beforeAttack(Minecraft.getInstance(), target)) ci.cancel();
    }
}
