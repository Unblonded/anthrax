package unblonded.anthrax.modules.visual;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Scales the custom Anthrax menu like Packet Edit's global font-size override. */
public final class FontSizeModule extends FeatureModule {
    public static final FontSizeModule INSTANCE = new FontSizeModule();
    private final Setting.Num size = setting(Setting.Num.standalone("Font size", 1.5, .25, 4, .1));
    @Override protected boolean hiddenFromList() { return true; }
    private FontSizeModule() { super("Font Size Override", "Scale the Anthrax menu interface", "Client", false); }
    public static float scale() { return INSTANCE.isEnabled() ? (float)INSTANCE.size.get() : 1f; }
}
