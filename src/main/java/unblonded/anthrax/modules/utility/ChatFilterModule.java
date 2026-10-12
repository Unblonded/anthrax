package unblonded.anthrax.modules.utility;

import net.minecraft.network.chat.Component;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** The three Packet Edit chat modes, applied to server/player/client chat messages. */
public final class ChatFilterModule extends FeatureModule {
    public static final ChatFilterModule INSTANCE = new ChatFilterModule();
    private final Setting.Mode mode = setting(Setting.Mode.standalone("Filter mode", "Mute", "Mute", "Contains", "Not Contain"));
    private final Setting.Text search = setting(Setting.Text.standalone("Message text", "", 512));
    private ChatFilterModule() { super("Chat Filter", "Mute all messages or filter by text", "Utility", false); }

    public static boolean shouldHide(Component message) {
        ChatFilterModule self = INSTANCE;
        if (!self.isEnabled()) return false;
        if (self.mode.get().equals("Mute")) return true;
        String query = self.search.get().trim().toLowerCase(java.util.Locale.ROOT);
        // An empty filter would otherwise hide (or show) everything; treat it as "no filter yet".
        if (query.isEmpty()) return false;
        boolean contains = message.getString().toLowerCase(java.util.Locale.ROOT).contains(query);
        return self.mode.get().equals("Contains") ? contains : !contains;
    }

    @Override public String hudInfo() { return mode.get(); }
}
