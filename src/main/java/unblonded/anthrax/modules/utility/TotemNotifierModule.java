package unblonded.anthrax.modules.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import unblonded.anthrax.modules.FeatureModule;

/** Packet Edit's self totem-pop notifier: watches the local player's two hands and health. */
public final class TotemNotifierModule extends FeatureModule {
    private boolean hadTotem;
    private boolean alreadyAlerted;
    private float previousHealth;
    public TotemNotifierModule() { super("Totem Notifier", "Notify when your totem pops", "Combat", false); }

    @Override public void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.player.isCreative() || client.player.isSpectator()) {
            hadTotem = false; previousHealth = 0; return;
        }
        var player = client.player;
        boolean hasTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
        float health = player.getHealth();
        if (hadTotem && !hasTotem && !alreadyAlerted
                && (previousHealth <= 2 || health < previousHealth - 6 || player.hurtTime > 0)) {
            alreadyAlerted = true;
            player.sendSystemMessage(Component.literal("Totem popped! Health: " + health));
        }
        if (!hadTotem && hasTotem) alreadyAlerted = false;
        hadTotem = hasTotem;
        previousHealth = health;
    }

    @Override protected void onToggle(boolean enabled) { if (!enabled) { hadTotem = false; alreadyAlerted = false; previousHealth = 0; } }
}
