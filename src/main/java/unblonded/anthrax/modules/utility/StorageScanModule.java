package unblonded.anthrax.modules.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponentPatch;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Search text and highlight matching storage/inventory slots while a container screen is open. */
public final class StorageScanModule extends FeatureModule {
    public static final StorageScanModule INSTANCE = new StorageScanModule();
    private final Setting.Text search = setting(Setting.Text.standalone("Search for", "", 512));
    private final Setting.Mode color = setting(Setting.Mode.standalone("Highlight color", "Red", "Red", "Green", "Blue", "Purple", "White"));
    private record SearchKey(Item item, int count, DataComponentPatch components, String query, Object world, Object player) {}
    private static final Map<SearchKey, Boolean> MATCH_CACHE = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<SearchKey, Boolean> eldest) { return size() > 2048; }
    };
    private static Object cacheWorld;
    private static Object cachePlayer;
    private static String cacheQuery = "";
    private StorageScanModule() { super("Storage Scan", "Search box in containers: highlights matching items live", "Utility", false); }

    public static boolean matches(ItemStack stack) {
        String query = INSTANCE.search.get().trim().toLowerCase(Locale.ROOT);
        if (!INSTANCE.isEnabled() || query.isEmpty() || stack.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        synchronized (MATCH_CACHE) {
            if (cacheWorld != mc.level || cachePlayer != mc.player || !cacheQuery.equals(query)) {
                MATCH_CACHE.clear();
                cacheWorld = mc.level;
                cachePlayer = mc.player;
                cacheQuery = query;
            }
        }
        SearchKey key = new SearchKey(stack.getItem(), stack.getCount(), stack.getComponentsPatch(), query, mc.level, mc.player);
        synchronized (MATCH_CACHE) {
            Boolean cached = MATCH_CACHE.get(key);
            if (cached != null) return cached;
        }
        boolean match = stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
        if (!match) {
            for (Component line : stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.ADVANCED)) {
                if (line.getString().toLowerCase(Locale.ROOT).contains(query)) { match = true; break; }
            }
        }
        synchronized (MATCH_CACHE) { MATCH_CACHE.put(key, match); }
        return match;
    }

    /** The search text (edited from the box in container screens). */
    public static String query() { return INSTANCE.search.get(); }
    public static void setQuery(String text) { INSTANCE.search.set(text); }

    public static int highlightColor() {
        return switch (INSTANCE.color.get()) {
            case "Green" -> 0x6655FF55;
            case "Blue" -> 0x665599FF;
            case "Purple" -> 0x66CC66FF;
            case "White" -> 0x66FFFFFF;
            default -> 0x66FF4444;
        };
    }
}
