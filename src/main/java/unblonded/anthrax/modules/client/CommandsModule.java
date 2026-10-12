package unblonded.anthrax.modules.client;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Holds the chat command prefix; see {@link Commands}. */
public final class CommandsModule extends FeatureModule {
    public static final CommandsModule INSTANCE = new CommandsModule();
    private final Setting.Text prefix = setting(Setting.Text.standalone("Command prefix", ".", 3));

    private CommandsModule() {
        super("Commands", "Chat commands such as .toggle, .bind, .friend, .wp and .profile (type .help)", "Client", true);
    }

    @Override protected boolean hiddenFromList() { return true; }

    public String prefix() { return isEnabled() ? prefix.get() : ""; }
    void setPrefix(String p) { prefix.set(p); }
}
