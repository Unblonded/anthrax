package unblonded.anthrax.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import unblonded.anthrax.CrystalAuraModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class CrystalPlaceMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void onUseItemOn(LocalPlayer player, InteractionHand hand,
                             BlockHitResult hitResult,
                             CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.END_CRYSTAL)) {
            CrystalAuraModule.INSTANCE.onCrystalPlace();
        }
    }
}