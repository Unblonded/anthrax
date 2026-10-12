package unblonded.anthrax.modules.visual;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Menu size: steps the Anthrax menus' UI scale up or down. It changes the scale the menus lay out at
 * (Draw.S), so text stays pixel-sharp and the backdrop still covers the screen, unlike stretching the
 * finished menu by a fraction.
 */
public final class FontSizeModule extends FeatureModule {
    public static final FontSizeModule INSTANCE = new FontSizeModule();
    private final Setting.Mode size = setting(Setting.Mode.standalone("Menu size", "Larger", "Smaller", "Normal", "Larger", "Largest"));
    @Override protected boolean hiddenFromList() { return true; }
    private FontSizeModule() { super("Menu Size", "Make the Anthrax menus smaller or larger", "Client", false); }

    /** Steps added to the menu UI scale (0 while the module is off). */
    public static int step() {
        if (!INSTANCE.isEnabled()) return 0;
        return switch (INSTANCE.size.get()) {
            case "Smaller" -> -1;
            case "Larger" -> 1;
            case "Largest" -> 2;
            default -> 0;
        };
    }

    /** UI scale for a menu: the size-based scale plus the chosen step, kept within 1..4. */
    public static int apply(int base) { return Math.max(1, Math.min(4, base + step())); }
}
