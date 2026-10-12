package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Outlines storage blocks in loaded chunks, colored by type. Block entities are collected once a second. */
public final class StorageEspModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final StorageEspModule INSTANCE = new StorageEspModule();

    private record Box(BlockPos pos, int color) {}

    private final Setting.Num range = setting(Setting.Num.standalone("Range", 96, 16, 256, 8));
    private final Setting.Bool chests = setting(Setting.Bool.standalone("Chests", true));
    private final Setting.Bool enderChests = setting(Setting.Bool.standalone("Ender chests", true));
    private final Setting.Bool shulkers = setting(Setting.Bool.standalone("Shulker boxes", true));
    private final Setting.Bool barrels = setting(Setting.Bool.standalone("Barrels", true));
    private final Setting.Bool hoppers = setting(Setting.Bool.standalone("Hoppers, droppers, dispensers", false));
    private final Setting.Bool furnaces = setting(Setting.Bool.standalone("Furnaces", false));
    private final Setting.Bool fill = setting(Setting.Bool.standalone("Filled boxes", true), "Fill the highlight boxes instead of drawing outlines only.");
    private final Setting.Bool tracers = setting(Setting.Bool.standalone("Tracers", false));
    private volatile List<Box> boxes = List.of();
    private long lastScan;

    private StorageEspModule() {
        super("Storage ESP", "Highlight chests, shulkers, barrels and other storage through walls", "Visuals", false);
    }

    @Override public boolean active() { return isEnabled(); }

    @Override public void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (mc.level == null || mc.player == null || now - lastScan < 1000) return;
        lastScan = now;
        int r = (int) Math.ceil(range.get() / 16.0);
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        double rangeSq = range.get() * range.get();
        List<Box> out = new ArrayList<>();
        for (int x = pcx - r; x <= pcx + r; x++)
            for (int z = pcz - r; z <= pcz + r; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    int c = color(be);
                    if (c == 0 || mc.player.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > rangeSq) continue;
                    out.add(new Box(be.getBlockPos(), c));
                }
            }
        Vec3 p = mc.player.position();
        out.sort(Comparator.comparingDouble(b -> b.pos.distToCenterSqr(p)));
        boxes = out.size() > 600 ? List.copyOf(out.subList(0, 600)) : List.copyOf(out);
    }

    private int color(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return chests.get() ? 0xFFFF6B4A : 0;
        if (be instanceof ChestBlockEntity) return chests.get() ? 0xFFFFB347 : 0;
        if (be instanceof EnderChestBlockEntity) return enderChests.get() ? 0xFFB76BFF : 0;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? 0xFFFF7AD9 : 0;
        if (be instanceof BarrelBlockEntity) return barrels.get() ? 0xFFD49A5A : 0;
        if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity) return hoppers.get() ? 0xFFA0A8B8 : 0;
        if (be instanceof AbstractFurnaceBlockEntity) return furnaces.get() ? 0xFF8FA0B0 : 0;
        return 0;
    }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        for (Box b : boxes) {
            GizmoStyle style = fill.get()
                    ? GizmoStyle.strokeAndFill(WorldOverlays.argb(0xE0, b.color), 1.5f, WorldOverlays.argb(0x30, b.color))
                    : GizmoStyle.stroke(WorldOverlays.argb(0xE0, b.color), 1.5f);
            Gizmos.cuboid(b.pos, style).setAlwaysOnTop();
            if (tracers.get()) Gizmos.line(eye.add(look.scale(2)), Vec3.atCenterOf(b.pos), WorldOverlays.argb(0xA0, b.color), 1f).setAlwaysOnTop();
        }
    }

    @Override protected void onToggle(boolean enabled) { boxes = List.of(); lastScan = 0; }
    @Override public String hudInfo() { return Integer.toString(boxes.size()); }
}
