package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Outlines or fills the block you look at, with an optional info card under the crosshair (see Hud). */
public final class BlockHighlightModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final BlockHighlightModule INSTANCE = new BlockHighlightModule();
    private final Setting.Mode style = setting(Setting.Mode.standalone("Style", "Outline and fill", "Outline", "Fill", "Outline and fill"), "How the block is marked.");
    private final Setting.Mode colour = setting(Setting.Mode.standalone("Colour", "White", "White", "Cyan", "Pink", "Green", "Gold", "Rainbow"), "");
    private final Setting.Num width = setting(Setting.Num.standalone("Line width", 2, 1, 6, 0.5), "");
    private final Setting.Bool shape = setting(Setting.Bool.standalone("Follow block shape", true), "Fits the box to slabs, stairs and other partial blocks.");
    private final Setting.Bool card = setting(Setting.Bool.standalone("Info card", true), "Name, position, hardness and the right tool under your crosshair.");

    private BlockHighlightModule() { super("Block Highlight", "Outlines or fills the block you are looking at, with an optional info card", "Visuals", false); }

    public boolean showCard() { return isEnabled() && card.get(); }

    public int rgb() {
        return switch (colour.get()) {
            case "Cyan" -> 0x40E0FF; case "Pink" -> 0xFF6FB5; case "Green" -> 0x55FF88; case "Gold" -> 0xFFD24A;
            case "Rainbow" -> java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 4000) / 4000f, 0.7f, 1f) & 0xFFFFFF;
            default -> 0xFFFFFF;
        };
    }

    public static BlockPos target(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult hit && mc.hitResult.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    @Override public boolean active() { return isEnabled() && target(Minecraft.getInstance()) != null; }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        BlockPos pos = target(mc);
        if (pos == null) return;
        var state = mc.level.getBlockState(pos);
        var box = new net.minecraft.world.phys.AABB(pos);
        if (shape.get()) {
            var s = state.getShape(mc.level, pos);
            if (!s.isEmpty()) box = s.bounds().move(pos);
        }
        box = box.inflate(0.002);
        int c = rgb();
        float w = (float) (double) width.get();
        GizmoStyle st = switch (style.get()) {
            case "Outline" -> GizmoStyle.stroke(WorldOverlays.argb(0xF0, c), w);
            case "Fill" -> GizmoStyle.fill(WorldOverlays.argb(0x48, c));
            default -> GizmoStyle.strokeAndFill(WorldOverlays.argb(0xF0, c), w, WorldOverlays.argb(0x30, c));
        };
        Gizmos.cuboid(box, st);
    }
}
