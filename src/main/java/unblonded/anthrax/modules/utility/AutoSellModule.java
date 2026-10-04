package unblonded.anthrax.modules.utility;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Packet Edit's configured `/ah sell <price>` hotbar cycle with confirmation GUI handling. */
public final class AutoSellModule extends FeatureModule {
    public static final AutoSellModule INSTANCE = new AutoSellModule();
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ms)", 300, 5, 500, 5));
    private final Setting.Text price = setting(Setting.Text.standalone("Price", "", 64));
    private final Setting.Num start = setting(Setting.Num.standalone("Start slot", 1, 1, 9, 1));
    private final Setting.Num end = setting(Setting.Num.standalone("Stop slot", 9, 1, 9, 1));
    private final Setting.Action trigger = setting(new Setting.Action("Trigger sell", this::start));
    private boolean running;
    private int slot;
    private int stage;
    private long stageAt;
    private int originalSlot = -1;
    private net.minecraft.client.player.LocalPlayer owner;
    private net.minecraft.client.multiplayer.ClientLevel world;

    private AutoSellModule() {
        super("Auto Sell", "List hotbar items through the configured auction-house sell flow", "Utility", false);
        ClientTickEvents.END_CLIENT_TICK.register(this::tickCycle);
    }

    private void start() {
        Minecraft mc = Minecraft.getInstance();
        String configuredPrice = price.get().trim();
        if (mc.player == null || !configuredPrice.matches("[0-9]+(?:\\.[0-9]{1,2})?")
                || configuredPrice.equals("0") || configuredPrice.equals("0.0") || configuredPrice.equals("0.00")) {
            message("Set a valid nonzero auction price first"); return;
        }
        if (start.get() > end.get()) { message("Start slot must be at or before stop slot"); return; }
        if (running) return;
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        owner = mc.player;
        world = mc.level;
        originalSlot = owner.getInventory().getSelectedSlot();
        slot = (int)start.get() - 1;
        stage = 0;
        running = true;
        stageAt = 0;
        message("Auto Sell started");
    }

    private void tickCycle(Minecraft mc) {
        if (!running) return;
        if (mc.player == null || mc.gameMode == null || mc.level == null || mc.player != owner || mc.level != world) {
            finish("Auto Sell stopped: player or world changed");
            return;
        }
        long now = System.currentTimeMillis();
        if (stageAt != 0 && now - stageAt < (long)delay.get()) return;
        if (stage == 0) {
            if (slot > (int)end.get() - 1) { finish("Auto Sell complete"); return; }
            var stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) { slot++; stageAt = now; return; }
            mc.player.getInventory().setSelectedSlot(slot);
            if (mc.getConnection() == null) { finish("Auto Sell stopped: disconnected"); return; }
            mc.getConnection().sendCommand("ah sell " + price.get().trim());
            stage = 1; stageAt = now;
        } else if (stage == 1) {
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen) {
                int confirm = findConfirm(screen);
                if (confirm >= 0) {
                    mc.gameMode.handleContainerInput(screen.getMenu().containerId, confirm, 0,
                            net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                    mc.player.closeContainer();
                    slot++; stage = 0; stageAt = now;
                } else if (now - stageAt > 3000) { message("No lime pane confirm slot found; skipping item"); mc.player.closeContainer(); slot++; stage = 0; stageAt = now; }
            } else if (now - stageAt > 5000) { message("Auction confirmation timed out; skipping item"); slot++; stage = 0; stageAt = now; }
        }
    }

    private static int findConfirm(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.getItem().isEmpty() && BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).getPath().equals("lime_stained_glass_pane")) return slot.index;
        }
        return -1;
    }

    private void finish(String text) {
        if (!running) return;
        Minecraft mc = Minecraft.getInstance();
        if (owner != null && mc.player == owner && originalSlot >= 0)
            owner.getInventory().setSelectedSlot(originalSlot);
        running = false; stage = 0; stageAt = 0; originalSlot = -1; owner = null; world = null;
        message(text);
    }
    private static void message(String text) { if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(Component.literal(text)); }
    @Override protected void onToggle(boolean enabled) { if (!enabled) finish("Auto Sell stopped"); }
}
