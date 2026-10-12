package unblonded.anthrax.modules.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Friends list. Middle-click a player to add or remove them. Friends are skipped by combat modules
 * and shown in their own color by ESP, tracers and nametags. Stored as a setting, so it is part of
 * config profiles.
 */
public final class FriendsModule extends FeatureModule {
    public static final FriendsModule INSTANCE = new FriendsModule();
    public static final int COLOR = 0xFF5CFF9D;

    private final Setting.Text list = setting(Setting.Text.standalone("Friends", "", 2048));
    private final Setting.Bool middleClick = setting(Setting.Bool.standalone("Middle-click to friend", true));
    private final Setting.Bool protect = setting(Setting.Bool.standalone("Combat ignores friends", true));
    private final Setting.Action clear = setting(new Setting.Action("Clear friends", () -> {
        list.set("");
        Notifications.info("Friends", "Friends list cleared");
    }));
    private boolean middleWasDown;

    private FriendsModule() {
        super("Friends", "Middle-click players to friend them; combat modules leave friends alone", "Client", true);
    }

    @Override protected boolean hiddenFromList() { return true; }

    @Override public void tick(Minecraft mc) {
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (down && !middleWasDown && middleClick.get() && mc.gui.screen() == null
                && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p) {
            toggle(p.getName().getString());
        }
        middleWasDown = down;
    }

    public static Set<String> names() {
        Set<String> out = new LinkedHashSet<>();
        for (String s : INSTANCE.list.get().split(",")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }

    public static boolean isFriend(String name) {
        if (!INSTANCE.isEnabled() || name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String n : names()) if (n.toLowerCase(Locale.ROOT).equals(lower)) return true;
        return false;
    }

    public static boolean isFriend(Entity e) { return e instanceof Player p && isFriend(p.getName().getString()); }

    /** True when combat modules should not target this entity. */
    public static boolean protects(Entity e) { return INSTANCE.protect.get() && isFriend(e); }

    public static boolean add(String name) {
        if (isFriend(name)) return false;
        Set<String> n = names();
        n.add(name);
        INSTANCE.list.set(String.join(",", n));
        return true;
    }

    public static boolean remove(String name) {
        List<String> kept = new ArrayList<>();
        boolean removed = false;
        for (String n : names()) {
            if (n.equalsIgnoreCase(name)) removed = true; else kept.add(n);
        }
        INSTANCE.list.set(String.join(",", kept));
        return removed;
    }

    public static void toggle(String name) {
        if (remove(name)) Notifications.info("Friends", "Removed " + name);
        else if (add(name)) Notifications.success("Friends", "Added " + name);
    }
}
