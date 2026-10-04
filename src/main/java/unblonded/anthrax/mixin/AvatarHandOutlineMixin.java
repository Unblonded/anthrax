package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import unblonded.anthrax.modules.visual.HandRenderModule;

import java.util.HashMap;
import java.util.Map;

@Mixin(AvatarRenderer.class)
public abstract class AvatarHandOutlineMixin {
    @Redirect(method = "renderHand", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void anthrax$outlineArm(SubmitNodeCollector collector, ModelPart part, PoseStack pose,
                                   RenderType renderType, int light, int overlay, TextureAtlasSprite sprite) {
        collector.submitModelPart(part, pose, renderType, light, overlay, sprite);
        if (!HandRenderModule.INSTANCE.outline()) return;

        pose.pushPose();
        part.translateAndRotate(pose);
        PoseStack.Pose armPose = pose.last().copy();
        Map<Edge, EdgeUse> edges = new HashMap<>();
        for (ModelPart.Cube cube : ((ModelPartAccessor) (Object) part).anthrax$getCubes()) {
            for (ModelPart.Polygon polygon : cube.polygons) {
                Vector3fc normal = polygon.normal();
                float facing = armPose.transformNormal(normal, new org.joml.Vector3f()).z;
                ModelPart.Vertex[] vertices = polygon.vertices();
                for (int i = 0; i < vertices.length; i++) {
                    ModelPart.Vertex a = vertices[i], b = vertices[(i + 1) % vertices.length];
                    Edge edge = edgeOf(a, b);
                    EdgeUse use = edges.computeIfAbsent(edge, ignored -> new EdgeUse());
                    use.count++;
                    if (facing > .05f) use.front = true;
                    else if (facing < -.05f) use.back = true;
                    else use.tangent = true;
                }
            }
        }
        Map<Edge, EdgeUse> silhouette = new HashMap<>();
        for (Map.Entry<Edge, EdgeUse> entry : edges.entrySet()) {
            EdgeUse use = entry.getValue();
            if (use.count == 1 || use.front && (use.back || use.tangent)) silhouette.put(entry.getKey(), use);
        }
        if (silhouette.isEmpty()) { pose.popPose(); return; }
        double time = System.currentTimeMillis() * .001;
        int rgb = spectrum(time * 55);
        int alpha = (int) (240 + 15 * (.5 + .5 * Math.sin(time * 3.1)));
        collector.submitCustomGeometry(pose, RenderTypes.linesTranslucent(), (matrix, out) -> {
            for (Edge edge : silhouette.keySet()) {
                line(matrix, out, edge.a, edge.b, rgb, alpha);
            }
        });
        pose.popPose();
    }

    private static Edge edgeOf(ModelPart.Vertex a, ModelPart.Vertex b) {
        Point pa = new Point(Math.round(a.x() * 10000), Math.round(a.y() * 10000), Math.round(a.z() * 10000));
        Point pb = new Point(Math.round(b.x() * 10000), Math.round(b.y() * 10000), Math.round(b.z() * 10000));
        return pa.compareTo(pb) <= 0 ? new Edge(pa, pb) : new Edge(pb, pa);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer out, Point a, Point b, int rgb, int alpha) {
        int halo = (alpha << 24) | rgb, core = (alpha << 24) | 0xFFFFFF;
        vertex(pose, out, a, halo, 3.5f); vertex(pose, out, b, halo, 3.5f);
        vertex(pose, out, a, core, 1.25f); vertex(pose, out, b, core, 1.25f);
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
        @Override public int compareTo(Point o) {
            int c = Long.compare(x, o.x); if (c != 0) return c;
            c = Long.compare(y, o.y); return c != 0 ? c : Long.compare(z, o.z);
        }
    }
    private record Edge(Point a, Point b) { }
    private static final class EdgeUse { int count; boolean front, back, tangent; }
}
