package unblonded.anthrax.render;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Projects world positions onto the screen using the exact camera of the frame being drawn
 * (captured from the level renderer), so HUD elements can be pinned to things in the world.
 */
public final class WorldProjection {
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static double camX, camY, camZ;
    private static boolean valid;

    private WorldProjection() {}

    /** Called once per frame from the level-render hook. */
    public static void capture(CameraRenderState camera) {
        if (camera == null || camera.projectionMatrix == null || camera.viewRotationMatrix == null || camera.pos == null) {
            valid = false;
            return;
        }
        VIEW_PROJECTION.set(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        camX = camera.pos.x;
        camY = camera.pos.y;
        camZ = camera.pos.z;
        valid = true;
    }

    /**
     * Screen position of a world point in a {@code width x height} canvas, or null when it is behind
     * the camera. out[0], out[1] = x, y; out[2] = distance from the camera.
     */
    public static float[] project(Vec3 world, int width, int height) {
        if (!valid) return null;
        double dx = world.x - camX, dy = world.y - camY, dz = world.z - camZ;
        TMP.set((float) dx, (float) dy, (float) dz, 1f).mul(VIEW_PROJECTION);
        if (TMP.w <= 0.05f) return null;
        float ndcX = TMP.x / TMP.w, ndcY = TMP.y / TMP.w;
        return new float[]{(ndcX + 1f) * 0.5f * width, (1f - ndcY) * 0.5f * height, (float) Math.sqrt(dx * dx + dy * dy + dz * dz)};
    }
}
