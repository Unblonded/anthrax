package unblonded.anthrax.modules.visual;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Selectively disables the six overlays offered by Packet Edit's No Render panel. */
public final class NoRenderModule extends FeatureModule {
    public static final NoRenderModule INSTANCE = new NoRenderModule();
    private final Setting.Bool[] effects = {
            setting(Setting.Bool.standalone("No fire overlay", false)),
            setting(Setting.Bool.standalone("No liquid overlay", false)),
            setting(Setting.Bool.standalone("No suffocation overlay", false)),
            setting(Setting.Bool.standalone("No fog", false)),
            setting(Setting.Bool.standalone("No weather", false)),
            setting(Setting.Bool.standalone("No vignette", false))
    };
    private NoRenderModule() { super("No Render", "Choose which environmental overlays to hide", "Visuals", false); }
    public static boolean isHidden(int option) {
        return INSTANCE.isEnabled() && option >= 0 && option < INSTANCE.effects.length && INSTANCE.effects[option].get();
    }
}
