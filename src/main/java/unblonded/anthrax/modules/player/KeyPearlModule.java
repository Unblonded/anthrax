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

/** Throws an ender pearl from your hotbar where you look, then switches back. Bind it to a key. */
public final class KeyPearlModule extends FeatureModule {
    public static final KeyPearlModule INSTANCE = new KeyPearlModule();
    private final Setting.Bool inventory = setting(Setting.Bool.standalone("Use inventory pearls", false), "If none are in the hotbar, borrows one from your inventory.");

    private KeyPearlModule() { super("Key Pearl", "Throws an ender pearl from your hotbar in your look direction and switches back. Bind it to a key", "Player", false); }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        try {
            if (p == null || mc.gameMode == null) return;
            if (p.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) { Notifications.info("Key Pearl", "Pearl on cooldown"); return; }
            int slot = -1;
            for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(Items.ENDER_PEARL)) { slot = i; break; }
            int prev = p.getInventory().getSelectedSlot();
            if (slot < 0 && inventory.get()) {
                for (int i = 9; i < 36; i++) if (p.getInventory().getItem(i).is(Items.ENDER_PEARL)) {
                    mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, i, prev, ContainerInput.SWAP, p);
                    mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                    mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, i, prev, ContainerInput.SWAP, p);
                    return;
                }
            }
            if (slot < 0) { Notifications.warn("Key Pearl", "No ender pearls in your hotbar"); return; }
            p.getInventory().setSelectedSlot(slot);
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            p.swing(InteractionHand.MAIN_HAND);
            p.getInventory().setSelectedSlot(prev);
        } finally {
            mc.execute(() -> setEnabled(false));
        }
    }

    @Override protected boolean hiddenFromList() { return true; }
}
