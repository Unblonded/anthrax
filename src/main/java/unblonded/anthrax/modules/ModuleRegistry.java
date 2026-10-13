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
import unblonded.anthrax.modules.utility.RenderMethodModule;
import unblonded.anthrax.modules.utility.TotemNotifierModule;
import unblonded.anthrax.modules.visual.TimeChangerModule;
import unblonded.anthrax.modules.visual.PlayerListModule;
import unblonded.anthrax.modules.visual.FontSizeModule;
import unblonded.anthrax.modules.visual.CosmicCrosshairModule;
import unblonded.anthrax.modules.visual.NoRenderModule;
import unblonded.anthrax.modules.visual.HandRenderModule;
import unblonded.anthrax.modules.visual.BlockEspModule;
import unblonded.anthrax.modules.visual.PlayerEspModule;
import unblonded.anthrax.modules.visual.WorldThemeModule;
import unblonded.anthrax.modules.client.HudModule;

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

    /** Sidebar order; categories not listed here follow in registration order. */
    private static final List<String> CATEGORY_ORDER = List.of("Combat", "HVH", "Movement", "Player", "Mining", "World", "Visuals", "Utility", "Client");

    static {
        addFeature(CrystalAuraModule.INSTANCE);
        addFeature(new AutoTotemModule());
        addFeature(new AutoCrystalModule());
        addFeature(new CrystalSpamModule());
        addFeature(new AimAssistModule());
        addFeature(new AutoAnchorModule());
        addFeature(new SelfCrystalModule());
        addFeature(new unblonded.anthrax.modules.combat.AutoArmorModule());
        addFeature(unblonded.anthrax.modules.combat.KillAuraModule.INSTANCE);
        addFeature(new unblonded.anthrax.modules.combat.TriggerBotModule());
        addFeature(unblonded.anthrax.modules.combat.AutoWeaponModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.combat.VelocityModule.INSTANCE);
        addFeature(new unblonded.anthrax.modules.combat.AutoEatModule());
        addFeature(new unblonded.anthrax.modules.combat.SurroundModule());
        addFeature(unblonded.anthrax.modules.combat.TargetHudModule.INSTANCE);
        addFeature(new TotemNotifierModule());
        addFeature(AirUnderCheckModule.INSTANCE);
        addFeature(SeedRayModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.AmethystEspModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.BaseFinderModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.WaypointsModule.INSTANCE);
        addFeature(BlockEspModule.INSTANCE);
        addFeature(PlayerEspModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.NametagsModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.TracersModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.StorageEspModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.HoleEspModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.TrajectoriesModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.LogoutSpotsModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.BreadcrumbsModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.FullbrightModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.FreecamModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.ZoomModule.INSTANCE);
        addFeature(PlayerListModule.INSTANCE);
        addFeature(WorldThemeModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.NewChunksModule.INSTANCE);
        addFeature(TimeChangerModule.INSTANCE);
        addFeature(CosmicCrosshairModule.INSTANCE);
        addFeature(NoRenderModule.INSTANCE);
        addFeature(HandRenderModule.INSTANCE);
        addFeature(new AutoDisconnectModule());
        addFeature(InteractionCancelerModule.INSTANCE);
        addFeature(new RenderMethodModule());
        addFeature(ChatFilterModule.INSTANCE);
        addFeature(StorageScanModule.INSTANCE);
        addFeature(LoadoutManagerModule.INSTANCE);
        addFeature(AutoSellModule.INSTANCE);
        addFeature(FpsChartModule.INSTANCE);
        addFeature(HudModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.client.FriendsModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.client.CommandsModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.client.ProfilesModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.client.ClientSettingsModule.INSTANCE);
        addFeature(FontSizeModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.AutoWalkModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.StepModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.HighJumpModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.SpeedModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.FlyModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.SpiderModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.FastLadderModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.GlideModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.FastFallModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.ReverseStepModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.ParkourModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.JesusModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.EntitySpeedModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.BoatFlyModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.ElytraBoostModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.LongJumpModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.movement.SafeWalkModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.AutoRespawnModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.AutoToolModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.AutoFishModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.ChestStealerModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.AntiAfkModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.CoordSnapperModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.KeyPearlModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.AutoMendModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.DurabilityAlertModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.RefillModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.NotifierModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.player.AutoReconnectModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.AutoFarmModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.NukerModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.BedBreakerModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.ScaffoldModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.TowerModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.AirPlaceModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.world.SpeedMineModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.combat.AutoClickerModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.combat.ReachModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.combat.ShieldBreakerModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.combat.AutoBlockModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.FreelookModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.ItemEspModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.SpawnerNotifierModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.hvh.AutoTrapModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.hvh.AutoWebModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.hvh.CoverPlannerModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.hvh.HoleFillerModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.hvh.PearlTrapModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.hvh.TargetStrafeModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.hvh.TpAuraModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.hvh.TrapEarlyWarningModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.combat.BowAimbotModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.combat.CriticalsModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.BetterTooltipsModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.BlockHighlightModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.ChamsModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.ChatAnimationsModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.DamageNumbersModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.MapModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.MotionBlurModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.MusicModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.NoBlockRotationModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.OreEspModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.SmoothHotbarModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.StashFinderModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.TargetEspModule.INSTANCE);
        addFeature(unblonded.anthrax.modules.visual.XrayModule.INSTANCE);
    }

    private ModuleRegistry() {}

    public static void register(GuiModule module) {
        ALL.add(module);
        allView = null;
        refreshCategories();
    }

    private static void addFeature(FeatureModule feature) {
        FeatureManager.register(feature);
        register(feature.asGuiModule());
    }

    private static List<GuiModule> allView;

    public static List<GuiModule> all() {
        if (allView == null) allView = List.copyOf(ALL);
        return allView;
    }

    public static List<String> categories() { return CATEGORIES; }

    private static void refreshCategories() {
        Map<String, List<GuiModule>> grouped = new LinkedHashMap<>();
        for (GuiModule module : ALL) {
            grouped.computeIfAbsent(module.category, ignored -> new ArrayList<>()).add(module);
        }
        Map<String, List<GuiModule>> immutableGroups = new LinkedHashMap<>();
        grouped.forEach((category, modules) -> immutableGroups.put(category, List.copyOf(modules)));
        BY_CATEGORY = Collections.unmodifiableMap(immutableGroups);
        List<String> ordered = new ArrayList<>(grouped.keySet());
        ordered.sort(java.util.Comparator.comparingInt(category -> {
            int index = CATEGORY_ORDER.indexOf(category);
            return index < 0 ? CATEGORY_ORDER.size() : index;
        }));
        CATEGORIES = List.copyOf(ordered);
    }

    public static List<GuiModule> inCategory(String category) {
        return BY_CATEGORY.getOrDefault(category, List.of());
    }
}
