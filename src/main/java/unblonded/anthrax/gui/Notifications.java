package unblonded.anthrax.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import unblonded.anthrax.modules.client.HudModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-wide toast notifications. Shown by the HUD in game and by the menu while it is open.
 * Falls back to the action bar when HUD notifications are switched off. Client thread only.
 */
public final class Notifications {
    public enum Kind { INFO, SUCCESS, WARNING, ERROR }

    record Entry(String title, String message, Kind kind, long born) {}

    static final long LIFETIME_MS = 3300;
    private static final int MAX = 5;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private Notifications() {}

    public static void info(String title, String message) { push(title, message, Kind.INFO); }
    public static void success(String title, String message) { push(title, message, Kind.SUCCESS); }
    public static void warn(String title, String message) { push(title, message, Kind.WARNING); }
    public static void error(String title, String message) { push(title, message, Kind.ERROR); }

    public static void push(String title, String message, Kind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> push(title, message, kind));
            return;
        }
        boolean menuOpen = mc.gui.screen() instanceof AnthraxScreen;
        if (!menuOpen && !HudModule.INSTANCE.notifications()) {
            if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(title + ": " + message));
            return;
        }
        // Collapse an identical message that is still on screen instead of stacking duplicates.
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(e -> e.title().equals(title) && e.message().equals(message));
        ENTRIES.add(new Entry(title, message, kind, now));
        while (ENTRIES.size() > MAX) ENTRIES.removeFirst();
    }

    static List<Entry> active() {
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(e -> now - e.born() > LIFETIME_MS);
        return ENTRIES;
    }

    static int color(Kind kind, int accent) {
        return switch (kind) {
            case SUCCESS -> Theme.OK;
            case WARNING -> 0xFFFFC94A;
            case ERROR -> Theme.DANGER;
            default -> accent;
        };
    }
}
