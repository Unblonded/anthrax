package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.List;

/** Blocks you place pop into place: a box that grows in, drops from above or flashes, plus a little dust. */
public final class PlaceAnimationModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final PlaceAnimationModule INSTANCE = new PlaceAnimationModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Pop", "Pop", "Drop in", "Flash", "Ripple"),
            "Pop grows the box out from the centre with a little overshoot, Drop in slides it down from above, Flash lights it up, Ripple sends a ring outwards.");
    private final Palette palette = palette("Theme");
    private final Setting.Num duration = setting(Setting.Num.standalone("Duration (ms)", 300, 100, 1000, 25), "");
    private final Setting.Bool dust = setting(Setting.Bool.standalone("Dust puff", true), "A puff of the block's pieces where it lands.");

    private record Anim(BlockPos pos, int rgb, long born) {}
    private final List<Anim> anims = new ArrayList<>();

    private PlaceAnimationModule() { super("Place Animation", "Blocks you place pop, drop or flash into place"); }

    public void onPlace(BlockPos pos, BlockState state) {
        if (!isEnabled()) return;
        anims.add(new Anim(pos.immutable(), palette.rgb(), System.currentTimeMillis()));
        if (anims.size() > 48) anims.removeFirst();
        if (dust.get() && !state.isAir())
            for (int i = 0; i < 8; i++)
                Fx.particle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + Fx.RND.nextDouble(), pos.getY() + 0.05, pos.getZ() + Fx.RND.nextDouble(), 0, 0.05, 0);
    }

    @Override public boolean active() { return isEnabled() && !anims.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        long now = System.currentTimeMillis();
        double dur = duration.get();
        anims.removeIf(a -> now - a.born > dur);
        for (Anim a : anims) {
            float t = (float) ((now - a.born) / dur);
            AABB full = new AABB(a.pos);
            Vec3 c = full.getCenter();
            int alpha = Math.round(230 * (1 - t));
            switch (style.get()) {
                case "Drop in" -> {
                    double y = (1 - easeOut(t)) * 1.2;
                    Gizmos.cuboid(full.move(0, y, 0), GizmoStyle.strokeAndFill(WorldOverlays.argb(alpha, a.rgb), 2f, WorldOverlays.argb(alpha / 4, a.rgb)));
                }
                case "Flash" -> Gizmos.cuboid(full.inflate(0.01), GizmoStyle.strokeAndFill(WorldOverlays.argb(alpha, 0xFFFFFF), 2f, WorldOverlays.argb(Math.round(170 * (1 - t)), a.rgb)));
                case "Ripple" -> {
                    for (int k = 0; k < 2; k++) {
                        float tt = Math.max(0, t - k * 0.25f);
                        Gizmos.circle(new Vec3(c.x, a.pos.getY() + 0.02, c.z), (float) (0.5 + tt * 1.6), GizmoStyle.stroke(WorldOverlays.argb(Math.round(220 * (1 - tt)), a.rgb), 2.5f));
                    }
                }
                default -> {
                    double s = popScale(t);
                    double h = 0.5 * s;
                    AABB box = new AABB(c.x - h, c.y - h, c.z - h, c.x + h, c.y + h, c.z + h);
                    Gizmos.cuboid(box, GizmoStyle.strokeAndFill(WorldOverlays.argb(alpha, a.rgb), 2f, WorldOverlays.argb(alpha / 3, a.rgb))).setAlwaysOnTop();
                }
            }
        }
    }

    /** 0.3 to 1 with a small overshoot, like a spring. */
    private static double popScale(float t) {
        double x = Math.min(1, t * 1.6);
        return 0.3 + 0.7 * (1 + 2.2 * Math.pow(x - 1, 3) + 1.2 * Math.pow(x - 1, 2));
    }

    private static double easeOut(float t) { return 1 - Math.pow(1 - t, 3); }

    @Override protected void onToggle(boolean enabled) { anims.clear(); }
}
