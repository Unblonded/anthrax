package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.combat.AutoWeaponModule;
import unblonded.anthrax.modules.combat.CombatUtil;

/** Every attack (by hand or by a module): pick the best weapon first, and remember the target. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CombatHooksMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void anthrax$beforeAttack(Player player, Entity target, CallbackInfo ci) {
        AutoWeaponModule.INSTANCE.onAttack(Minecraft.getInstance(), target);
        CombatUtil.markTarget(target);
    }
}
