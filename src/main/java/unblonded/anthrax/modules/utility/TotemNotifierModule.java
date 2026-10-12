package unblonded.anthrax.modules.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.combat.TotemPops;

/** Announces totem pops (yours and, optionally, everyone's) with a running per-life pop count. */
public final class TotemNotifierModule extends FeatureModule {
    private final Setting.Bool self = setting(Setting.Bool.standalone("Your pops", true));
    private final Setting.Bool others = setting(Setting.Bool.standalone("Other players", true));
    private final Setting.Bool chat = setting(Setting.Bool.standalone("Also log to chat", false));

    public TotemNotifierModule() {
        super("Totem Notifier", "Announce totem pops with a running count per player", "Combat", false);
        TotemPops.listen(this::onPop);
    }

    private void onPop(Player player, int count) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = player == mc.player;
        if (isSelf ? !self.get() : !others.get()) return;
        String who = isSelf ? "You" : player.getName().getString();
        String text = who + " popped " + count + (count == 1 ? " totem" : " totems")
                + (isSelf ? String.format(" (%.1f HP)", player.getHealth()) : "");
        if (isSelf) Notifications.warn("Totem Pop", text);
        else Notifications.info("Totem Pop", text);
        if (chat.get() && mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Anthrax] " + text));
    }
}
