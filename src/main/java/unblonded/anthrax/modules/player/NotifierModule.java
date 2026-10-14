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
    private final Setting.Bool totems = setting(Setting.Bool.standalone("Totem pops", true), "When a player pops a totem of undying, with how many they've popped this life.");
    private final Setting.Bool ownTotems = setting(Setting.Bool.standalone("Your own pops", false), "Also announce your own totem pops.");
    private final Setting.Bool deaths = setting(Setting.Bool.standalone("Deaths after pops", true), "When a player who popped totems dies, say how many it took.");
    private final Setting.Num radius = setting(Setting.Num.standalone("Close radius", 0, 0, 128, 4), "Extra alert when a player comes this close (0 = off).");
    private final Setting.Mode output = setting(Setting.Mode.standalone("Show in", "Notifications", "Notifications", "Chat", "Both"), "Where alerts appear. Chat messages are local and only you see them.");
    private final java.util.Set<java.util.UUID> seen = new java.util.HashSet<>(), close = new java.util.HashSet<>();
    private final java.util.Set<Integer> pearlIds = new java.util.HashSet<>();
    /** False until the first scan after joining, so players already around are not announced. */
    private boolean primed;

    /** Pop counts of players seen popping, kept until they die, so the death message can say how many it took. */
    private final java.util.Map<java.util.UUID, Integer> popped = new java.util.HashMap<>();

    private NotifierModule() {
        super("Notifier", "Alerts you to players entering or leaving view, thrown ender pearls and totem pops, in chat, as notifications, or both", "Player", false);
        unblonded.anthrax.modules.combat.TotemPops.listen((player, count) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!isEnabled() || !totems.get() || mc.player == null) return;
            boolean self = player == mc.player;
            if (self && !ownTotems.get()) return;
            popped.put(player.getUUID(), count);
            String who = self ? "You" : player.getName().getString();
            say(mc, who + " popped " + (count == 1 ? "a totem" : count + " totems") + (self ? "" : " (" + Math.round(mc.player.distanceTo(player)) + "m)"));
        });
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) { seen.clear(); close.clear(); pearlIds.clear(); popped.clear(); primed = false; return; }
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
        if (deaths.get() && !popped.isEmpty()) {
            popped.entrySet().removeIf(en -> {
                var pl = mc.level.getPlayerByUUID(en.getKey());
                if (pl == null) return !now.contains(en.getKey()) && !en.getKey().equals(self.getUUID());
                if (!pl.isDeadOrDying()) return false;
                say(mc, (pl == self ? "You" : pl.getName().getString()) + " died after popping " + en.getValue() + (en.getValue() == 1 ? " totem" : " totems"));
                return true;
            });
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
