package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.mixin.GameModeAccessor;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

/** Shows how far along the block you are mining is: a box that grows, fills or shrinks with the break progress. */
public final class BreakAnimationModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final BreakAnimationModule INSTANCE = new BreakAnimationModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Grow", "Grow", "Shrink", "Fill up", "Outline"),
            "Grow swells a box from the centre, Shrink closes in from the edges, Fill up rises from the bottom, Outline only pulses the edges.");
    private final Setting.Mode colourMode = setting(Setting.Mode.standalone("Colour by", "Progress", "Progress", "Palette"),
            "Progress fades red to green as the block nears breaking.");
    private final Palette palette = palette("Theme");
    private final Setting.Num fill = setting(Setting.Num.standalone("Fill opacity", 30, 0, 100, 5), "How solid the box face is.");
    private final Setting.Num width = setting(Setting.Num.standalone("Line width", 2, 1, 6, 0.5), "");
    private float shown;
    private BlockPos lastPos;

    private BreakAnimationModule() { super("Break Animation", "Animates a box on the block you are mining that follows your break progress"); }

    @Override public boolean active() { return isEnabled() && Minecraft.getInstance().gameMode != null && Minecraft.getInstance().gameMode.isDestroying(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        GameModeAccessor gm = (GameModeAccessor) mc.gameMode;
        BlockPos pos = gm.anthrax$getDestroyBlockPos();
        if (pos == null || mc.level.getBlockState(pos).isAir()) return;
        float target = Math.max(0, Math.min(1, gm.anthrax$getDestroyProgress()));
        if (!pos.equals(lastPos) || target < shown) shown = target;   // new block: start over
        shown += (target - shown) * 0.35f;                             // ease between ticks
        lastPos = pos.immutable();
        float p = shown;
        int rgb = colourMode.get().equals("Progress") ? mix(0xFF3B3B, 0x3BFF6A, p) : palette.rgb(p);
        AABB full = mc.level.getBlockState(pos).getShape(mc.level, pos).isEmpty() ? new AABB(pos) : mc.level.getBlockState(pos).getShape(mc.level, pos).bounds().move(pos);
        Vec3 c = full.getCenter();
        AABB box = switch (style.get()) {
            case "Shrink" -> scaled(full, c, 1 - p * 0.9);
            case "Fill up" -> new AABB(full.minX, full.minY, full.minZ, full.maxX, full.minY + (full.maxY - full.minY) * p, full.maxZ);
            case "Outline" -> full.inflate(0.004 + 0.03 * Math.sin(System.currentTimeMillis() / 90.0) * p);
            default -> scaled(full, c, 0.1 + p * 0.9);
        };
        int fa = style.get().equals("Outline") ? 0 : (int) Math.round(fill.get() / 100 * 255);
        // drawn on top: a box inside the block would otherwise be hidden by the block itself
        Gizmos.cuboid(box, GizmoStyle.strokeAndFill(WorldOverlays.argb(230, rgb), (float) (double) width.get(), WorldOverlays.argb(fa, rgb))).setAlwaysOnTop();
    }

    private static AABB scaled(AABB b, Vec3 c, double s) {
        double hx = (b.maxX - b.minX) / 2 * s, hy = (b.maxY - b.minY) / 2 * s, hz = (b.maxZ - b.minZ) / 2 * s;
        return new AABB(c.x - hx, c.y - hy, c.z - hz, c.x + hx, c.y + hy, c.z + hz);
    }
}
