package unblonded.anthrax.gui;


import unblonded.anthrax.CrystalAuraModule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Registry of everything the GUI shows. Categories (sidebar tabs) are created
 * automatically from the modules you register.
 *
 * To add a module, register it in the static block below:
 *
 *   register(new GuiModule("Velocity", "Reduce knockback", "Movement", vel::isEnabled, vel::setEnabled)
 *       .add(new Setting.Num("Horizontal", 0, 100, 1, vel::getH, v -> vel.setH((int) Math.round(v))))
 *       .add(new Setting.Bool("Only when hit", vel::isOnlyHit, vel::setOnlyHit))
 *       .add(Setting.Mode.standalone("Mode", "Cancel", "Cancel", "Reduce", "Jump")));
 */
public final class Modules {
    private static final List<GuiModule> ALL = new ArrayList<>();

    static {
        CrystalAuraModule ca = CrystalAuraModule.INSTANCE;
        register(new GuiModule("Crystal Aura", "Auto target after placement", "Combat",
                ca::isEnabled, ca::setEnabled)
                .add(new Setting.Num("Smoothing", 0.05, 0.8, 0.01, ca::getSmoothing, v -> ca.setSmoothing((float) v)))
                .add(new Setting.Num("Step interval", 1, 20, 1, ca::getStepMs, v -> ca.setStepMs((int) Math.round(v))))
                .add(new Setting.Num("Placement delay", 0, 10, 1, ca::getTickDelay, v -> ca.setTickDelay((int) Math.round(v)))));
    }

    private Modules() {}

    public static void register(GuiModule module) { ALL.add(module); }

    public static List<GuiModule> all() { return ALL; }

    public static List<String> categories() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (GuiModule m : ALL) out.add(m.category);
        return new ArrayList<>(out);
    }

    public static List<GuiModule> inCategory(String category) {
        List<GuiModule> out = new ArrayList<>();
        for (GuiModule m : ALL) if (m.category.equals(category)) out.add(m);
        return out;
    }
}
