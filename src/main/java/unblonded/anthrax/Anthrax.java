package unblonded.anthrax;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.gui.AnthraxScreen;
import unblonded.anthrax.gui.Hud;
import unblonded.anthrax.modules.FeatureManager;
import unblonded.anthrax.modules.ModuleRegistry;
import unblonded.anthrax.modules.ModuleConfig;
import unblonded.anthrax.modules.combat.TotemPops;

public class Anthrax implements ClientModInitializer {
    /** Hold to zoom. */
    public static KeyMapping zoomKey;

    @Override
    public void onInitializeClient() {
        // Force feature registration before the first GUI opens.
        ModuleRegistry.categories();
        ModuleConfig.load();
        Hud.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ModuleConfig.saveNow();
            unblonded.anthrax.modules.visual.MapModule.INSTANCE.shutdown();
            unblonded.anthrax.modules.visual.MusicModule.INSTANCE.shutdown();
        });
        unblonded.anthrax.modules.visual.BetterTooltipsModule.register();
        unblonded.anthrax.modules.player.AutoReconnectModule.register();
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.anthrax.zoom",
                GLFW.GLFW_KEY_C,
                KeyMapping.Category.MISC
        ));
        unblonded.anthrax.modules.client.Commands.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ModuleConfig.tick();
            TotemPops.tick(client);
            FeatureManager.tick(client);
            unblonded.anthrax.modules.player.AutoReconnectModule.INSTANCE.screenTick(client);
        });
    }
}
