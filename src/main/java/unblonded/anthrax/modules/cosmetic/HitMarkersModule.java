package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import unblonded.anthrax.modules.Setting;

/** A marker around your crosshair when your hit lands, bigger and red when it kills. Drawn by the HUD. */
public final class HitMarkersModule extends CosmeticModule {
    public static final HitMarkersModule INSTANCE = new HitMarkersModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Cross", "Cross", "Circle", "Brackets", "Dot burst"), "Shape of the marker.");
    private final Palette palette = palette("White");
    private final Setting.Num size = setting(Setting.Num.standalone("Size", 1.0, 0.5, 2.5, 0.1), "");
    private final Setting.Num duration = setting(Setting.Num.standalone("Duration (ms)", 350, 100, 1000, 25), "");
    private final Setting.Bool killMarker = setting(Setting.Bool.standalone("Kill marker", true), "A larger red marker when your hit kills.");
    private final Setting.Bool onlyConfirmed = setting(Setting.Bool.standalone("Only when damage lands", true), "Waits until the target actually flinches.");

    private Entity pending;
    private long pendingAt;
    private long shownAt;
    private boolean kill;

    private HitMarkersModule() { super("Hit Markers", "Shows a marker around the crosshair when your hits land, and a red one on kills"); }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        if (!onlyConfirmed.get() || !(target instanceof LivingEntity)) { show(false); return; }
        pending = target;
        pendingAt = System.currentTimeMillis();
    }

    @Override public void tick(Minecraft mc) {
        if (pending == null) return;
        if (pending instanceof LivingEntity l && (l.hurtTime > 0 || l.isDeadOrDying())) {
            show(l.isDeadOrDying());
            pending = null;
        } else if (System.currentTimeMillis() - pendingAt > 400) pending = null;
    }

    private void show(boolean killed) { shownAt = System.currentTimeMillis(); kill = killed && killMarker.get(); }

    /** 0..1 progress of the current marker, or -1 when none is showing. */
    public float progress() {
        if (!isEnabled()) return -1;
        float t = (System.currentTimeMillis() - shownAt) / (float) (double) duration.get();
        return t >= 1 ? -1 : t;
    }

    public boolean kill() { return kill; }
    public String style() { return style.get(); }
    public float size() { return (float) (double) size.get(); }
    public int rgb() { return kill ? 0xFF3040 : palette.rgb(); }
}
