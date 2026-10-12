package unblonded.anthrax.modules.visual;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Lights everything as if under night vision, without the potion effect (see LightmapMixin). */
public final class FullbrightModule extends FeatureModule {
    public static final FullbrightModule INSTANCE = new FullbrightModule();
    private final Setting.Num strength = setting(Setting.Num.standalone("Strength", 100, 10, 100, 5));

    private FullbrightModule() {
        super("Fullbright", "See clearly in caves and at night", "Visuals", false);
    }

    public float strength() { return (float) strength.get() / 100f; }
}
