package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.HandRenderModule;
import unblonded.anthrax.render.Glow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemOutlineMixin {
    private static final Map<List<SpriteContents>, boolean[][]> MASKS = new HashMap<>();
    @Shadow @Final private List<BakedQuad> quads;
    @Shadow @Final private ItemStackRenderState this$0;

    @Inject(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
            shift = At.Shift.AFTER))
    private void anthrax$outlineModel(PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
                                     int color, CallbackInfo ci) {
        HandRenderModule module = HandRenderModule.INSTANCE;
        ItemDisplayContext context = ((ItemStackRenderStateAccessor) (Object) this$0).anthrax$getDisplayContext();
        if (!module.outlineItems() || context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND || quads.isEmpty()) return;

        PoseStack.Pose itemPose = pose.last().copy();
        // First-person poses start from the inverse camera rotation (GameRenderer.renderItemInHand pushes the
        // camera's model-view back on RenderSystem's stack), so they are world-oriented. Facing tests must
        // run in real view space, or front/back and silhouette edges change as the player turns.
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewStack()).mul(itemPose.pose());
        Matrix3f viewNormal = new Matrix3f(RenderSystem.getModelViewStack()).mul(itemPose.normal());
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (BakedQuad quad : quads)
            for (int i = 0; i < 4; i++) {
                Vector3fc vertex = quad.position(i);
                minX = Math.min(minX, vertex.x()); minY = Math.min(minY, vertex.y()); minZ = Math.min(minZ, vertex.z());
                maxX = Math.max(maxX, vertex.x()); maxY = Math.max(maxY, vertex.y()); maxZ = Math.max(maxZ, vertex.z());
            }

        // Generated 2D items are a 1/16-thick slab: a front and back face covering the whole sprite plus a
        // 1-pixel side strip for every opaque/transparent boundary. Outline the sprite's opaque pixels on
        // whichever big face points at the camera, merging every texture layer so overlays add no inner lines.
        float[] extent = {maxX - minX, maxY - minY, maxZ - minZ};
        int thin = extent[0] <= extent[1] && extent[0] <= extent[2] ? 0 : extent[1] <= extent[2] ? 1 : 2;
        float longAxis = Math.max(extent[0], Math.max(extent[1], extent[2]));
        if (extent[thin] < .12f && longAxis > .28f) {
            Direction faceDir = null;
            float best = Float.NEGATIVE_INFINITY;
            for (BakedQuad quad : quads) {
                Direction direction = quad.direction();
                if (direction == null || direction.getAxis().ordinal() != thin) continue;
                float z = facing(view, viewNormal, quad, direction);
                if (z > best) { best = z; faceDir = direction; }
            }
            if (faceDir == null) return;
            List<BakedQuad> faces = new ArrayList<>();
            List<SpriteContents> sprites = new ArrayList<>();
            for (BakedQuad quad : quads) {
                if (quad.direction() != faceDir) continue;
                faces.add(quad);
                sprites.add(quad.materialInfo().sprite().contents());
            }
            List<Edge> spriteSilhouette = spriteContour(faces.get(0), mask(sprites));
            if (spriteSilhouette.isEmpty()) return;
            collector.submitCustomGeometry(pose, RenderTypes.linesTranslucent(), (matrix, out) -> {
                for (Edge edge : spriteSilhouette) line(matrix, out, edge.a, edge.b);
            });
            return;
        }

        Map<Edge, EdgeUse> edges = new HashMap<>();
        for (BakedQuad quad : quads) {
            Direction direction = quad.direction();
            Vector3fc normal = direction == null ? null : direction.step();
            Vector3f transformedNormal = normal == null ? null : viewNormal.transform(normal, new Vector3f()).normalize();
            float facing = direction == null ? 0 : facing(view, viewNormal, quad, direction);
            for (int i = 0; i < 4; i++)
                edges.computeIfAbsent(Edge.of(quad.position(i), quad.position((i + 1) & 3)), ignored -> new EdgeUse())
                        .add(transformedNormal, facing);
        }
        List<Edge> silhouette = new ArrayList<>();
        for (Map.Entry<Edge, EdgeUse> entry : edges.entrySet()) {
            EdgeUse use = entry.getValue();
            if (use.count == 1 || use.front && (use.back || use.tangent || use.crease)) silhouette.add(entry.getKey());
        }
        if (silhouette.isEmpty()) return;

        collector.submitCustomGeometry(pose, RenderTypes.linesTranslucent(), (matrix, out) -> {
            for (Edge edge : silhouette) line(matrix, out, edge.a, edge.b);
        });
    }

    /** How much a face points at the eye (view-space origin): 1 straight at it, 0 edge-on, negative away. */
    private static float facing(Matrix4f view, Matrix3f viewNormal, BakedQuad quad, Direction direction) {
        Vector3f n = viewNormal.transform(direction.step(), new Vector3f()).normalize();
        Vector3fc p = quad.position(0);
        Vector3f eyeToFace = view.transformPosition(p.x(), p.y(), p.z(), new Vector3f()).normalize();
        return -n.dot(eyeToFace);
    }

    /** Union of the opaque pixels of every layer, on the finest layer's grid; cached per sprite set. */
    private static boolean[][] mask(List<SpriteContents> sprites) {
        boolean[][] cached = MASKS.get(sprites);
        if (cached != null) return cached;
        int width = 1, height = 1;
        for (SpriteContents sprite : sprites) { width = Math.max(width, sprite.width()); height = Math.max(height, sprite.height()); }
        boolean[][] opaque = new boolean[height][width];
        for (SpriteContents sprite : sprites) {
            int frame = sprite.getUniqueFrames().getInt(0);
            int sw = sprite.width(), sh = sprite.height();
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    if (!opaque[y][x] && !sprite.isTransparent(frame, x * sw / width, y * sh / height)) opaque[y][x] = true;
        }
        if (MASKS.size() > 512) MASKS.clear();   // sprites are replaced on every resource reload
        MASKS.put(List.copyOf(sprites), opaque);
        return opaque;
    }

    /** Pixel-boundary contour of the mask, mapped onto the face quad through its UV corners, as merged straight runs. */
    private static List<Edge> spriteContour(BakedQuad quad, boolean[][] opaque) {
        List<Edge> out = new ArrayList<>();
        float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        Vector3fc[] positions = new Vector3fc[4];
        float[] us = new float[4], vs = new float[4];
        for (int i = 0; i < 4; i++) {
            positions[i] = quad.position(i);
            long uv = quad.packedUV(i);
            us[i] = Float.intBitsToFloat((int) (uv >>> 32));
            vs[i] = Float.intBitsToFloat((int) uv);
            minU = Math.min(minU, us[i]); maxU = Math.max(maxU, us[i]);
            minV = Math.min(minV, vs[i]); maxV = Math.max(maxV, vs[i]);
        }
        if (maxU <= minU || maxV <= minV) return out;
        Vector3fc[] c = new Vector3fc[4];   // 00, 10, 11, 01 in texture space
        for (int i = 0; i < 4; i++) {
            boolean right = us[i] > (minU + maxU) * .5f;
            boolean bottom = vs[i] > (minV + maxV) * .5f;
            c[right ? bottom ? 2 : 1 : bottom ? 3 : 0] = positions[i];
        }
        if (c[0] == null || c[1] == null || c[2] == null || c[3] == null) return out;

        int height = opaque.length, width = opaque[0].length;
        // horizontal boundaries between row y-1 and row y
        for (int y = 0; y <= height; y++) {
            int run = -1;
            for (int x = 0; x <= width; x++) {
                boolean edge = x < width && (y > 0 && opaque[y - 1][x]) != (y < height && opaque[y][x]);
                if (edge && run < 0) run = x;
                else if (!edge && run >= 0) {
                    out.add(new Edge(mapPixel(c, run, y, width, height), mapPixel(c, x, y, width, height)));
                    run = -1;
                }
            }
        }
        // vertical boundaries between column x-1 and column x
        for (int x = 0; x <= width; x++) {
            int run = -1;
            for (int y = 0; y <= height; y++) {
                boolean edge = y < height && (x > 0 && opaque[y][x - 1]) != (x < width && opaque[y][x]);
                if (edge && run < 0) run = y;
                else if (!edge && run >= 0) {
                    out.add(new Edge(mapPixel(c, x, run, width, height), mapPixel(c, x, y, width, height)));
                    run = -1;
                }
            }
        }
        return out;
    }

    private static Point mapPixel(Vector3fc[] c, int x, int y, int width, int height) {
        Vector3fc p00 = c[0], p10 = c[1], p11 = c[2], p01 = c[3];
        float u = x / (float) width, v = y / (float) height;
        float ax = p00.x() + (p10.x() - p00.x()) * u, ay = p00.y() + (p10.y() - p00.y()) * u, az = p00.z() + (p10.z() - p00.z()) * u;
        float bx = p01.x() + (p11.x() - p01.x()) * u, by = p01.y() + (p11.y() - p01.y()) * u, bz = p01.z() + (p11.z() - p01.z()) * u;
        return new Point(Math.round((ax + (bx - ax) * v) * 10000),
                Math.round((ay + (by - ay) * v) * 10000), Math.round((az + (bz - az) * v) * 10000));
    }

    private static void line(PoseStack.Pose pose, VertexConsumer out, Point a, Point b) {
        Glow.segment(pose, out, a.x / 10000f, a.y / 10000f, a.z / 10000f, b.x / 10000f, b.y / 10000f, b.z / 10000f);
    }

    private record Point(long x, long y, long z) implements Comparable<Point> {
        static Point of(Vector3fc p) {
            return new Point(Math.round(p.x() * 10000), Math.round(p.y() * 10000), Math.round(p.z() * 10000));
        }
        @Override public int compareTo(Point o) {
            int c = Long.compare(x, o.x); if (c != 0) return c;
            c = Long.compare(y, o.y); return c != 0 ? c : Long.compare(z, o.z);
        }
    }

    private record Edge(Point a, Point b) {
        static Edge of(Vector3fc a, Vector3fc b) {
            Point pa = Point.of(a), pb = Point.of(b);
            return pa.compareTo(pb) <= 0 ? new Edge(pa, pb) : new Edge(pb, pa);
        }
    }

    private static final class EdgeUse {
        int count;
        boolean front, back, tangent;
        boolean crease;
        Vector3f firstNormal;

        void add(Vector3f normal, float facing) {
            count++;
            if (facing > .05f) front = true;
            else if (facing < -.05f) back = true;
            else tangent = true;
            if (normal == null) return;
            if (firstNormal == null) firstNormal = new Vector3f(normal);
            else if (firstNormal.dot(normal) < .985f) crease = true;
        }
    }
}
