package unblonded.anthrax;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;


public class Anthrax implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(CrystalAuraModule.INSTANCE::onTick);
	}
}