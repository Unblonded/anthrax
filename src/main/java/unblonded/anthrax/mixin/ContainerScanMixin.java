package unblonded.anthrax.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.gui.ScanBox;
import unblonded.anthrax.modules.utility.StorageScanModule;

/**
 * Storage Scan in container screens: the slot highlight (drawn as each slot is drawn, behind the item)
 * and the search box in the bottom-right corner, which gets first pick of mouse and keyboard input.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScanMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void anthrax$highlightSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!StorageScanModule.matches(slot.getItem())) return;
        int c = StorageScanModule.highlightColor(), edge = (c & 0x00FFFFFF) | 0xE0000000;
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, c);
        // crisp 1px frame so matches stand out even on busy item textures
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, edge);
        graphics.fill(slot.x, slot.y + 15, slot.x + 16, slot.y + 16, edge);
        graphics.fill(slot.x, slot.y + 1, slot.x + 1, slot.y + 15, edge);
        graphics.fill(slot.x + 15, slot.y + 1, slot.x + 16, slot.y + 15, edge);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void anthrax$scanClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ScanBox.mouseClicked(event.x(), event.y(), event.button())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void anthrax$scanDrag(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (ScanBox.mouseDragged(event.x(), event.y())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void anthrax$scanRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ScanBox.mouseReleased()) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void anthrax$scanKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ScanBox.keyPressed(event.key(), event.modifiers())) cir.setReturnValue(true);
    }

    /** Container screens do not override charTyped; this adds it so the box can receive typed text. */
    public boolean charTyped(CharacterEvent event) {
        if (ScanBox.charTyped(event.codepointAsString())) return true;
        GuiEventListener focused = ((AbstractContainerScreen<?>) (Object) this).getFocused();
        return focused != null && focused.charTyped(event);
    }
}
