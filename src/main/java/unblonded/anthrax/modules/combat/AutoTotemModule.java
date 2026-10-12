package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Re-equips an offhand totem. Pops are detected exactly from the server's totem event, and the
 * swap is a single vanilla "swap with offhand" inventory click, so the cursor is never touched.
 */
public final class AutoTotemModule extends FeatureModule {
    private static final int OFFHAND_BUTTON = 40;

    private final Setting.Mode mode = setting(Setting.Mode.standalone("Mode", "Pop only", "Pop only", "Always"), "Pop only refills after a totem is used; Always keeps a totem in your offhand at all times.");
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ms)", 50, 0, 500, 10), "Pause before the swap, so it does not happen in the same instant as the pop.");
    private final Setting.Num humanity = setting(Setting.Num.standalone("Extra delay", 0, 0, 200, 10), "Random extra pause added on top of Delay.");
    private long equipAt;
    private boolean warnedEmpty;
    /** Set when a swap click was sent; if no totem arrives in time, the next attempt uses pickup clicks. */
    private long verifyBy;
    private boolean usePickup;

    public AutoTotemModule() {
        super("Auto Totem", "Put a fresh totem in your offhand the moment one pops", "Combat", false);
        TotemPops.listen((player, count) -> {
            if (isEnabled() && player == Minecraft.getInstance().player) schedule();
        });
    }

    private void schedule() {
        long jitter = humanity.get() <= 0 ? 0 : ThreadLocalRandom.current().nextLong((long) humanity.get() + 1);
        equipAt = System.currentTimeMillis() + (long) delay.get() + jitter;
    }

    @Override public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || player.isCreative() || player.isSpectator()) {
            equipAt = 0;
            return;
        }
        boolean hasTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        if (hasTotem) {
            equipAt = 0;
            verifyBy = 0;
            warnedEmpty = false;
            return;
        }
        // The last click did not produce a totem (e.g. the server ignored the swap): switch method and retry.
        if (verifyBy != 0 && System.currentTimeMillis() > verifyBy) {
            verifyBy = 0;
            if (!usePickup) usePickup = true;
            equipAt = System.currentTimeMillis();
        }
        if (verifyBy != 0) return;
        // "Always" keeps a totem in the offhand at all times; "Pop only" also refills an empty offhand.
        if (equipAt == 0 && (mode.get().equals("Always") || player.getOffhandItem().isEmpty())) schedule();
        if (equipAt == 0 || System.currentTimeMillis() < equipAt) return;

        // Clicks must target the menu the server thinks is open, and must not disturb a held item.
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) return;

        int slot = findTotemSlot(player);
        equipAt = 0;
        if (slot < 0) {
            if (!warnedEmpty) Notifications.error("Auto Totem", "No totems left in your inventory");
            warnedEmpty = true;
            return;
        }
        int menu = player.inventoryMenu.containerId;
        if (usePickup) {
            // Classic three-click move: pick up the totem, drop it in the offhand (slot 45), put back whatever was there.
            client.gameMode.handleContainerInput(menu, slot, 0, ContainerInput.PICKUP, player);
            client.gameMode.handleContainerInput(menu, 45, 0, ContainerInput.PICKUP, player);
            if (!player.inventoryMenu.getCarried().isEmpty())
                client.gameMode.handleContainerInput(menu, slot, 0, ContainerInput.PICKUP, player);
        } else {
            client.gameMode.handleContainerInput(menu, slot, OFFHAND_BUTTON, ContainerInput.SWAP, player);
        }
        // Allow for latency before deciding the click did not work.
        verifyBy = System.currentTimeMillis() + 400 + latency(client);
    }

    private static long latency(Minecraft client) {
        if (client.getConnection() == null || client.player == null) return 0;
        var info = client.getConnection().getPlayerInfo(client.player.getUUID());
        return info == null ? 0 : Math.min(1000, info.getLatency() * 2L);
    }

    /** Returns the inventory-menu slot index of a totem, preferring the main inventory over the hotbar. */
    private static int findTotemSlot(LocalPlayer player) {
        for (int i = 9; i < 36; i++) if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
        for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i + 36;
        return -1;
    }

    @Override public String hudInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) count++;
        return Integer.toString(count);
    }

    @Override protected void onToggle(boolean enabled) {
        equipAt = 0;
        verifyBy = 0;
        usePickup = false;
        warnedEmpty = false;
    }
}
