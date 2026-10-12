package unblonded.anthrax.modules.combat;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** A card next to the crosshair with your current target's face, health, armor, totem pops and distance. */
public final class TargetHudModule extends FeatureModule {
    public static final TargetHudModule INSTANCE = new TargetHudModule();

    private final Setting.Bool armor = setting(Setting.Bool.standalone("Show armor", true));
    private final Setting.Bool mobs = setting(Setting.Bool.standalone("Show mobs too", false));

    private TargetHudModule() {
        super("Target HUD", "Card by the crosshair showing your target's health, armor and pops", "Combat", true);
    }

    @Override protected boolean hiddenFromList() { return true; }

    public boolean showArmor() { return armor.get(); }
    public boolean showMobs() { return mobs.get(); }
}
