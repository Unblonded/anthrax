package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Finds loaded chunks with a lot of storage in them (stashes). Naturally generated storage can match too. */
public final class StashFinderModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final StashFinderModule INSTANCE = new StashFinderModule();
    private final Setting.Num minimum = setting(Setting.Num.standalone("Minimum storage", 12, 3, 64, 1), "Chests, barrels and shulker boxes a chunk needs to count as a stash.");
    private final Setting.Bool shulkerWeight = setting(Setting.Bool.standalone("Shulkers count triple", true), "Shulker boxes are rarely natural, so each one counts as three.");
    private final Setting.Bool notify = setting(Setting.Bool.standalone("Notify", true), "Shows a notification with the coordinates of each new stash.");
    private final Setting.Bool outline = setting(Setting.Bool.standalone("Outline chunk", true), "Draws a column around stash chunks.");
    private final Map<ChunkPos, Integer> stashes = new HashMap<>();
    private final Set<ChunkPos> announced = new HashSet<>();
    private net.minecraft.world.level.Level lastLevel;
    private int tick;

    private StashFinderModule() { super("Stash Finder", "Finds loaded chunks with concentrated storage. Naturally generated storage can also match", "Visuals", false); }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || ++tick % 40 != 0) return;
        if (mc.level != lastLevel) { stashes.clear(); announced.clear(); lastLevel = mc.level; }
        int view = mc.options.getEffectiveRenderDistance();
        int cx = mc.player.chunkPosition().x(), cz = mc.player.chunkPosition().z();
        for (int x = cx - view; x <= cx + view; x++)
            for (int z = cz - view; z <= cz + view; z++) {
                var chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                int score = 0;
                for (var be : chunk.getBlockEntities().values()) {
                    if (be instanceof ShulkerBoxBlockEntity) score += shulkerWeight.get() ? 3 : 1;
                    else if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) score++;
                }
                ChunkPos cp = chunk.getPos();
                if (score >= minimum.get()) {
                    stashes.put(cp, score);
                    if (announced.add(cp) && notify.get())
                        Notifications.success("Stash Finder", score + " storage at " + (cp.getMiddleBlockX()) + " " + (cp.getMiddleBlockZ()));
                } else stashes.remove(cp);
            }
    }

    @Override public boolean active() { return isEnabled() && outline.get() && !stashes.isEmpty(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        double y0 = mc.player.getY() - 24, y1 = mc.player.getY() + 24;
        for (var e : stashes.entrySet()) {
            ChunkPos cp = e.getKey();
            var box = new AABB(cp.getMinBlockX(), y0, cp.getMinBlockZ(), cp.getMaxBlockX() + 1, y1, cp.getMaxBlockZ() + 1);
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(WorldOverlays.argb(0xC0, 0xFFB347), 2f, WorldOverlays.argb(0x14, 0xFFB347))).setAlwaysOnTop();
        }
    }

    @Override public String hudInfo() { return stashes.isEmpty() ? null : String.valueOf(stashes.size()); }
}
