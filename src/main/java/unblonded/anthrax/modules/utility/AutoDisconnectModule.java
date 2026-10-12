package unblonded.anthrax.modules.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.player.Player;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Disconnects when another player gets too close or your health drops too low. Disarms after firing. */
public final class AutoDisconnectModule extends FeatureModule {
    private final Setting.Bool primed = setting(Setting.Bool.standalone("Primed", false));
    private final Setting.Num distance = setting(Setting.Num.standalone("Player proximity", 5, 1, 64, .5));
    private final Setting.Num health = setting(Setting.Num.standalone("Health below (0 = off)", 0, 0, 20, .5));

    public AutoDisconnectModule() {
        super("Auto Disconnect", "Leave the server when a player gets close or health runs low", "Utility", false);
    }

    @Override public void tick(Minecraft client) {
        if (!primed.get() || client.player == null || client.level == null || client.getConnection() == null) return;
        if (health.get() > 0 && client.player.getHealth() <= health.get()) {
            leave(client, String.format("Health dropped to %.1f", client.player.getHealth()));
            return;
        }
        double radiusSq = distance.get() * distance.get();
        for (Player other : client.level.players()) {
            if (other == client.player || other.isRemoved() || other.isSpectator()) continue;
            if (client.player.distanceToSqr(other) <= radiusSq) {
                leave(client, other.getName().getString() + " came within " + distance.display() + " blocks");
                return;
            }
        }
    }

    private void leave(Minecraft client, String reason) {
        primed.set(false);
        System.out.println("[Anthrax] Auto Disconnect: " + reason);
        client.disconnect(new TitleScreen(), false);
    }

    @Override public String hudInfo() { return primed.get() ? "Armed" : "Idle"; }
}
