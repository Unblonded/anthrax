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

/** Rejoins the last server after a kick or disconnect, after a countdown. */
public final class AutoReconnectModule extends FeatureModule {
    public static final AutoReconnectModule INSTANCE = new AutoReconnectModule();
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (s)", 5, 1, 60, 1), "Countdown on the disconnect screen before rejoining.");
    private net.minecraft.client.multiplayer.ServerData last;
    private long disconnectedAt;
    private boolean notified;

    private AutoReconnectModule() { super("Auto Reconnect", "Rejoins the server after a kick or disconnect", "Player", false); }

    @Override public void tick(Minecraft mc) {
        if (mc.getCurrentServer() != null && mc.player != null) { last = mc.getCurrentServer(); disconnectedAt = 0; notified = false; }
    }

    /** Called every client tick even without a player (see Anthrax tick hook). */
    public void screenTick(Minecraft mc) {
        if (!isEnabled() || last == null || !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen)) { disconnectedAt = 0; return; }
        long now = System.currentTimeMillis();
        if (disconnectedAt == 0) { disconnectedAt = now; notified = false; }
        long left = (long) (delay.get() * 1000) - (now - disconnectedAt);
        if (!notified) { Notifications.info("Auto Reconnect", "Rejoining " + last.name + " in " + (int) delay.get() + "s (turn the module off to cancel)"); notified = true; }
        if (left > 0) return;
        disconnectedAt = 0;
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), mc,
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(last.ip), last, false, null);
    }
}
