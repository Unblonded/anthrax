package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Re-equips an offhand totem after a likely pop, using normal inventory click packets. */
public final class AutoTotemModule extends FeatureModule {
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ms)", 50, 0, 500, 10));
    private final Setting.Num humanity = setting(Setting.Num.standalone("Extra delay", 0, 0, 200, 10));
    private boolean hadTotem;
    private float previousHealth;
    private long equipAt;
    private net.minecraft.client.player.LocalPlayer owner;
    private net.minecraft.client.multiplayer.ClientLevel world;

    public AutoTotemModule() {
        super("Auto Totem", "Re-equip an offhand totem after a pop", "Combat", false);
    }

    @Override public void tick(Minecraft client) {
        if (client.player == null || client.gameMode == null || client.player.isCreative() || client.player.isSpectator()) {
            hadTotem = false;
            equipAt = 0;
            return;
        }
        var player = client.player;
        if (owner != player || world != client.level) {
            hadTotem = false;
            equipAt = 0;
            owner = player;
            world = client.level;
        }
        boolean hasTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        float health = player.getHealth();
        long now = System.currentTimeMillis();

        if (hadTotem && !hasTotem && (previousHealth <= 2 || health < previousHealth - 6 || player.hurtTime > 0)) {
            equipAt = now + Math.round(delay.get() + Math.random() * humanity.get());
        }
        hadTotem = hasTotem;
        previousHealth = health;

        if (equipAt == 0 || now < equipAt || !player.getOffhandItem().isEmpty()) return;
        equipAt = 0;

        int inventoryIndex = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                inventoryIndex = i;
                break;
            }
        }
        if (inventoryIndex < 0) {
            player.sendSystemMessage(Component.literal("Auto Totem: no totem found"));
            return;
        }

        int slot = inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
        int menu = player.inventoryMenu.containerId;
        client.gameMode.handleContainerInput(menu, slot, 0, ContainerInput.PICKUP, player);
        client.gameMode.handleContainerInput(menu, 45, 0, ContainerInput.PICKUP, player);
        client.gameMode.handleContainerInput(menu, slot, 0, ContainerInput.PICKUP, player);
    }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled) {
            hadTotem = false;
            equipAt = 0;
            owner = null;
            world = null;
        }
    }
}
