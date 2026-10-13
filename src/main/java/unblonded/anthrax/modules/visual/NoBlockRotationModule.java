package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import unblonded.anthrax.modules.FeatureModule;

/** Same model rotation for identical blocks instead of the random per-position pick (see BlockSeedMixin). */
public final class NoBlockRotationModule extends FeatureModule {
    public static final NoBlockRotationModule INSTANCE = new NoBlockRotationModule();

    private NoBlockRotationModule() { super("No Block Rotation", "Uses the same model rotation for identical blocks instead of random rotations. Directional blocks are unchanged", "Visuals", false); }

    @Override protected void onToggle(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        XrayModule.rebuildChunks(mc);
    }
}
