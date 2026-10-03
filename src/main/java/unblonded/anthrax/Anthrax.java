package unblonded.anthrax;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.gui.AnthraxScreen;

public class Anthrax implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMapping guiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.anthrax.gui",
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CrystalAuraModule.INSTANCE.onTick(client);
            if (guiKey.consumeClick() && client.gui.screen() == null) {
                client.gui.setScreen(new AnthraxScreen());
            }
        });
    }
}