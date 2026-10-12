package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.combat.TotemPops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Nearby players with distance, health, armor, held item and totem pops, drawn by the HUD. */
public final class PlayerListModule extends FeatureModule {
    public static final PlayerListModule INSTANCE = new PlayerListModule();

    public record Entry(String name, double distance, float health, float maxHealth, int armor,
                        ItemStack mainHand, int pops, boolean sneaking, boolean sprinting) {}

    private final Setting.Bool freeze = setting(Setting.Bool.standalone("Freeze list", false));
    private final Setting.Num maxEntries = setting(Setting.Num.standalone("Max players", 8, 1, 20, 1));
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 128, 8, 512, 8));
    private List<Entry> current = List.of();
    private List<Entry> frozen = List.of();
    private boolean wasFrozen;

    private PlayerListModule() {
        super("Show Player List", "Nearby players with distance, health, armor, items and pops", "Visuals", false);
    }

    @Override public void tick(Minecraft client) {
        if (client.player == null || client.level == null) { current = List.of(); frozen = List.of(); wasFrozen = false; return; }
        List<Entry> entries = new ArrayList<>();
        double rangeSq = range.get() * range.get();
        for (Player other : client.level.players()) {
            if (other == client.player || other.isRemoved() || other.isSpectator()) continue;
            double distSq = client.player.distanceToSqr(other);
            if (distSq > rangeSq) continue;
            entries.add(new Entry(other.getName().getString(), Math.sqrt(distSq),
                    other.getHealth() + other.getAbsorptionAmount(), other.getMaxHealth(), other.getArmorValue(),
                    other.getMainHandItem().copy(), TotemPops.pops(other), other.isCrouching(), other.isSprinting()));
        }
        entries.sort(Comparator.comparingDouble(Entry::distance));
        if (entries.size() > (int) maxEntries.get()) entries = entries.subList(0, (int) maxEntries.get());
        current = List.copyOf(entries);
        boolean freezeNow = freeze.get();
        if (freezeNow && !wasFrozen) frozen = current;
        else if (!freezeNow) frozen = List.of();
        wasFrozen = freezeNow;
    }

    public List<Entry> entries() { return freeze.get() ? frozen : current; }

    @Override public String hudInfo() { return Integer.toString(current.size()); }
}
