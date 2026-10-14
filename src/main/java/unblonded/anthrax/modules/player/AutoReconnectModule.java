package unblonded.anthrax.modules.player;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Rejoins the last server after a kick or disconnect, after a countdown. The disconnect screen gets a
 * "Reconnect now" button (even with the module off) and a button that shows the countdown and cancels it.
 */
public final class AutoReconnectModule extends FeatureModule {
    public static final AutoReconnectModule INSTANCE = new AutoReconnectModule();
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (s)", 5, 1, 60, 1), "Countdown on the disconnect screen before rejoining.");
    private final Setting.Bool buttons = setting(Setting.Bool.standalone("Disconnect screen buttons", true), "Reconnect now and Cancel buttons on the disconnect screen. Reconnect now works even with the module off.");
    private ServerData last;
    private long disconnectedAt;
    private boolean cancelled;
    private Button countdownButton;

    private AutoReconnectModule() { super("Auto Reconnect", "Rejoins the server after a kick or disconnect, with Reconnect now and Cancel buttons on the disconnect screen", "Player", false); }

    /** Adds the buttons whenever a disconnect screen is built (also after a resize). Registered once at start-up. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof DisconnectedScreen) || INSTANCE.last == null || !INSTANCE.buttons.get()) return;
            int bottom = 0;
            for (AbstractWidget b : Screens.getWidgets(screen)) bottom = Math.max(bottom, b.getY() + b.getHeight());
            int x = w / 2 - 100, y = Math.min(h - 48, bottom + 6);
            Screens.getWidgets(screen).add(Button.builder(Component.literal("Reconnect now  (" + INSTANCE.last.name + ")"), b -> INSTANCE.reconnect(client))
                    .bounds(x, y, 200, 20).build());
            INSTANCE.countdownButton = Button.builder(Component.empty(), b -> INSTANCE.toggleCountdown()).bounds(x, y + 24, 200, 20).build();
            Screens.getWidgets(screen).add(INSTANCE.countdownButton);
            INSTANCE.refreshLabel(System.currentTimeMillis());
        });
    }

    /** Called every client tick even without a player (see Anthrax tick hook). */
    public void screenTick(Minecraft mc) {
        // remembered even with the module off, so Reconnect now always knows the server
        if (mc.player != null && mc.getCurrentServer() != null) last = mc.getCurrentServer();
        if (last == null || !(mc.gui.screen() instanceof DisconnectedScreen)) { disconnectedAt = 0; cancelled = false; countdownButton = null; return; }
        long now = System.currentTimeMillis();
        if (disconnectedAt == 0) disconnectedAt = now;
        refreshLabel(now);
        if (!isEnabled() || cancelled) return;
        if ((long) (delay.get() * 1000) - (now - disconnectedAt) > 0) return;
        reconnect(mc);
    }

    private void reconnect(Minecraft mc) {
        if (last == null) return;
        disconnectedAt = 0;
        cancelled = false;
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(last.ip), last, false, null);
    }

    /** Cancels a running countdown, starts it again, or turns the module on when it is off. */
    private void toggleCountdown() {
        if (!isEnabled()) { setEnabled(true); cancelled = false; disconnectedAt = System.currentTimeMillis(); }
        else if (cancelled) { cancelled = false; disconnectedAt = System.currentTimeMillis(); }
        else cancelled = true;
        refreshLabel(System.currentTimeMillis());
    }

    private void refreshLabel(long now) {
        Button b = countdownButton;
        if (b == null) return;
        String text;
        if (!isEnabled()) text = "Auto reconnect: off (turn on)";
        else if (cancelled) text = "Auto reconnect cancelled (restart)";
        else {
            long base = disconnectedAt == 0 ? now : disconnectedAt;
            long left = Math.max(0, (long) (delay.get() * 1000) - (now - base));
            text = "Cancel  (rejoining in " + (left + 999) / 1000 + "s)";
        }
        if (!b.getMessage().getString().equals(text)) b.setMessage(Component.literal(text));
    }
}
