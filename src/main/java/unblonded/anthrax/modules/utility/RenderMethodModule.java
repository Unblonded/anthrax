package unblonded.anthrax.modules.utility;

import net.minecraft.client.Minecraft;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Swaps between two render distances. Both values are settings, so the normal distance survives
 * restarts even if the game is closed while the low distance is active.
 */
public final class RenderMethodModule extends FeatureModule {
    private final Setting.Num low = setting(Setting.Num.standalone("Low distance", 2, 2, 32, 1));
    private final Setting.Num normal = setting(Setting.Num.standalone("Normal distance", 12, 2, 32, 1));

    public RenderMethodModule() {
        super("Render Method", "Instantly swap between a low and your normal render distance", "Utility", false);
    }

    @Override protected void onToggle(boolean enabled) {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        if (enabled) {
            int current = client.options.renderDistance().get();
            // Remember the player's real distance, unless it is already the low one.
            if (current != (int) low.get()) normal.set(current);
            client.options.renderDistance().set((int) low.get());
        } else {
            client.options.renderDistance().set((int) normal.get());
        }
    }

    @Override public String hudInfo() { return Integer.toString((int) low.get()); }
}
