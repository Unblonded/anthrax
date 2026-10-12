package unblonded.anthrax.modules.client;

import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.ModuleConfig;
import unblonded.anthrax.modules.Setting;

import java.util.List;

/** Named config profiles: every module, setting, keybind and menu preference in one file. */
public final class ProfilesModule extends FeatureModule {
    public static final ProfilesModule INSTANCE = new ProfilesModule();

    private final Setting.Text name = setting(Setting.Text.standalone("Profile name", "default", 32));
    private final Setting.Action next = setting(new Setting.Action("Next saved profile", this::next));
    private final Setting.Action save = setting(new Setting.Action("Save profile", () -> {
        if (ModuleConfig.saveProfile(name.get())) Notifications.success("Profiles", "Saved " + name.get());
        else Notifications.error("Profiles", "Could not save " + name.get());
    }));
    private final Setting.Action load = setting(new Setting.Action("Load profile", () -> {
        String target = name.get();
        if (ModuleConfig.loadProfile(target)) Notifications.success("Profiles", "Loaded " + target);
        else Notifications.error("Profiles", "No profile named " + target);
    }));
    private final Setting.Action delete = setting(new Setting.Action("Delete profile", () -> {
        if (ModuleConfig.deleteProfile(name.get())) Notifications.info("Profiles", "Deleted " + name.get());
        else Notifications.error("Profiles", "No profile named " + name.get());
    }));

    private ProfilesModule() {
        super("Config Profiles", "Save and switch complete setups (modules, settings, binds, menu)", "Client", true);
    }

    @Override protected boolean hiddenFromList() { return true; }

    private void next() {
        List<String> all = ModuleConfig.profiles();
        if (all.isEmpty()) { Notifications.warn("Profiles", "No saved profiles yet"); return; }
        int i = (all.indexOf(name.get()) + 1) % all.size();
        name.set(all.get(i));
        Notifications.info("Profiles", "Selected " + all.get(i) + " (" + (i + 1) + "/" + all.size() + ")");
    }
}
