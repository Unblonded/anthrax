package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3fc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.visual.HandRenderModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemOutlineMixin {
    @Shadow @Final private List<BakedQuad> quads;
    @Shadow @Final private ItemStackRenderState this$0;

    @Inject(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
            shift = At.Shift.AFTER))
    private void anthrax$outlineModel(PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
                                     int color, CallbackInfo ci) {
        HandRenderModule module = HandRenderModule.INSTANCE;
        ItemDisplayContext context = ((ItemStackRenderStateAccessor) (Object) this$0).anthrax$getDisplayContext();
        if (!module.outline() || context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND || quads.isEmpty()) return;

        PoseStack.Pose itemPose = pose.last().copy();
        Map<Edge, EdgeUse> edges = new HashMap<>();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (BakedQuad quad : quads) {
            Direction direction = quad.direction();
            Vector3fc normal = direction == null ? null : direction.step();
            Vector3f transformedNormal = normal == null ? null : itemPose.transformNormal(normal, new Vector3f()).normalize();
            float facing = transformedNormal == null ? 0 : transformedNormal.z;
            for (int i = 0; i < 4; i++) {
                Vector3fc vertex = quad.position(i);
                minX = Math.min(minX, vertex.x()); minY = Math.min(minY, vertex.y()); minZ = Math.min(minZ, vertex.z());
                maxX = Math.max(maxX, vertex.x()); maxY = Math.max(maxY, vertex.y()); maxZ = Math.max(maxZ, vertex.z());
                Edge edge = Edge.of(vertex, quad.position((i + 1) & 3));
                EdgeUse use = edges.computeIfAbsent(edge, ignored -> new EdgeUse());
                use.add(transformedNormal, facing);
            }
        }

        // Generated 2D sprites are very thin slabs; their perimeter reads as a box instead of a silhouette.
        float thinAxis = Math.min(maxX - minX, Math.min(maxY - minY, maxZ - minZ));
        float longAxis = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        if (thinAxis < .12f && longAxis > .28f) {
            List<Edge> spriteSilhouette = new ArrayList<>();
            for (BakedQuad quad : quads) {
                Direction direction = quad.direction();
                if (direction == null || itemPose.transformNormal(direction.step(), new org.joml.Vector3f()).z <= .05f) continue;
                spriteContour(quad, spriteSilhouette);
            }
            if (spriteSilhouette.isEmpty()) return;
            double time = System.currentTimeMillis() * .001;
            int rgb = spectrum(time * 55);
            int alpha = (int) (235 + 20 * (.5 + .5 * Math.sin(time * 3.1)));
            collector.submitCustomGeometry(pose, RenderTypes.linesTranslucent(), (matrix, out) -> {
                for (Edge edge : spriteSilhouette) line(matrix, out, edge.a, edge.b, rgb, alpha);
            });
            return;
        }

        List<Edge> silhouette = new ArrayList<>();
        for (Map.Entry<Edge, EdgeUse> entry : edges.entrySet()) {
            EdgeUse use = entry.getValue();
            if (use.count == 1 || use.front && (use.back || use.tangent || use.crease)) silhouette.add(entry.getKey());
        }
        if (silhouette.isEmpty()) return;

        double time = System.currentTimeMillis() * .001;
        int rgb = spectrum(time * 55);
        int alpha = (int) (235 + 20 * (.5 + .5 * Math.sin(time * 3.1)));
        collector.submitCustomGeometry(pose, RenderTypes.linesTranslucent(), (matrix, out) -> {
            for (Edge edge : silhouette) {
                line(matrix, out, edge.a, edge.b, rgb, alpha);
            }
        });
    }

    /** Trace opaque texture pixels on flat item faces instead of outlining the sprite's rectangular quad. */
    private static void spriteContour(BakedQuad quad, List<Edge> out) {
        TextureAtlasSprite sprite = quad.materialInfo().sprite();
        var contents = sprite.contents();
        int width = contents.width(), height = contents.height();
        int frame = contents.getUniqueFrames().getInt(0);
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
        if (maxU <= minU || maxV <= minV) return;
        Vector3fc p00 = null, p10 = null, p11 = null, p01 = null;
        for (int i = 0; i < 4; i++) {
            boolean right = us[i] > (minU + maxU) * .5f;
            boolean bottom = vs[i] > (minV + maxV) * .5f;
            if (!right && !bottom) p00 = positions[i];
            else if (right && !bottom) p10 = positions[i];
            else if (right) p11 = positions[i];
            else p01 = positions[i];
        }
        if (p00 == null || p10 == null || p11 == null || p01 == null) return;

        boolean[][] opaque = new boolean[height][width];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) opaque[y][x] = !contents.isTransparent(frame, x, y);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!opaque[y][x]) continue;
                if (y == 0 || !opaque[y - 1][x]) out.add(new Edge(mapPixel(p00, p10, p11, p01, x, y, width, height),
                        mapPixel(p00, p10, p11, p01, x + 1, y, width, height)));
                if (x == width - 1 || !opaque[y][x + 1]) out.add(new Edge(mapPixel(p00, p10, p11, p01, x + 1, y, width, height),
                        mapPixel(p00, p10, p11, p01, x + 1, y + 1, width, height)));
                if (y == height - 1 || !opaque[y + 1][x]) out.add(new Edge(mapPixel(p00, p10, p11, p01, x + 1, y + 1, width, height),
                        mapPixel(p00, p10, p11, p01, x, y + 1, width, height)));
                if (x == 0 || !opaque[y][x - 1]) out.add(new Edge(mapPixel(p00, p10, p11, p01, x, y + 1, width, height),
                        mapPixel(p00, p10, p11, p01, x, y, width, height)));
            }
        }
    }

    private static Point mapPixel(Vector3fc p00, Vector3fc p10, Vector3fc p11, Vector3fc p01,
                                  int x, int y, int width, int height) {
        float u = x / (float) width, v = y / (float) height;
        float ax = p00.x() + (p10.x() - p00.x()) * u, ay = p00.y() + (p10.y() - p00.y()) * u, az = p00.z() + (p10.z() - p00.z()) * u;
        float bx = p01.x() + (p11.x() - p01.x()) * u, by = p01.y() + (p11.y() - p01.y()) * u, bz = p01.z() + (p11.z() - p01.z()) * u;
        return new Point(Math.round((ax + (bx - ax) * v) * 10000),
                Math.round((ay + (by - ay) * v) * 10000), Math.round((az + (bz - az) * v) * 10000));
    }

    private static void line(PoseStack.Pose pose, VertexConsumer out, Point a, Point b, int rgb, int alpha) {
        int halo = (alpha << 24) | rgb;
        int core = (alpha << 24) | 0xFFFFFF;
        vertex(pose, out, a, halo, 3.5f);
        vertex(pose, out, b, halo, 3.5f);
        vertex(pose, out, a, core, 1.25f);
        vertex(pose, out, b, core, 1.25f);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Point p, int color, float width) {
        out.addVertex(pose, p.x / 10000f, p.y / 10000f, p.z / 10000f)
                .setColor(color).setNormal(pose, 0, 0, 1).setLineWidth(width);
    }

    private static int spectrum(double time) {
        int r = (int) (127 + 128 * Math.sin(time * .0174533));
        int g = (int) (127 + 128 * Math.sin(time * .0174533 + 2.094));
        int b = (int) (127 + 128 * Math.sin(time * .0174533 + 4.188));
        return (r << 16) | (g << 8) | b;
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
