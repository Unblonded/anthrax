package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Counts totem pops per player from the server's "totem used" entity event (id 35).
 * This is exact, unlike guessing from offhand contents and health. Client thread only.
 */
public final class TotemPops {
    public static final byte TOTEM_EVENT = 35;
    private static final Map<UUID, Integer> POPS = new HashMap<>();
    private static final List<BiConsumer<Player, Integer>> LISTENERS = new ArrayList<>();
    private static Object level;

    private TotemPops() {}

    /** Listener receives the player and their pop count in this life. */
    public static void listen(BiConsumer<Player, Integer> listener) { LISTENERS.add(listener); }

    public static void onEntityEvent(Entity entity, byte event) {
        if (event != TOTEM_EVENT || !(entity instanceof Player player)) return;
        resetIfLevelChanged();
        int count = POPS.merge(player.getUUID(), 1, Integer::sum);
        for (BiConsumer<Player, Integer> listener : LISTENERS) listener.accept(player, count);
    }

    public static int pops(Player player) {
        resetIfLevelChanged();
        return POPS.getOrDefault(player.getUUID(), 0);
    }

    /** Forget counts of players who died or left so the next life starts at zero. */
    public static void tick(Minecraft client) {
        resetIfLevelChanged();
        if (client.level == null || POPS.isEmpty()) return;
        POPS.keySet().removeIf(id -> {
            Player player = client.level.getPlayerByUUID(id);
            return player == null || player.isDeadOrDying();
        });
    }

    private static void resetIfLevelChanged() {
        Object current = Minecraft.getInstance().level;
        if (current != level) {
            level = current;
            POPS.clear();
        }
    }
}
