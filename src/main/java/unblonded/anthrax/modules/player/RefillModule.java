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

/** Keeps pearls, food and splash potions in your hotbar by pulling them from your inventory. */
public final class RefillModule extends FeatureModule {
    public static final RefillModule INSTANCE = new RefillModule();
    private final Setting.Bool pearls = setting(Setting.Bool.standalone("Ender pearls", true), "");
    private final Setting.Bool food = setting(Setting.Bool.standalone("Food", true), "");
    private final Setting.Bool potions = setting(Setting.Bool.standalone("Splash potions", true), "");
    private final Setting.Bool totems = setting(Setting.Bool.standalone("Totems", false), "");
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ms)", 150, 0, 1000, 25), "Pause between moves.");
    private long next;

    private RefillModule() { super("Refill", "Refills pearls, potions and food from your inventory into empty hotbar slots", "Player", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || System.currentTimeMillis() < next) return;
        if (p.containerMenu != p.inventoryMenu || !p.inventoryMenu.getCarried().isEmpty()) return;
        java.util.List<java.util.function.Predicate<ItemStack>> kinds = new java.util.ArrayList<>();
        if (pearls.get()) kinds.add(s -> s.is(Items.ENDER_PEARL));
        if (food.get()) kinds.add(s -> s.has(net.minecraft.core.component.DataComponents.FOOD) && !s.is(Items.ROTTEN_FLESH) && !s.is(Items.SPIDER_EYE));
        if (potions.get()) kinds.add(s -> s.is(Items.SPLASH_POTION));
        if (totems.get()) kinds.add(s -> s.is(Items.TOTEM_OF_UNDYING));
        for (var kind : kinds) {
            boolean inHotbar = false;
            for (int i = 0; i < 9; i++) if (kind.test(p.getInventory().getItem(i))) { inHotbar = true; break; }
            if (inHotbar) continue;
            int from = -1, to = -1;
            for (int i = 9; i < 36; i++) if (kind.test(p.getInventory().getItem(i))) { from = i; break; }
            for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).isEmpty()) { to = i; break; }
            if (from < 0 || to < 0) continue;
            mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, from, to, ContainerInput.SWAP, p);
            next = System.currentTimeMillis() + (long) delay.get();
            return;
        }
    }
}
