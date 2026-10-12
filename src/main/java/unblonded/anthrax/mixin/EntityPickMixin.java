package unblonded.anthrax.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import unblonded.anthrax.modules.utility.InteractionCancelerModule;

import java.util.function.Predicate;

/** Lets the crosshair pass through entities chosen in Interaction Canceler (attack and use alike). */
@Mixin(LocalPlayer.class)
public abstract class EntityPickMixin {
    @Redirect(method = "raycastHitResult", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lnet/minecraft/world/entity/EntitySelector;CAN_BE_PICKED:Ljava/util/function/Predicate;"))
    private Predicate<Entity> anthrax$filterAttackRange() {
        return InteractionCancelerModule.pickFilter();
    }

    @Redirect(method = "pick", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lnet/minecraft/world/entity/EntitySelector;CAN_BE_PICKED:Ljava/util/function/Predicate;"))
    private static Predicate<Entity> anthrax$filterPick() {
        return InteractionCancelerModule.pickFilter();
    }
}
