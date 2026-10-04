package unblonded.anthrax.modules.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.player.Player;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Disconnects before a nearby player enters the configured proximity radius. */
public final class AutoDisconnectModule extends FeatureModule {
    private final Setting.Bool primed = setting(Setting.Bool.standalone("Primed", false));
    private final Setting.Num distance = setting(Setting.Num.standalone("Player proximity", 5, 1, 64, .5));

    public AutoDisconnectModule() {
        super("Auto Disconnect", "Leave the server when another player gets close", "Utility", false);
    }

    @Override public void tick(Minecraft client) {
        if (!primed.get() || client.player == null || client.level == null || client.getConnection() == null) return;
        double radius = distance.get();
        double radiusSq = radius * radius;
        for (Player other : client.level.players()) {
            if (other == client.player || other.isRemoved()) continue;
            if (client.player.distanceToSqr(other) <= radiusSq) {
                primed.set(false);
                client.disconnect(new TitleScreen(), false);
                return;
            }
        }
    }
}
