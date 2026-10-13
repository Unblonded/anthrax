package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Highlights loaded spawners and reports their coordinates locally. */
public final class SpawnerNotifierModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final SpawnerNotifierModule INSTANCE = new SpawnerNotifierModule();
    private final Setting.Bool notify = setting(Setting.Bool.standalone("Notify new spawners", true), "Shows a notification with the coordinates of each new spawner.");
    private final Setting.Bool boxes = setting(Setting.Bool.standalone("Boxes", true), "Draws a box around each spawner through walls.");
    private final Setting.Bool lines = setting(Setting.Bool.standalone("Tracers", false), "Draws a line to each spawner.");
    private final java.util.Set<BlockPos> known = new java.util.HashSet<>();
    private net.minecraft.world.level.Level lastLevel;
    private int tick;

    private SpawnerNotifierModule() { super("Spawner Notifier", "Highlights loaded spawners and reports their coordinates locally", "Visuals", false); }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) { known.clear(); lastLevel = mc.level; }
        if (++tick % 40 != 0) return;
        int view = mc.options.getEffectiveRenderDistance();
        int cx = mc.player.chunkPosition().x(), cz = mc.player.chunkPosition().z();
        java.util.Set<BlockPos> now = new java.util.HashSet<>();
        for (int x = cx - view; x <= cx + view; x++)
            for (int z = cz - view; z <= cz + view; z++) {
                var chunk = mc.level.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values())
                    if (be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity) now.add(be.getBlockPos());
            }
        for (BlockPos pos : now)
            if (known.add(pos) && notify.get()) Notifications.info("Spawner", pos.getX() + " " + pos.getY() + " " + pos.getZ());
        known.retainAll(now);
    }

    @Override public boolean active() { return isEnabled() && (boxes.get() || lines.get()); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        for (BlockPos pos : known) {
            if (boxes.get()) net.minecraft.gizmos.Gizmos.cuboid(pos, net.minecraft.gizmos.GizmoStyle.strokeAndFill(WorldOverlays.argb(0xE0, 0xFF5577), 1.5f, WorldOverlays.argb(0x30, 0xFF5577))).setAlwaysOnTop();
            if (lines.get()) net.minecraft.gizmos.Gizmos.line(eye.add(look.scale(2)), Vec3.atCenterOf(pos), WorldOverlays.argb(0xA0, 0xFF5577), 1f).setAlwaysOnTop();
        }
    }
}
