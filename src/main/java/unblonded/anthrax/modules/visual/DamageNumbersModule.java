package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Damage numbers where hits land, sized and coloured by the damage. Reads the health every living entity in view
 * reports to the client, so it works for anyone's hits, not only yours. Drawn by the HUD (HudExtras).
 */
public final class DamageNumbersModule extends FeatureModule {
    public static final DamageNumbersModule INSTANCE = new DamageNumbersModule();

    /** One floating number. */
    public static final class Popup {
        public final Vec3 pos;
        public final float amount;
        public final boolean heal;
        public final long born = System.currentTimeMillis();
        public final double drift;
        Popup(Vec3 pos, float amount, boolean heal, double drift) { this.pos = pos; this.amount = amount; this.heal = heal; this.drift = drift; }
    }

    private final Setting.Num range = setting(Setting.Num.standalone("Range", 32, 8, 96, 4), "Only show numbers for entities this close.");
    private final Setting.Num lifetime = setting(Setting.Num.standalone("Lifetime (s)", 1.2, 0.4, 4, 0.1), "How long each number floats.");
    private final Setting.Bool heals = setting(Setting.Bool.standalone("Show healing", false), "Green numbers when health goes up.");
    private final Setting.Bool self = setting(Setting.Bool.standalone("Show your own", false), "Numbers for damage you take.");
    private final Setting.Bool hearts = setting(Setting.Bool.standalone("In hearts", false), "Divide by two so the numbers read as hearts.");
    private final Setting.Mode size = setting(Setting.Mode.standalone("Size", "By damage", "By damage", "Fixed"), "Bigger hits get bigger numbers, or keep them all the same.");
    private final Map<Integer, Float> lastHealth = new HashMap<>();
    private final List<Popup> popups = new ArrayList<>();
    private final java.util.Random rnd = new java.util.Random();

    private DamageNumbersModule() { super("Damage Numbers", "Shows damage numbers where hits land, sized and coloured by how much damage was dealt", "Visuals", false); }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { lastHealth.clear(); popups.clear(); return; }
        Map<Integer, Float> seen = new HashMap<>();
        double r2 = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || mc.player.distanceToSqr(e) > r2) continue;
            if (e == mc.player && !self.get()) continue;
            float hp = living.getHealth() + living.getAbsorptionAmount();
            Float before = lastHealth.get(e.getId());
            seen.put(e.getId(), hp);
            if (before == null) continue;
            float delta = before - hp;
            if (delta > 0.05f || (heals.get() && delta < -0.05f)) {
                Vec3 at = e.position().add((rnd.nextDouble() - 0.5) * e.getBbWidth(), e.getBbHeight() * (0.6 + rnd.nextDouble() * 0.4), (rnd.nextDouble() - 0.5) * e.getBbWidth());
                popups.add(new Popup(at, Math.abs(delta), delta < 0, rnd.nextDouble() - 0.5));
            }
        }
        lastHealth.clear();
        lastHealth.putAll(seen);
        long now = System.currentTimeMillis();
        popups.removeIf(p -> now - p.born > lifetime.get() * 1000);
        if (popups.size() > 80) popups.subList(0, popups.size() - 80).clear();
    }

    @Override protected void onToggle(boolean enabled) { lastHealth.clear(); popups.clear(); }

    public List<Popup> popups() { return popups; }
    public double lifetimeMs() { return lifetime.get() * 1000; }
    public boolean hearts() { return hearts.get(); }
    public boolean scaled() { return size.get().equals("By damage"); }
}
