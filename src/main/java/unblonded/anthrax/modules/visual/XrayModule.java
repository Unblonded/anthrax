package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.HashSet;
import java.util.Set;

/**
 * Hides every block except the chosen ones and lights everything (see XrayMixin). Works with Sodium, which
 * reads the same block render shape and occlusion.
 */
public final class XrayModule extends FeatureModule {
    public static final XrayModule INSTANCE = new XrayModule();
    private static final String DEFAULT = "diamond_ore, deepslate_diamond_ore, ancient_debris, emerald_ore, deepslate_emerald_ore, gold_ore, "
            + "deepslate_gold_ore, nether_gold_ore, iron_ore, deepslate_iron_ore, redstone_ore, deepslate_redstone_ore, lapis_ore, deepslate_lapis_ore, "
            + "copper_ore, deepslate_copper_ore, coal_ore, deepslate_coal_ore, nether_quartz_ore, spawner, chest, trapped_chest, barrel, ender_chest, "
            + "lava, water, bedrock, obsidian";
    private final Setting.Text blocks = setting(Setting.Text.standalone("Visible blocks", DEFAULT, 1024), "Block ids to keep, separated by commas.");
    private final Setting.Bool fluids = setting(Setting.Bool.standalone("Keep fluids", true), "Leave water and lava visible.");
    private volatile Set<Block> visible = Set.of();
    private String parsedFrom = "";

    private XrayModule() { super("Xray", "Hides every block except the ones you choose, and lights everything, so ores and the like stand out through the ground", "Visuals", false); }

    /** True when this block should disappear (called from the render shape and occlusion mixins, on any thread). */
    public static boolean hides(BlockState state) {
        XrayModule m = INSTANCE;
        if (m == null || !m.isEnabled() || state.isAir()) return false;
        if (m.fluids.get() && !state.getFluidState().isEmpty()) return false;
        return !m.visible.contains(state.getBlock());
    }

    @Override public void tick(Minecraft mc) {
        String raw = blocks.get();
        if (raw.equals(parsedFrom)) return;
        parsedFrom = raw;
        Set<Block> set = new HashSet<>();
        for (String s : raw.split(",")) {
            String id = s.trim();
            if (id.isEmpty()) continue;
            Identifier ident = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
            if (ident != null) BuiltInRegistries.BLOCK.getOptional(ident).ifPresent(set::add);
        }
        visible = Set.copyOf(set);
        reload(mc);
    }

    @Override protected void onToggle(boolean enabled) {
        parsedFrom = "";
        reload(Minecraft.getInstance());
    }

    private static void reload(Minecraft mc) { rebuildChunks(mc); }

    /** Rebuilds every chunk mesh (vanilla and Sodium both hook this), so block render changes show at once. */
    public static void rebuildChunks(Minecraft mc) {
        mc.execute(() -> {
            if (mc.levelRenderer != null && mc.level != null)
                mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
        });
    }

    public boolean fullbright() { return isEnabled(); }
}
