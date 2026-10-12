package unblonded.anthrax.modules.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import unblonded.anthrax.modules.GuiModule;
import unblonded.anthrax.modules.Keybinds;
import unblonded.anthrax.modules.ModuleConfig;
import unblonded.anthrax.modules.ModuleRegistry;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.world.WaypointsModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chat commands with a configurable prefix (default "."). They never reach the server.
 * Built on a Brigadier dispatcher so the chat box can suggest and highlight them (see CommandSuggestionsMixin).
 * Module and setting names may contain spaces; they are matched ignoring case and spaces, and suggested
 * without spaces ("AimAssist").
 */
public final class Commands {
    private Commands() {}

    public static final CommandDispatcher<ClientSuggestionProvider> DISPATCHER = new CommandDispatcher<>();

    public static String prefix() { return CommandsModule.INSTANCE.prefix(); }

    public static void register() {
        build();
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String prefix = prefix();
            if (prefix.isEmpty() || !message.startsWith(prefix)) return true;
            Minecraft mc = Minecraft.getInstance();
            mc.gui.hud.getChat().addRecentChat(message);
            String line = message.substring(prefix.length()).trim();
            if (line.isEmpty()) { help(); return false; }
            if (mc.player == null) return false;
            try {
                DISPATCHER.execute(new StringReader(line), mc.player.connection.getSuggestionsProvider());
            } catch (CommandSyntaxException e) {
                error(e.getRawMessage().getString() + (e.getCursor() >= 0 ? " at: " + line.substring(0, Math.min(e.getCursor(), line.length())) + "<--" : ""));
            } catch (RuntimeException e) {
                error("Command failed: " + e.getMessage());
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ tree
    private static LiteralArgumentBuilder<ClientSuggestionProvider> lit(String name) { return LiteralArgumentBuilder.literal(name); }
    private static <T> RequiredArgumentBuilder<ClientSuggestionProvider, T> arg(String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
    private static String str(CommandContext<ClientSuggestionProvider> c, String name) { return StringArgumentType.getString(c, name); }

    private interface Run { void run(CommandContext<ClientSuggestionProvider> c); }
    private static com.mojang.brigadier.Command<ClientSuggestionProvider> does(Run r) { return c -> { r.run(c); return 1; }; }

    private static void alias(String name, LiteralCommandNode<ClientSuggestionProvider> target) {
        DISPATCHER.register(lit(name).executes(target.getCommand()).redirect(target));
    }

    private static final SuggestionProvider<ClientSuggestionProvider> MODULES = (c, b) -> {
        List<String> names = new ArrayList<>();
        for (GuiModule m : ModuleRegistry.all()) names.add(compact(m.name));
        return SharedSuggestionProvider.suggest(names, b);
    };
    private static final SuggestionProvider<ClientSuggestionProvider> SETTINGS = (c, b) -> {
        GuiModule m = find(str(c, "module"));
        List<String> names = new ArrayList<>();
        if (m != null) for (Setting s : m.settings()) names.add(compact(s.name));
        return SharedSuggestionProvider.suggest(names, b);
    };
    private static final SuggestionProvider<ClientSuggestionProvider> VALUES = (c, b) -> {
        Setting s = setting(find(str(c, "module")), str(c, "setting"));
        List<String> values = new ArrayList<>();
        if (s instanceof Setting.Bool) values.addAll(List.of("true", "false", "toggle"));
        else if (s instanceof Setting.Mode md) values.addAll(md.options);
        else if (s instanceof Setting.Num n) values.add(trimNum(n.get()));
        else if (s instanceof Setting.Text t && !t.get().isEmpty()) values.add(t.get());
        else if (s instanceof Setting.Action) values.add("run");
        return SharedSuggestionProvider.suggest(values, b);
    };
    private static final SuggestionProvider<ClientSuggestionProvider> KEYS = (c, b) -> {
        List<String> keys = new ArrayList<>(List.of("none", "middle", "mb4", "mb5", "lshift", "rshift", "lctrl", "rctrl",
                "lalt", "ralt", "space", "tab", "caps.lock", "grave.accent", "insert", "delete", "home", "end", "page.up", "page.down"));
        for (char ch = 'a'; ch <= 'z'; ch++) keys.add(String.valueOf(ch));
        for (char ch = '0'; ch <= '9'; ch++) keys.add(String.valueOf(ch));
        for (int i = 1; i <= 12; i++) keys.add("f" + i);
        return SharedSuggestionProvider.suggest(keys, b);
    };
    private static final SuggestionProvider<ClientSuggestionProvider> PLAYERS =
            (c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b);
    private static final SuggestionProvider<ClientSuggestionProvider> FRIENDS =
            (c, b) -> SharedSuggestionProvider.suggest(FriendsModule.names(), b);
    private static final SuggestionProvider<ClientSuggestionProvider> WAYPOINTS = (c, b) -> {
        List<String> names = new ArrayList<>();
        for (var w : WaypointsModule.INSTANCE.here()) names.add(w.name);
        return SharedSuggestionProvider.suggest(names, b);
    };
    private static final SuggestionProvider<ClientSuggestionProvider> PROFILES =
            (c, b) -> SharedSuggestionProvider.suggest(ModuleConfig.profiles(), b);
    private static final SuggestionProvider<ClientSuggestionProvider> SITES =
            (c, b) -> SharedSuggestionProvider.suggest(List.of(TestStructures.names().split(", ")), b);

    private static void build() {
        DISPATCHER.register(lit("help").executes(does(c -> help())));
        alias("?", DISPATCHER.register(lit("commands").executes(does(c -> help()))));

        LiteralCommandNode<ClientSuggestionProvider> toggle = DISPATCHER.register(lit("toggle")
                .then(arg("module", StringArgumentType.greedyString()).suggests(MODULES).executes(does(c -> toggle(str(c, "module"))))));
        alias("t", toggle);

        DISPATCHER.register(lit("bind")
                .then(arg("module", StringArgumentType.word()).suggests(MODULES)
                        .then(arg("key", StringArgumentType.word()).suggests(KEYS)
                                .executes(does(c -> bind(str(c, "module"), str(c, "key")))))));

        DISPATCHER.register(lit("set")
                .then(arg("module", StringArgumentType.word()).suggests(MODULES)
                        .then(arg("setting", StringArgumentType.word()).suggests(SETTINGS)
                                .then(arg("value", StringArgumentType.greedyString()).suggests(VALUES)
                                        .executes(does(c -> set(str(c, "module"), str(c, "setting"), str(c, "value"))))))));

        DISPATCHER.register(lit("reset")
                .then(arg("module", StringArgumentType.greedyString()).suggests(MODULES).executes(does(c -> reset(str(c, "module"))))));

        alias("list", DISPATCHER.register(lit("modules").executes(does(c -> modules()))));

        LiteralCommandNode<ClientSuggestionProvider> friend = DISPATCHER.register(lit("friend")
                .executes(does(c -> friendList()))
                .then(lit("add").then(arg("name", StringArgumentType.word()).suggests(PLAYERS)
                        .executes(does(c -> { String n = str(c, "name"); info(FriendsModule.add(n) ? "Added " + n : n + " is already a friend"); }))))
                .then(lit("del").then(arg("name", StringArgumentType.word()).suggests(FRIENDS)
                        .executes(does(c -> { String n = str(c, "name"); info(FriendsModule.remove(n) ? "Removed " + n : n + " is not a friend"); }))))
                .then(lit("list").executes(does(c -> friendList())))
                .then(lit("clear").executes(does(c -> { for (String n : List.copyOf(FriendsModule.names())) FriendsModule.remove(n); info("Friends cleared"); }))));
        alias("friends", friend);
        alias("f", friend);

        LiteralCommandNode<ClientSuggestionProvider> wp = DISPATCHER.register(lit("wp")
                .executes(does(c -> waypointList()))
                .then(lit("add").then(arg("name", StringArgumentType.word())
                        .executes(does(c -> WaypointsModule.INSTANCE.addHere(str(c, "name"))))
                        .then(arg("x", IntegerArgumentType.integer()).then(arg("y", IntegerArgumentType.integer()).then(arg("z", IntegerArgumentType.integer())
                                .executes(does(c -> WaypointsModule.INSTANCE.add(str(c, "name"), IntegerArgumentType.getInteger(c, "x"),
                                        IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z")))))))))
                .then(lit("del").then(arg("name", StringArgumentType.word()).suggests(WAYPOINTS)
                        .executes(does(c -> { String n = str(c, "name"); info(WaypointsModule.INSTANCE.remove(n) ? "Removed " + n : "No waypoint " + n); }))))
                .then(lit("list").executes(does(c -> waypointList())))
                .then(lit("clear").executes(does(c -> { WaypointsModule.INSTANCE.clearHere(); info("Waypoints in this dimension cleared"); }))));
        alias("waypoint", wp);
        alias("waypoints", wp);

        LiteralCommandNode<ClientSuggestionProvider> profile = DISPATCHER.register(lit("profile")
                .executes(does(c -> profileList()))
                .then(lit("save").executes(does(c -> profileSave("default")))
                        .then(arg("name", StringArgumentType.greedyString()).suggests(PROFILES).executes(does(c -> profileSave(str(c, "name"))))))
                .then(lit("load").then(arg("name", StringArgumentType.greedyString()).suggests(PROFILES)
                        .executes(does(c -> { String n = str(c, "name"); if (ModuleConfig.loadProfile(n)) info("Loaded profile " + n); else error("No profile " + n); }))))
                .then(lit("del").then(arg("name", StringArgumentType.greedyString()).suggests(PROFILES)
                        .executes(does(c -> { String n = str(c, "name"); info(ModuleConfig.deleteProfile(n) ? "Deleted " + n : "No profile " + n); }))))
                .then(lit("list").executes(does(c -> profileList()))));
        alias("config", profile);
        alias("cfg", profile);

        DISPATCHER.register(lit("panic").executes(does(c -> panic())));
        DISPATCHER.register(lit("coords").executes(does(c -> coords())));
        DISPATCHER.register(lit("prefix").then(arg("symbol", StringArgumentType.word()).executes(does(c -> prefix(str(c, "symbol"))))));

        LiteralCommandNode<ClientSuggestionProvider> testbuild = DISPATCHER.register(lit("testbuild")
                .then(arg("site", StringArgumentType.word()).suggests(SITES).executes(does(c -> testBuild(str(c, "site"))))));
        alias("tb", testbuild);
    }

    // ------------------------------------------------------------------ commands
    private static void help() {
        String p = CommandsModule.INSTANCE.prefix();
        info("Commands (Tab completes):");
        line(p + "toggle <module>", "turn a module on or off");
        line(p + "bind <module> <key|none>", "set a toggle key");
        line(p + "set <module> <setting> <value>", "change a setting");
        line(p + "reset <module>", "restore a module's defaults");
        line(p + "friend add|del|list|clear [name]", "manage friends");
        line(p + "wp add|del|list|clear [name] [x y z]", "manage waypoints");
        line(p + "profile save|load|del|list [name]", "config profiles");
        line(p + "modules", "list modules");
        line(p + "coords", "copy your coordinates");
        line(p + "panic", "disable every module");
        line(p + "prefix <symbol>", "change the command prefix");
        line(p + "testbuild <" + TestStructures.names().replace(", ", "|") + ">", "singleplayer test sites");
    }

    private static void toggle(String name) {
        GuiModule m = module(name);
        if (m == null) return;
        m.toggle();
        info(m.name + (m.isEnabled() ? " enabled" : " disabled"));
    }

    private static void bind(String name, String key) {
        GuiModule m = module(name);
        if (m == null) return;
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

    private static void set(String moduleName, String settingName, String value) {
        GuiModule m = module(moduleName);
        if (m == null) return;
        Setting s = setting(m, settingName);
        if (s == null) { error(m.name + " has no setting \"" + settingName + "\""); return; }
        String result = apply(s, value);
        if (result == null) error("Invalid value \"" + value + "\" for " + s.name);
        else info(m.name + " > " + s.name + " = " + result);
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

    private static void reset(String name) {
        GuiModule m = module(name);
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

    private static void friendList() {
        info(FriendsModule.names().isEmpty() ? "No friends yet (middle-click a player)" : "Friends: " + String.join(", ", FriendsModule.names()));
    }

    private static void waypointList() {
        var list = WaypointsModule.INSTANCE.here();
        if (list.isEmpty()) { info("No waypoints in this dimension"); return; }
        for (var w : list) line(w.name, w.x + " " + w.y + " " + w.z + (w.death ? "  (death)" : ""));
    }

    private static void profileSave(String name) {
        if (ModuleConfig.saveProfile(name)) info("Saved profile " + name); else error("Could not save " + name);
    }

    private static void profileList() {
        List<String> all = ModuleConfig.profiles();
        info(all.isEmpty() ? "No saved profiles" : "Profiles: " + String.join(", ", all));
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

    private static void prefix(String p) {
        if (p.length() > 3 || p.startsWith("/")) { error("Prefix must be 1-3 characters and not start with /"); return; }
        CommandsModule.INSTANCE.setPrefix(p);
        info("Prefix is now " + p);
    }

    private static void testBuild(String site) {
        String err = TestStructures.run(site);
        if (err != null) { error(err); return; }
        info(site.equalsIgnoreCase("clear") ? "Test sites flattened" : "Building " + site + " around you (waypoints added)");
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

    private static Setting setting(GuiModule m, String name) {
        if (m == null) return null;
        String n = normalize(name);
        for (Setting s : m.settings()) if (normalize(s.name).equals(n)) return s;
        for (Setting s : m.settings()) if (!n.isEmpty() && normalize(s.name).startsWith(n)) return s;
        return null;
    }

    /** "Show Player List" -> "ShowPlayerList": one word, so it fits a word argument and still matches. */
    private static String compact(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("[^A-Za-z0-9]+"))
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        return sb.toString();
    }

    private static String trimNum(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(Math.round(v * 1000) / 1000.0);
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
