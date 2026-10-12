package unblonded.anthrax.modules.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import unblonded.anthrax.modules.GuiModule;
import unblonded.anthrax.modules.Keybinds;
import unblonded.anthrax.modules.ModuleConfig;
import unblonded.anthrax.modules.ModuleRegistry;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.world.WaypointsModule;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Chat commands with a configurable prefix (default "."). They never reach the server.
 * Module names may contain spaces; they are matched ignoring case and spaces.
 */
public final class Commands {
    private Commands() {}

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String prefix = CommandsModule.INSTANCE.prefix();
            if (prefix.isEmpty() || !message.startsWith(prefix)) return true;
            Minecraft.getInstance().gui.hud.getChat().addRecentChat(message);
            try {
                run(message.substring(prefix.length()).trim());
            } catch (RuntimeException e) {
                error("Command failed: " + e.getMessage());
            }
            return false;
        });
    }

    private static void run(String line) {
        if (line.isEmpty()) { help(); return; }
        String[] args = line.split("\\s+");
        String cmd = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (cmd) {
            case "help", "?" -> help();
            case "t", "toggle" -> toggle(rest);
            case "bind" -> bind(rest);
            case "set" -> set(rest);
            case "reset" -> reset(rest);
            case "modules", "list" -> modules();
            case "friend", "friends", "f" -> friend(rest);
            case "wp", "waypoint", "waypoints" -> waypoint(rest);
            case "profile", "config", "cfg" -> profile(rest);
            case "panic" -> panic();
            case "coords" -> coords();
            case "prefix" -> prefix(rest);
            default -> error("Unknown command \"" + cmd + "\". Try " + CommandsModule.INSTANCE.prefix() + "help");
        }
    }

    // ------------------------------------------------------------------ commands
    private static void help() {
        String p = CommandsModule.INSTANCE.prefix();
        info("Commands:");
        line(p + "toggle <module>", "turn a module on or off");
        line(p + "bind <module> <key|none>", "set a toggle key");
        line(p + "set <module> <setting> <value>", "change a setting");
        line(p + "reset <module>", "restore a module's defaults");
        line(p + "friend add|del|list [name]", "manage friends");
        line(p + "wp add|del|list|clear [name] [x y z]", "manage waypoints");
        line(p + "profile save|load|del|list [name]", "config profiles");
        line(p + "modules", "list modules");
        line(p + "coords", "copy your coordinates");
        line(p + "panic", "disable every module");
        line(p + "prefix <symbol>", "change the command prefix");
    }

    private static void toggle(String[] a) {
        GuiModule m = module(String.join(" ", a));
        if (m == null) return;
        m.toggle();
        info(m.name + (m.isEnabled() ? " enabled" : " disabled"));
    }

    private static void bind(String[] a) {
        if (a.length < 2) { error("Usage: bind <module> <key|none>"); return; }
        GuiModule m = module(String.join(" ", Arrays.copyOf(a, a.length - 1)));
        if (m == null) return;
        String key = a[a.length - 1];
        if (key.equalsIgnoreCase("none") || key.equalsIgnoreCase("clear")) {
            m.setKey(GuiModule.NO_KEY);
            info(m.name + " unbound");
            return;
        }
        int code = keyCode(key);
        if (code <= 0) { error("Unknown key \"" + key + "\""); return; }
        m.setKey(code);
        info(m.name + " bound to " + Keybinds.keyName(code));
    }

    private static void set(String[] a) {
        if (a.length < 3) { error("Usage: set <module> <setting> <value>"); return; }
        // Try every split point: the module name and setting name may both contain spaces.
        for (int split = 1; split < a.length - 1; split++) {
            GuiModule m = find(String.join(" ", Arrays.copyOfRange(a, 0, split)));
            if (m == null) continue;
            for (int valueStart = a.length - 1; valueStart > split; valueStart--) {
                String settingName = String.join(" ", Arrays.copyOfRange(a, split, valueStart));
                String value = String.join(" ", Arrays.copyOfRange(a, valueStart, a.length));
                for (Setting s : m.settings()) {
                    if (!normalize(s.name).equals(normalize(settingName))) continue;
                    String result = apply(s, value);
                    if (result == null) error("Invalid value \"" + value + "\" for " + s.name);
                    else info(m.name + " > " + s.name + " = " + result);
                    return;
                }
            }
        }
        error("No such module/setting. Example: set aim assist range 5");
    }

    private static String apply(Setting s, String value) {
        try {
            if (s instanceof Setting.Bool b) {
                boolean v = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equals("1")
                        || (!value.equalsIgnoreCase("false") && !value.equalsIgnoreCase("off") && !value.equals("0") && !b.get());
                b.set(v);
                return Boolean.toString(v);
            }
            if (s instanceof Setting.Num n) { n.set(Double.parseDouble(value)); return n.display(); }
            if (s instanceof Setting.Mode md) {
                for (String o : md.options) if (o.equalsIgnoreCase(value)) { md.set(o); return o; }
                return null;
            }
            if (s instanceof Setting.Text t) { t.set(value); return value; }
            if (s instanceof Setting.Action action) { action.run(); return "run"; }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private static void reset(String[] a) {
        GuiModule m = module(String.join(" ", a));
        if (m == null) return;
        ModuleConfig.resetModule(m);
        info(m.name + " reset to defaults");
    }

    private static void modules() {
        StringBuilder sb = new StringBuilder();
        for (String cat : ModuleRegistry.categories()) {
            sb.setLength(0);
            for (GuiModule m : ModuleRegistry.inCategory(cat)) sb.append(sb.length() > 0 ? ", " : "").append(m.name).append(m.isEnabled() ? "*" : "");
            line(cat, sb.toString());
        }
    }

    private static void friend(String[] a) {
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> { if (a.length < 2) error("Usage: friend add <name>"); else info(FriendsModule.add(a[1]) ? "Added " + a[1] : a[1] + " is already a friend"); }
            case "del", "remove", "rm" -> { if (a.length < 2) error("Usage: friend del <name>"); else info(FriendsModule.remove(a[1]) ? "Removed " + a[1] : a[1] + " is not a friend"); }
            case "clear" -> { for (String n : FriendsModule.names()) FriendsModule.remove(n); info("Friends cleared"); }
            default -> info(FriendsModule.names().isEmpty() ? "No friends yet (middle-click a player)" : "Friends: " + String.join(", ", FriendsModule.names()));
        }
    }

    private static void waypoint(String[] a) {
        WaypointsModule wp = WaypointsModule.INSTANCE;
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> {
                if (a.length < 2) { error("Usage: wp add <name> [x y z]"); return; }
                if (a.length >= 5) {
                    try { wp.add(a[1], Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])); }
                    catch (NumberFormatException e) { error("Coordinates must be whole numbers"); }
                } else wp.addHere(a[1]);
            }
            case "del", "remove", "rm" -> { if (a.length < 2) error("Usage: wp del <name>"); else info(wp.remove(a[1]) ? "Removed " + a[1] : "No waypoint " + a[1]); }
            case "clear" -> { wp.clearHere(); info("Waypoints in this dimension cleared"); }
            default -> {
                var list = wp.here();
                if (list.isEmpty()) { info("No waypoints in this dimension"); return; }
                for (var w : list) line(w.name, w.x + " " + w.y + " " + w.z + (w.death ? "  (death)" : ""));
            }
        }
    }

    private static void profile(String[] a) {
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        String name = a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : "default";
        switch (sub) {
            case "save" -> { if (ModuleConfig.saveProfile(name)) info("Saved profile " + name); else error("Could not save " + name); }
            case "load" -> { if (ModuleConfig.loadProfile(name)) info("Loaded profile " + name); else error("No profile " + name); }
            case "del", "delete", "rm" -> info(ModuleConfig.deleteProfile(name) ? "Deleted " + name : "No profile " + name);
            default -> {
                List<String> all = ModuleConfig.profiles();
                info(all.isEmpty() ? "No saved profiles" : "Profiles: " + String.join(", ", all));
            }
        }
    }

    private static void panic() {
        int n = 0;
        for (GuiModule m : ModuleRegistry.all()) {
            if (!m.isEnabled() || m.isHiddenFromList()) continue;
            m.setEnabled(false);
            n++;
        }
        info("Disabled " + n + " module" + (n == 1 ? "" : "s"));
    }

    private static void coords() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        String c = p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
        Minecraft.getInstance().keyboardHandler.setClipboard(c);
        info("Copied " + c);
    }

    private static void prefix(String[] a) {
        if (a.length != 1 || a[0].length() > 3) { error("Usage: prefix <1-3 characters>"); return; }
        CommandsModule.INSTANCE.setPrefix(a[0]);
        info("Prefix is now " + a[0]);
    }

    // ------------------------------------------------------------------ helpers
    private static String normalize(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }

    private static GuiModule find(String name) {
        String n = normalize(name);
        if (n.isEmpty()) return null;
        for (GuiModule m : ModuleRegistry.all()) if (normalize(m.name).equals(n)) return m;
        for (GuiModule m : ModuleRegistry.all()) if (normalize(m.name).startsWith(n)) return m;
        return null;
    }

    private static GuiModule module(String name) {
        GuiModule m = find(name);
        if (m == null) error("No module \"" + name + "\"");
        return m;
    }

    private static int keyCode(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        switch (n) {
            case "middle", "mb3", "mouse3" -> { return Keybinds.MOUSE_BASE + 2; }
            case "mb4", "mouse4" -> { return Keybinds.MOUSE_BASE + 3; }
            case "mb5", "mouse5" -> { return Keybinds.MOUSE_BASE + 4; }
            default -> {}
        }
        if (n.length() == 1) {
            char c = Character.toUpperCase(n.charAt(0));
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return c;
        }
        try {
            return InputConstants.getKey("key.keyboard." + n.replace("lshift", "left.shift").replace("rshift", "right.shift")
                    .replace("lctrl", "left.control").replace("rctrl", "right.control")
                    .replace("lalt", "left.alt").replace("ralt", "right.alt")).getValue();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static MutableComponent tag() {
        return Component.literal("[Anthrax] ").withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private static void info(String text) { send(tag().append(Component.literal(text).withStyle(ChatFormatting.GRAY))); }
    private static void error(String text) { send(tag().append(Component.literal(text).withStyle(ChatFormatting.RED))); }
    private static void line(String head, String text) {
        send(Component.literal("  " + head + "  ").withStyle(ChatFormatting.AQUA).append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
    }

    private static void send(Component c) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(c);
    }
}
