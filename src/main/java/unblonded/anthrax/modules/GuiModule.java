package unblonded.anthrax.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Describes one module to the GUI: name, category, on/off binding and its settings. */
public final class GuiModule {
    public final String name;
    public final String description;
    public final String category;

    private final BooleanSupplier enabled;
    private final Consumer<Boolean> setEnabled;
    private final List<Setting> settings = new ArrayList<>();
    private List<Setting> settingsView;

    public GuiModule(String name, String description, String category,
                     BooleanSupplier enabled, Consumer<Boolean> setEnabled) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabled;
        this.setEnabled = setEnabled;
    }

    public GuiModule add(Setting setting) {
        settings.add(setting);
        settingsView = null;
        return this;
    }

    public List<Setting> settings() {
        if (settingsView == null) settingsView = List.copyOf(settings);
        return settingsView;
    }
    public boolean isEnabled() { return enabled.getAsBoolean(); }
    public void setEnabled(boolean v) { setEnabled.accept(v); }
    public void toggle() { setEnabled(!isEnabled()); }

}
