package unblonded.anthrax.modules.cosmetic;

import net.minecraft.resources.Identifier;
import unblonded.anthrax.modules.Setting;

import java.util.Locale;

/**
 * A film-style colour grade over the world (not your hand or the HUD): soft glow on bright areas, white balance,
 * contrast, saturation and a vignette. Each look is a fixed post effect (post effect values are set per file),
 * applied by PostGradeMixin just before the hand is drawn.
 */
public final class RealisticWorldModule extends CosmeticModule {
    public static final RealisticWorldModule INSTANCE = new RealisticWorldModule();
    private final Setting.Mode look = setting(Setting.Mode.standalone("Look", "Natural", "Natural", "Cinematic", "Vivid", "Warm", "Cold", "Noir", "Dreamy"),
            "Natural is a light touch; Cinematic adds contrast and a vignette; Noir is black and white; Dreamy has a strong glow.");

    private RealisticWorldModule() { super("Realistic World", "A cinematic colour grade over the world: glow, contrast, warmth and a vignette"); }

    public Identifier effect() { return Identifier.fromNamespaceAndPath("anthrax", "realistic_" + look.get().toLowerCase(Locale.ROOT)); }

    @Override public String hudInfo() { return look.get(); }
}
