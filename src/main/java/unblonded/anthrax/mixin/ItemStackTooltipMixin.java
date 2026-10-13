package unblonded.anthrax.mixin;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.visual.BetterTooltipsModule;

import java.util.Optional;

/** Better Tooltips: shulker box and ender chest contents as a picture under the tooltip. */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void anthrax$preview(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (cir.getReturnValue().isPresent()) return;
        var p = BetterTooltipsModule.INSTANCE.preview((ItemStack) (Object) this);
        if (p.isPresent()) cir.setReturnValue(p);
    }
}
