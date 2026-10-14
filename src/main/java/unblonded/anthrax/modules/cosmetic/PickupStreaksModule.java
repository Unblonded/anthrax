package unblonded.anthrax.modules.cosmetic;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayList;
import java.util.List;

/** "+12 Cobblestone" pop-ups as you pick things up; repeated pickups of the same item add up into a streak. */
public final class PickupStreaksModule extends CosmeticModule {
    public static final PickupStreaksModule INSTANCE = new PickupStreaksModule();
    private final Setting.Num window = setting(Setting.Num.standalone("Combine window (s)", 2.5, 0.5, 8, 0.5), "Pickups of the same item within this time add to one streak.");
    private final Setting.Num max = setting(Setting.Num.standalone("Max shown", 5, 1, 10, 1), "");
    private final Setting.Bool xp = setting(Setting.Bool.standalone("Experience", true), "Show experience orbs too.");
    private final Setting.Bool sound = setting(Setting.Bool.standalone("Streak sound", false), "A rising note as a streak grows.");

    public static final class Streak {
        public final ItemStack icon;
        public final String name;
        public int count;
        public long last;
        public final long born = System.currentTimeMillis();
        Streak(ItemStack icon, String name) { this.icon = icon; this.name = name; }
    }
    private final List<Streak> streaks = new ArrayList<>();

    private PickupStreaksModule() { super("Pickup Streaks", "Pop-ups like +12 Cobblestone as you pick things up, adding up repeated pickups into streaks"); }

    /** From the take-item packet, only for pickups by you (CosmeticPacketMixin). */
    public void onPickup(Entity from, int amount) {
        if (!isEnabled()) return;
        ItemStack icon;
        String name;
        int n;
        if (from instanceof ItemEntity ie) {
            icon = ie.getItem().copyWithCount(1);
            name = ie.getItem().getHoverName().getString();
            n = amount > 0 ? amount : ie.getItem().getCount();
        } else if (from instanceof ExperienceOrb orb && xp.get()) {
            icon = new ItemStack(Items.EXPERIENCE_BOTTLE);
            name = "XP";
            n = orb.getValue();
        } else return;
        long now = System.currentTimeMillis();
        Item item = icon.getItem();
        Streak s = null;
        for (Streak st : streaks) if (st.icon.getItem() == item && st.name.equals(name) && now - st.last < window.get() * 1000) { s = st; break; }
        if (s == null) { s = new Streak(icon, name); streaks.add(s); }
        s.count += n;
        s.last = now;
        if (sound.get()) Fx.ui(Fx.namedSound("Pling"), Math.min(2f, 0.8f + s.count * 0.02f), 0.35f);
        while (streaks.size() > (int) max.get()) streaks.removeFirst();
    }

    /** Live streaks for the HUD, dropping ones that have faded. */
    public List<Streak> streaks() {
        if (!isEnabled()) return List.of();
        long now = System.currentTimeMillis();
        streaks.removeIf(s -> now - s.last > window.get() * 1000 + 600);
        return streaks;
    }

    public double windowMs() { return window.get() * 1000; }

    @Override protected void onToggle(boolean enabled) { streaks.clear(); }
}
