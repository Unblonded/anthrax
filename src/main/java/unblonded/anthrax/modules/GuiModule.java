package unblonded.anthrax.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Describes one module to the GUI: name, category, on/off binding, keybind and its settings. */
public final class GuiModule {
    /** GLFW key code meaning "not bound". */
    public static final int NO_KEY = -1;

    public final String name;
    public final String description;
    public final String category;

    private final BooleanSupplier enabled;
    private final Consumer<Boolean> setEnabled;
    private final List<Setting> settings = new ArrayList<>();
    private List<Setting> settingsView;
    private Supplier<String> info = () -> null;
    private boolean hiddenFromList;
    private int key = NO_KEY;

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

    /** Short live status shown beside the module name in the HUD module list (null for none). */
    public GuiModule info(Supplier<String> info) {
        this.info = info == null ? () -> null : info;
        return this;
    }

    /** Keeps always-on infrastructure modules (such as the HUD itself) out of the module list. */
    public GuiModule hiddenFromList(boolean hidden) {
        this.hiddenFromList = hidden;
        return this;
    }

    public List<Setting> settings() {
        if (settingsView == null) settingsView = List.copyOf(settings);
        return settingsView;
    }
    public boolean isEnabled() { return enabled.getAsBoolean(); }
    public void setEnabled(boolean v) { setEnabled.accept(v); }
    public void toggle() { setEnabled(!isEnabled()); }

    public String info() {
        try { return info.get(); }
        catch (RuntimeException ignored) { return null; }
    }
    public boolean isHiddenFromList() { return hiddenFromList; }

    public int key() { return key; }
    public void setKey(int key) { this.key = key <= 0 ? NO_KEY : key; }
    public boolean hasKey() { return key != NO_KEY; }
}
