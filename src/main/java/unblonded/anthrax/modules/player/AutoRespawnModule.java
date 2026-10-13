package unblonded.anthrax.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Respawns automatically when you die. */
public final class AutoRespawnModule extends FeatureModule {
    public static final AutoRespawnModule INSTANCE = new AutoRespawnModule();
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (s)", 0.5, 0, 10, 0.5), "Wait this long on the death screen first.");
    private final Setting.Bool coords = setting(Setting.Bool.standalone("Notify death position", true), "Shows where you died before respawning.");
    private long deadSince;

    private AutoRespawnModule() { super("Auto Respawn", "Respawns automatically when you die", "Player", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen)) { deadSince = 0; return; }
        long now = System.currentTimeMillis();
        if (deadSince == 0) {
            deadSince = now;
            if (coords.get()) Notifications.warn("Auto Respawn", "Died at " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ());
        }
        if (now - deadSince < delay.get() * 1000) return;
        p.respawn();
        mc.gui.setScreen(null);
        deadSince = 0;
    }
}
