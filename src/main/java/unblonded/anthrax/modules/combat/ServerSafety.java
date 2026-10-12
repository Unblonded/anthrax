package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps automated actions inside what a vanilla/Paper server accepts, so they work on servers and not
 * just in singleplayer:
 *  - reach: the server drops block clicks past block-interaction range (+1) and entity hits past
 *    entity-interaction range (+3); we stay inside the plain range, which also leaves room for lag.
 *  - hit points: a click's hit location must sit on the clicked block, so we aim at the clicked face.
 *  - rubber-banding: after the server teleports us back it ignores block clicks until we confirm the
 *    teleport, so automation pauses briefly instead of firing actions that would all be thrown away.
 */
public final class ServerSafety {
    private ServerSafety() {}

    private static final long PAUSE_AFTER_TELEPORT_MS = 300;
    private static volatile long lastServerTeleport;

    /** Called when the server moves us (ClientboundPlayerPositionPacket): a rubber-band or a real teleport. */
    public static void onServerTeleport() { lastServerTeleport = System.currentTimeMillis(); }

    /** True for a short moment after a server teleport, while automated clicks would be rejected. */
    public static boolean paused() { return System.currentTimeMillis() - lastServerTeleport < PAUSE_AFTER_TELEPORT_MS; }

    /** True when a click on this block reaches on a vanilla server (eye to the block's box, plain range). */
    public static boolean canReach(LocalPlayer self, BlockPos pos) {
        return self.isWithinBlockInteractionRange(pos, 0.0);
    }

    /** True when an attack/interaction on this entity reaches on a vanilla server. */
    public static boolean canReach(LocalPlayer self, Entity e) {
        return self.isWithinEntityInteractionRange(e, 0.0);
    }

    /** A click on {@code pos}'s {@code face}, with the hit point on that face (what a real click sends). */
    public static BlockHitResult click(BlockPos pos, Direction face) {
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5);
        return new BlockHitResult(hit, face, pos, false);
    }

    /** Whether automation may act right now (player present and not waiting on a server teleport). */
    public static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gameMode != null && !paused();
    }
}
