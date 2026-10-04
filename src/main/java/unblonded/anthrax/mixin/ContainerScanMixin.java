package unblonded.anthrax.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.utility.StorageScanModule;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScanMixin {
    @Accessor("leftPos") protected abstract int anthrax$getLeftPos();
    @Accessor("topPos") protected abstract int anthrax$getTopPos();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void anthrax$highlightSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        int left = anthrax$getLeftPos(), top = anthrax$getTopPos();
        for (Slot slot : screen.getMenu().slots) {
            if (StorageScanModule.matches(slot.getItem()))
                graphics.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, StorageScanModule.highlightColor());
        }
    }
}
