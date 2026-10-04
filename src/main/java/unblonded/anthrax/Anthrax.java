package unblonded.anthrax;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.gui.AnthraxScreen;
import unblonded.anthrax.modules.combat.CrystalAuraModule;
import unblonded.anthrax.modules.FeatureManager;
import unblonded.anthrax.modules.ModuleRegistry;
import unblonded.anthrax.modules.ModuleConfig;

public class Anthrax implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Force feature registration before the first GUI opens.
        ModuleRegistry.categories();
        ModuleConfig.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ModuleConfig.saveNow());
        KeyMapping guiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.anthrax.gui",
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ModuleConfig.tick();
            CrystalAuraModule.INSTANCE.onTick(client);
            FeatureManager.tick(client);
            if (guiKey.consumeClick() && client.gui.screen() == null) {
                client.gui.setScreen(new AnthraxScreen());
            }
        });
    }
}
