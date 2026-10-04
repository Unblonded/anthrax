package unblonded.anthrax.modules.visual;

import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Adds a vanilla outline pass to other players, including through walls. */
public final class PlayerEspModule extends FeatureModule {
    public static final PlayerEspModule INSTANCE = new PlayerEspModule();
    private final Setting.Mode color = setting(Setting.Mode.standalone("Color", "Cyan", "Cyan", "Red", "Green", "Gold", "Violet", "White"));

    private PlayerEspModule() {
        super("Player ESP", "Outline other players through walls", "Visuals", false);
    }

    public int outlineColor() {
        float[] rgb = BlockEspModule.color(color.get());
        return 0xFF000000 | (Math.round(rgb[0] * 255) << 16) | (Math.round(rgb[1] * 255) << 8) | Math.round(rgb[2] * 255);
    }
}
