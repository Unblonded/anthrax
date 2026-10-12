package unblonded.anthrax.modules;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import unblonded.anthrax.gui.Notifications;

/** Toggles modules from their bound keys. Called from the keyboard mixin on the client thread. */
public final class Keybinds {
    private Keybinds() {}

    /** Mouse buttons share the key space: MOUSE_BASE + GLFW button (2 = middle, 3/4 = side buttons). */
    public static final int MOUSE_BASE = 1000;

    public static boolean isMouse(int key) { return key >= MOUSE_BASE && key < MOUSE_BASE + 16; }

    public static void onKeyPress(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return;
        for (GuiModule module : ModuleRegistry.all()) {
            if (module.key() != key) continue;
            module.toggle();
            unblonded.anthrax.modules.client.ClientSettingsModule.toggled(module.name, module.isEnabled());
        }
        // never lock anyone out of the menu: with its key cleared, Right Shift still opens it
        Setting.Key open = unblonded.anthrax.modules.client.ClientSettingsModule.INSTANCE.menuKey();
        if (!open.bound() && key == GLFW.GLFW_KEY_RIGHT_SHIFT) { open.press(); return; }
        // the sub module menu wins over other action binds on the same key (it lists those screens anyway)
        Setting.Key menu = unblonded.anthrax.modules.client.ClientSettingsModule.INSTANCE.subMenuKey();
        if (menu.bound() && menu.get() == key) { menu.press(); return; }
        // action binds set inside module settings (open a screen and the like); these work with the module off too
        for (GuiModule module : ModuleRegistry.all())
            for (Setting setting : module.settings())
                if (setting instanceof Setting.Key bind && bind.bound() && bind.get() == key) bind.press();
    }

    /** Short, readable key label such as "R", "F4", "LSHIFT" or "MB4". */
    public static String keyName(int key) {
        if (key == GuiModule.NO_KEY) return "NONE";
        if (isMouse(key)) return switch (key - MOUSE_BASE) {
            case 2 -> "MIDDLE";
            case 3 -> "MB4";
            case 4 -> "MB5";
            default -> "MB" + (key - MOUSE_BASE + 1);
        };
        String glfw = GLFW.glfwGetKeyName(key, 0);
        if (glfw != null && !glfw.isBlank()) return glfw.toUpperCase(java.util.Locale.ROOT);
        String name = InputConstants.Type.KEYSYM.getOrCreate(key).getName();
        // Translation keys look like "key.keyboard.left.shift"; keep the meaningful tail.
        String tail = name.startsWith("key.keyboard.") ? name.substring("key.keyboard.".length()) : name;
        tail = tail.replace("left.", "L").replace("right.", "R").replace("keypad.", "NUM").replace('.', ' ');
        return tail.toUpperCase(java.util.Locale.ROOT);
    }
}
