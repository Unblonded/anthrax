package unblonded.anthrax.modules.utility;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import unblonded.anthrax.gui.Notifications;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Save, update, apply, inspect by name, and delete the slot/item loadouts from Packet Edit. */
public final class LoadoutManagerModule extends FeatureModule {
    public static final LoadoutManagerModule INSTANCE = new LoadoutManagerModule();
    private final Setting.Text name = setting(Setting.Text.standalone("New loadout name", "", 32));
    private final Setting.Text selected = setting(Setting.Text.standalone("Selected loadout", "", 32));
    private final Setting.Num moveDelay = setting(Setting.Num.standalone("Move delay (ms)", 100, 0, 1000, 25));
    private final Setting.Action cycle = setting(new Setting.Action("Next saved loadout", this::cycleSelected));
    private final Setting.Action save = setting(new Setting.Action("Save current", this::saveCurrent));
    private final Setting.Action apply = setting(new Setting.Action("Apply selected", this::applySelected));
    private final Setting.Action update = setting(new Setting.Action("Update selected", this::updateSelected));
    private final Setting.Action delete = setting(new Setting.Action("Delete selected", this::deleteSelected));
    private final Setting.Action clear = setting(new Setting.Action("Clear all", this::clearAll));
    private final Map<String, Map<Integer, String>> loadouts = new LinkedHashMap<>();
    private final Queue<Swap> swaps = new ArrayDeque<>();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("anthrax-loadouts.properties");
    private long nextSwap;
    private net.minecraft.client.player.LocalPlayer applyOwner;
    private net.minecraft.client.multiplayer.ClientLevel applyWorld;

    private LoadoutManagerModule() {
        super("Loadout Manager", "Save and apply named item-slot layouts", "Utility", false);
        load();
        ClientTickEvents.END_CLIENT_TICK.register(this::tickApply);
    }

    private void saveCurrent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || name.get().isBlank()) { notify("Enter a loadout name first"); return; }
        Map<Integer, String> slots = new LinkedHashMap<>();
        for (int i=0; i<mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) slots.put(i, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        if (slots.isEmpty()) { notify("Cannot save an empty inventory"); return; }
        loadouts.put(name.get().trim(), slots);
        selected.set(name.get().trim());
        persist(); Notifications.success("Loadout", "Saved " + selected.get());
    }

    private void updateSelected() {
        if (!loadouts.containsKey(selected.get())) { notify("Pick a saved loadout first"); return; }
        name.set(selected.get()); saveCurrent();
    }
    private void deleteSelected() {
        if (loadouts.remove(selected.get()) == null) { notify("Pick a saved loadout first"); return; }
        persist(); notify("Deleted loadout: " + selected.get()); selected.set("");
    }

    private void cycleSelected() {
        if (loadouts.isEmpty()) { notify("No saved loadouts yet"); return; }
        List<String> names = new ArrayList<>(loadouts.keySet());
        int next = (names.indexOf(selected.get()) + 1) % names.size();
        selected.set(names.get(next));
        notify("Selected " + selected.get() + " (" + (next + 1) + "/" + names.size() + ")");
    }
    private void clearAll() { loadouts.clear(); persist(); selected.set(""); notify("Cleared saved loadouts"); }

    private void applySelected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        Map<Integer, String> target = loadouts.get(selected.get());
        if (target == null) { notify("Loadout not found: " + selected.get()); return; }
        swaps.clear();
        applyOwner = mc.player;
        applyWorld = mc.level;
        // Plan against a simulated inventory: every swap moves two items, so later moves must see
        // where earlier moves put things. Slots already satisfied are never used as a source.
        int size = Math.min(41, mc.player.getInventory().getContainerSize());
        String[] simulated = new String[size];
        for (int i = 0; i < size; i++) simulated[i] = id(mc.player.getInventory().getItem(i));
        Set<Integer> done = new HashSet<>();
        for (var entry : target.entrySet()) {
            int dest = entry.getKey();
            if (dest < size && simulated[dest].equals(entry.getValue())) done.add(dest);
        }
        for (var entry : target.entrySet()) {
            int dest = entry.getKey();
            if (dest >= size || done.contains(dest)) continue;
            for (int src = 0; src < size; src++) {
                if (src == dest || done.contains(src) || !simulated[src].equals(entry.getValue())) continue;
                swaps.add(new Swap(src, dest));
                String moved = simulated[dest];
                simulated[dest] = simulated[src];
                simulated[src] = moved;
                done.add(dest);
                break;
            }
        }
        nextSwap = 0;
        if (swaps.isEmpty()) Notifications.success("Loadout", selected.get() + " is already applied");
        else Notifications.info("Loadout", "Applying " + selected.get() + " (" + swaps.size() + " moves)");
    }

    private void tickApply(Minecraft mc) {
        if (swaps.isEmpty()) return;
        if (mc.player == null || mc.gameMode == null || mc.level == null
                || mc.player != applyOwner || mc.level != applyWorld) {
            swaps.clear(); applyOwner = null; applyWorld = null;
            return;
        }
        if (System.currentTimeMillis() < nextSwap) return;
        // Wait while another container is open or an item is on the cursor; clicks would go astray.
        if (mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.inventoryMenu.getCarried().isEmpty()) return;
        Swap swap = swaps.remove();
        int from = menuSlot(swap.from), to = menuSlot(swap.to), menu = mc.player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(menu, from, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu, to, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu, from, 0, ContainerInput.PICKUP, mc.player);
        nextSwap = System.currentTimeMillis() + (long)moveDelay.get();
        if (swaps.isEmpty()) {
            applyOwner = null; applyWorld = null;
            Notifications.success("Loadout", "Applied " + selected.get());
        }
    }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled) { swaps.clear(); applyOwner = null; applyWorld = null; }
    }

    private void load() {
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            p.load(reader);
            for (String encodedName : p.stringPropertyNames()) {
                try {
                    String name = new String(Base64.getUrlDecoder().decode(encodedName), java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (name.isEmpty() || name.length() > 32) continue;
                    Map<Integer, String> slots = new LinkedHashMap<>();
                    for (String pair : p.getProperty(encodedName, "").split(",")) {
                        String[] parts = pair.split("=", 2);
                        if (parts.length != 2) continue;
                        int index = Integer.parseInt(parts[0]);
                        Identifier itemId = Identifier.tryParse(parts[1]);
                        if (index >= 0 && index < 41 && itemId != null && BuiltInRegistries.ITEM.containsKey(itemId))
                            slots.put(index, itemId.toString());
                    }
                    if (!slots.isEmpty()) loadouts.put(name, slots);
                } catch (RuntimeException malformedEntry) {
                    // Skip just the damaged entry; retain other valid loadouts.
                }
            }
        } catch (Exception exception) {
            System.err.println("Anthrax: could not read saved loadouts: " + exception.getMessage());
        }
    }

    private void persist() {
        Properties p = new Properties();
        loadouts.forEach((key, slots) -> p.setProperty(Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                slots.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).reduce((a,b) -> a + "," + b).orElse("")));
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) { p.store(writer, "Anthrax loadouts"); }
            try { Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (Exception e) { notify("Could not save loadouts"); }
    }

    private static String id(ItemStack stack) { return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static int menuSlot(int inventoryIndex) {
        if (inventoryIndex < 9) return inventoryIndex + 36;
        if (inventoryIndex <= 35) return inventoryIndex;
        if (inventoryIndex == 36) return 8;
        if (inventoryIndex == 37) return 7;
        if (inventoryIndex == 38) return 6;
        if (inventoryIndex == 39) return 5;
        if (inventoryIndex == 40) return 45;
        return inventoryIndex;
    }
    private static void notify(String text) { Notifications.info("Loadout", text); }
    private record Swap(int from, int to) {}
}
