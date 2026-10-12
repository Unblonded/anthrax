package unblonded.anthrax.modules.client;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Switches and styles for the in-game overlay drawn by {@code unblonded.anthrax.gui.Hud}. */
public final class HudModule extends FeatureModule {
    public static final HudModule INSTANCE = new HudModule();

    private final Setting.Bool watermark = setting(Setting.Bool.standalone("Watermark", true));
    private final Setting.Bool moduleList = setting(Setting.Bool.standalone("Module list", true));
    private final Setting.Bool listInfo = setting(Setting.Bool.standalone("Module list details", true), "Show extra info (like a module's target or mode) next to its name.");
    private final Setting.Mode listColor = setting(Setting.Mode.standalone("Module list color", "Theme", "Theme", "Rainbow", "White"));
    private final Setting.Bool notifications = setting(Setting.Bool.standalone("Notifications", true));
    private final Setting.Bool coordinates = setting(Setting.Bool.standalone("Coordinates", true));
    private final Setting.Bool armorHud = setting(Setting.Bool.standalone("Armor HUD", true));
    private final Setting.Bool effects = setting(Setting.Bool.standalone("Potion effects", true));
    private final Setting.Bool keystrokes = setting(Setting.Bool.standalone("Keystrokes and CPS", false));
    private final Setting.Mode size = setting(Setting.Mode.standalone("HUD size", "Normal", "Small", "Normal", "Large"), "Make every HUD block smaller or larger.");

    private HudModule() {
        super("HUD", "Watermark, module list, notifications and coordinates overlay", "Client", true);
    }

    @Override protected boolean hiddenFromList() { return true; }

    public boolean watermark() { return isEnabled() && watermark.get(); }
    public boolean moduleList() { return isEnabled() && moduleList.get(); }
    public boolean listInfo() { return listInfo.get(); }
    public String listColor() { return listColor.get(); }
    public boolean notifications() { return isEnabled() && notifications.get(); }
    public boolean coordinates() { return isEnabled() && coordinates.get(); }
    public boolean armorHud() { return isEnabled() && armorHud.get(); }
    public boolean effects() { return isEnabled() && effects.get(); }
    public boolean keystrokes() { return isEnabled() && keystrokes.get(); }
    /** -1, 0 or +1 steps relative to the automatic HUD scale. */
    public int sizeStep() { return switch (size.get()) { case "Small" -> -1; case "Large" -> 1; default -> 0; }; }
}
