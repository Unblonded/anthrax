package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Crystal-PvP safe holes: a 1x1 air pocket with bedrock/obsidian on all four sides and below.
 * Green = all bedrock (unbreakable), yellow = mixed, red = all obsidian.
 */
public final class HoleEspModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final HoleEspModule INSTANCE = new HoleEspModule();
    private record Hole(BlockPos pos, int color) {}

    private final Setting.Num radius = setting(Setting.Num.standalone("Radius", 8, 3, 16, 1));
    private final Setting.Num height = setting(Setting.Num.standalone("Box height", 0.15, 0.02, 1, 0.01));
    private final Setting.Bool outline = setting(Setting.Bool.standalone("Outline", true));
    private volatile List<Hole> holes = List.of();
    private int tickCounter;

    private HoleEspModule() {
        super("Hole ESP", "Show bedrock and obsidian safe holes for crystal PvP", "Visuals", false);
    }

    @Override public boolean active() { return isEnabled(); }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || ++tickCounter % 4 != 0) return;
        int r = (int) radius.get();
        BlockPos origin = mc.player.blockPosition();
        List<Hole> out = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -4; dy <= 3; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    int c = classify(mc, pos);
                    if (c != 0) out.add(new Hole(pos.immutable(), c));
                }
        holes = List.copyOf(out);
    }

    private static int classify(Minecraft mc, BlockPos pos) {
        var level = mc.level;
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                || !level.getBlockState(pos.above(2)).isAir()) return 0;
        int bedrock = 0;
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.BEDROCK)) bedrock++;
        else if (!below.is(Blocks.OBSIDIAN)) return 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = level.getBlockState(pos.relative(d));
            if (s.is(Blocks.BEDROCK)) bedrock++;
            else if (!s.is(Blocks.OBSIDIAN) && !s.is(Blocks.CRYING_OBSIDIAN) && !s.is(Blocks.RESPAWN_ANCHOR)) return 0;
        }
        return bedrock == 5 ? 0xFF5CFF9D : bedrock == 0 ? 0xFFFF4D6D : 0xFFFFC94A;
    }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        double h = height.get();
        for (Hole hole : holes) {
            AABB box = new AABB(hole.pos.getX(), hole.pos.getY(), hole.pos.getZ(),
                    hole.pos.getX() + 1, hole.pos.getY() + h, hole.pos.getZ() + 1);
            GizmoStyle style = outline.get()
                    ? GizmoStyle.strokeAndFill(WorldOverlays.argb(0xE0, hole.color), 1.5f, WorldOverlays.argb(0x50, hole.color))
                    : GizmoStyle.fill(WorldOverlays.argb(0x60, hole.color));
            Gizmos.cuboid(box, style);
        }
    }

    @Override public String hudInfo() { return Integer.toString(holes.size()); }
}
