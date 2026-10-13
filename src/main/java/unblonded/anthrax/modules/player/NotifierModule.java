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

/** Alerts for players entering or leaving view, thrown ender pearls and player detection radius. */
public final class NotifierModule extends FeatureModule {
    public static final NotifierModule INSTANCE = new NotifierModule();
    private final Setting.Bool visual = setting(Setting.Bool.standalone("Players entering view", true), "When a player comes within render distance or leaves it.");
    private final Setting.Bool pearls = setting(Setting.Bool.standalone("Thrown pearls", true), "When a nearby player throws an ender pearl.");
    private final Setting.Num radius = setting(Setting.Num.standalone("Close radius", 0, 0, 128, 4), "Extra alert when a player comes this close (0 = off).");
    private final Setting.Mode output = setting(Setting.Mode.standalone("Show in", "Notifications", "Notifications", "Chat", "Both"), "Where alerts appear. Chat messages are local and only you see them.");
    private final java.util.Set<java.util.UUID> seen = new java.util.HashSet<>(), close = new java.util.HashSet<>();
    private final java.util.Set<Integer> pearlIds = new java.util.HashSet<>();
    /** False until the first scan after joining, so players already around are not announced. */
    private boolean primed;

    private NotifierModule() { super("Notifier", "Alerts you to players entering or leaving view and thrown ender pearls, in chat, as notifications, or both", "Player", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) { seen.clear(); close.clear(); pearlIds.clear(); primed = false; return; }
        java.util.Set<java.util.UUID> now = new java.util.HashSet<>(), nowClose = new java.util.HashSet<>();
        for (var pl : mc.level.players()) {
            if (pl == self) continue;
            now.add(pl.getUUID());
            if (visual.get() && primed && !seen.contains(pl.getUUID())) say(mc, pl.getName().getString() + " entered view (" + Math.round(self.distanceTo(pl)) + "m)");
            if (radius.get() > 0 && self.distanceTo(pl) <= radius.get()) {
                nowClose.add(pl.getUUID());
                if (primed && !close.contains(pl.getUUID())) say(mc, pl.getName().getString() + " is within " + (int) radius.get() + "m");
            }
        }
        if (visual.get()) for (var id : seen) if (!now.contains(id)) {
            var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
            say(mc, (info == null ? "A player" : info.getProfile().name()) + " left view");
        }
        seen.clear(); seen.addAll(now);
        primed = true;
        close.clear(); close.addAll(nowClose);
        if (!pearls.get()) return;
        java.util.Set<Integer> live = new java.util.HashSet<>();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl pearl)) continue;
            live.add(pearl.getId());
            if (pearlIds.contains(pearl.getId())) continue;
            var owner = pearl.getOwner();
            if (owner == self) continue;
            say(mc, (owner == null ? "Someone" : owner.getName().getString()) + " threw a pearl");
        }
        pearlIds.clear(); pearlIds.addAll(live);
    }

    private void say(Minecraft mc, String text) {
        String o = output.get();
        if (!o.equals("Chat")) Notifications.info("Notifier", text);
        if (!o.equals("Notifications") && mc.player != null) mc.player.sendSystemMessage(Component.literal("[Anthrax] " + text));
    }
}
