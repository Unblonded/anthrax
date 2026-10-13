package unblonded.anthrax.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Takes everything out of chests, barrels and shulker boxes when you open them. */
public final class ChestStealerModule extends FeatureModule {
    public static final ChestStealerModule INSTANCE = new ChestStealerModule();
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ms)", 60, 0, 500, 10), "Pause between taking each stack.");
    private final Setting.Bool close = setting(Setting.Bool.standalone("Close when empty", true), "Closes the container once it is emptied.");
    private long next;

    private ChestStealerModule() { super("Chest Stealer", "Takes everything out of chests when you open them", "Player", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) return;
        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return;
        var menu = p.containerMenu;
        if (!(menu instanceof net.minecraft.world.inventory.ChestMenu || menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu)) return;
        long now = System.currentTimeMillis();
        if (now < next) return;
        for (var slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory || !slot.hasItem()) continue;
            mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, p);
            next = now + (long) delay.get();
            if (delay.get() > 0) return;
        }
        if (close.get()) p.closeContainer();
    }
}
