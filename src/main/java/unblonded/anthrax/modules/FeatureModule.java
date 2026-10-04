package unblonded.anthrax.modules;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** Shared lifecycle and GUI adapter for client features. Feature work runs on the client thread. */
public abstract class FeatureModule {
    public final String name;
    public final String description;
    public final String category;
    private final List<Setting> settings = new ArrayList<>();
    private boolean enabled;

    protected FeatureModule(String name, String description, String category, boolean defaultEnabled) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = defaultEnabled;
    }

    protected final <T extends Setting> T setting(T setting) {
        settings.add(setting);
        return setting;
    }

    public final List<Setting> settings() { return List.copyOf(settings); }

    public final boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        onToggle(enabled);
    }

    protected void onToggle(boolean enabled) {}
    public void tick(Minecraft client) {}

    public final GuiModule asGuiModule() {
        GuiModule module = new GuiModule(name, description, category, this::isEnabled, this::setEnabled);
        for (Setting setting : settings()) module.add(setting);
        return module;
    }
}
