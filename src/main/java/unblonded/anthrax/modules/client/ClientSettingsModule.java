package unblonded.anthrax.modules.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.gui.AnthraxScreen;
import unblonded.anthrax.gui.GuiPreferences;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.gui.SubMenuScreen;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.GuiModule;
import unblonded.anthrax.modules.ModuleConfig;
import unblonded.anthrax.modules.ModuleRegistry;
import unblonded.anthrax.modules.Setting;

/** Client-wide options that belong to no single module, such as the sub module menu key. */
public final class ClientSettingsModule extends FeatureModule {
    public static final ClientSettingsModule INSTANCE = new ClientSettingsModule();

    private final Setting.Key menuKey = setting(Setting.Key.standalone("Open menu key", GLFW.GLFW_KEY_RIGHT_SHIFT,
            () -> Minecraft.getInstance().gui.setScreen(new AnthraxScreen())),
            "Opens this menu. Press it again inside the menu to close it.");
    private final Setting.Key subMenuKey = setting(Setting.Key.standalone("Open sub module menu", GLFW.GLFW_KEY_B,
            () -> Minecraft.getInstance().gui.setScreen(new SubMenuScreen())),
            "Opens a launcher for module screens (Base Intel, the chunk list). This key wins over other binds on the same key.");
    private final Setting.Bool onlyEnabled = setting(Setting.Bool.standalone("Only show enabled modules", true),
            "List only the screens whose module is turned on. Off lists them all.");
    private final Setting.Key panicKey = setting(Setting.Key.standalone("Panic key", Setting.Key.NONE, ClientSettingsModule::panic),
            "Turns every module off at once.");
    private final Setting.Bool toggleToasts = setting(Setting.Bool.standalone("Toggle notifications", true),
            "Show the Enabled / Disabled notification when a module is switched by key or in the menu.");
    private final Setting.Action resetLayout = setting(new Setting.Action("Reset menu and HUD layout", () -> {
        GuiPreferences.resetLayout();
        Notifications.info("Client Settings", "Window positions, sizes and HUD layout reset");
    }), "Puts the menu, category windows and every HUD block back to their default place and size.");
    private final Setting.Action replayTour = setting(new Setting.Action("Replay the tour", () -> {
        GuiPreferences.setOnboarded(false);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new AnthraxScreen()));
    }), "Shows the first-run walkthrough of the menu again.");
    private final Setting.Action openFolder = setting(new Setting.Action("Open config folder", () -> {
        ModuleConfig.saveNow();
        Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir());
    }), "Opens the folder with the config, profiles, waypoints, base log and chunk data.");

    private ClientSettingsModule() {
        super("Client Settings", "Client-wide options, including the sub module menu", "Client", true);
    }

    @Override protected boolean hiddenFromList() { return true; }

    public Setting.Key subMenuKey() { return subMenuKey; }
    public Setting.Key menuKey() { return menuKey; }

    /** Module toggle notification, unless turned off here. */
    public static void toggled(String module, boolean on) {
        if (!INSTANCE.toggleToasts.get()) return;
        if (on) Notifications.success(module, "Enabled");
        else Notifications.info(module, "Disabled");
    }

    /** Turns every listed module off (hidden client modules such as this one stay on). */
    public static void panic() {
        int n = 0;
        for (GuiModule m : ModuleRegistry.all()) {
            if (!m.isEnabled() || m.isHiddenFromList()) continue;
            m.setEnabled(false);
            n++;
        }
        Notifications.warn("Panic", "Turned off " + n + " module" + (n == 1 ? "" : "s"));
    }
    public boolean onlyEnabled() { return onlyEnabled.get(); }
    public void setOnlyEnabled(boolean value) { onlyEnabled.set(value); }
}
