package unblonded.anthrax.modules;

import unblonded.anthrax.modules.combat.CrystalAuraModule;
import unblonded.anthrax.modules.combat.AutoTotemModule;
import unblonded.anthrax.modules.combat.AutoCrystalModule;
import unblonded.anthrax.modules.combat.CrystalSpamModule;
import unblonded.anthrax.modules.combat.AutoAnchorModule;
import unblonded.anthrax.modules.combat.AimAssistModule;
import unblonded.anthrax.modules.combat.SelfCrystalModule;
import unblonded.anthrax.modules.mining.AirUnderCheckModule;
import unblonded.anthrax.modules.mining.SeedRayModule;
import unblonded.anthrax.modules.utility.AutoDisconnectModule;
import unblonded.anthrax.modules.utility.ChatFilterModule;
import unblonded.anthrax.modules.utility.StorageScanModule;
import unblonded.anthrax.modules.utility.LoadoutManagerModule;
import unblonded.anthrax.modules.utility.AutoSellModule;
import unblonded.anthrax.modules.utility.FpsChartModule;
import unblonded.anthrax.modules.utility.InteractionCancelerModule;
import unblonded.anthrax.modules.utility.TotemNotifierModule;
import unblonded.anthrax.modules.visual.TimeChangerModule;
import unblonded.anthrax.modules.visual.PlayerListModule;
import unblonded.anthrax.modules.visual.FontSizeModule;
import unblonded.anthrax.modules.visual.CosmicCrosshairModule;
import unblonded.anthrax.modules.visual.NoRenderModule;
import unblonded.anthrax.modules.visual.HandRenderModule;
import unblonded.anthrax.modules.visual.BlockEspModule;
import unblonded.anthrax.modules.visual.PlayerEspModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
public final class ModuleRegistry {
    private static final List<GuiModule> ALL = new ArrayList<>();
    private static List<String> CATEGORIES = List.of();
    private static Map<String, List<GuiModule>> BY_CATEGORY = Map.of();

    static {
        CrystalAuraModule ca = CrystalAuraModule.INSTANCE;
        register(new GuiModule("Crystal Aura", "Anthrax crystal targeting", "Combat",
                ca::isEnabled, ca::setEnabled)
                .add(new Setting.Num("Smoothing", 0.05, 0.8, 0.01, ca::getSmoothing, v -> ca.setSmoothing((float) v)))
                .add(new Setting.Num("Step interval", 1, 20, 1, ca::getStepMs, v -> ca.setStepMs((int) Math.round(v))))
                .add(new Setting.Num("Placement delay", 0, 10, 1, ca::getTickDelay, v -> ca.setTickDelay((int) Math.round(v)))));
        addFeature(new AutoDisconnectModule());
        addFeature(new AutoTotemModule());
        addFeature(new AutoCrystalModule());
        addFeature(new CrystalSpamModule());
        addFeature(new AimAssistModule());
        addFeature(new AutoAnchorModule());
        addFeature(new SelfCrystalModule());
        addFeature(new AirUnderCheckModule());
        addFeature(SeedRayModule.INSTANCE);
        addFeature(new TimeChangerModule());
        addFeature(new PlayerListModule());
        addFeature(BlockEspModule.INSTANCE);
        addFeature(PlayerEspModule.INSTANCE);
        addFeature(FontSizeModule.INSTANCE);
        addFeature(CosmicCrosshairModule.INSTANCE);
        addFeature(NoRenderModule.INSTANCE);
        addFeature(HandRenderModule.INSTANCE);
        addFeature(new TotemNotifierModule());
        addFeature(new InteractionCancelerModule());
        addFeature(ChatFilterModule.INSTANCE);
        addFeature(StorageScanModule.INSTANCE);
        addFeature(LoadoutManagerModule.INSTANCE);
        addFeature(AutoSellModule.INSTANCE);
        addFeature(new FpsChartModule());
    }

    private ModuleRegistry() {}

    public static void register(GuiModule module) {
        ALL.add(module);
        refreshCategories();
    }

    private static void addFeature(FeatureModule feature) {
        FeatureManager.register(feature);
        register(feature.asGuiModule());
    }

    public static List<GuiModule> all() { return List.copyOf(ALL); }

    public static List<String> categories() { return CATEGORIES; }

    private static void refreshCategories() {
        Map<String, List<GuiModule>> grouped = new LinkedHashMap<>();
        for (GuiModule module : ALL) {
            grouped.computeIfAbsent(module.category, ignored -> new ArrayList<>()).add(module);
        }
        Map<String, List<GuiModule>> immutableGroups = new LinkedHashMap<>();
        grouped.forEach((category, modules) -> immutableGroups.put(category, List.copyOf(modules)));
        BY_CATEGORY = Collections.unmodifiableMap(immutableGroups);
        CATEGORIES = List.copyOf(grouped.keySet());
    }

    public static List<GuiModule> inCategory(String category) {
        return BY_CATEGORY.getOrDefault(category, List.of());
    }
}
