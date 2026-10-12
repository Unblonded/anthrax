package unblonded.anthrax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import unblonded.anthrax.modules.visual.HandRenderModule;
import unblonded.anthrax.render.Glow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neon silhouette around the first-person arm. Edges are computed in view space from the outermost
 * visible layer (the sleeve when shown, otherwise the arm), so the line sits on the visible surface.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarHandOutlineMixin {
    /** View-space quantization so shared cube edges hash together (1/2000 of a block). */
    private static final float QUANT = 2000f;

    @Redirect(method = "renderHand", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void anthrax$outlineArm(SubmitNodeCollector collector, ModelPart part, PoseStack pose,
                                   RenderType renderType, int light, int overlay, TextureAtlasSprite sprite) {
        collector.submitModelPart(part, pose, renderType, light, overlay, sprite);
        if (!HandRenderModule.INSTANCE.outline() || !part.visible) return;

        // Gather (pose, cube) pairs for the outermost layer.
        List<Matrix4f> poses = new ArrayList<>();
        List<ModelPart.Cube> cubes = new ArrayList<>();
        pose.pushPose();
        part.translateAndRotate(pose);
        for (ModelPart child : ((ModelPartAccessor) (Object) part).anthrax$getChildren().values()) {
            if (!child.visible || child.skipDraw) continue;
            List<ModelPart.Cube> childCubes = ((ModelPartAccessor) (Object) child).anthrax$getCubes();
            if (childCubes.isEmpty()) continue;
            pose.pushPose();
            child.translateAndRotate(pose);
            for (ModelPart.Cube cube : childCubes) { poses.add(new Matrix4f(pose.last().pose())); cubes.add(cube); }
            pose.popPose();
        }
        if (cubes.isEmpty()) {
            for (ModelPart.Cube cube : ((ModelPartAccessor) (Object) part).anthrax$getCubes()) {
                poses.add(new Matrix4f(pose.last().pose()));
                cubes.add(cube);
            }
        }
        pose.popPose();
        if (cubes.isEmpty()) return;

        Map<Edge, EdgeUse> edges = new HashMap<>();
        Vector3f a = new Vector3f(), b = new Vector3f(), n = new Vector3f();
        for (int c = 0; c < cubes.size(); c++) {
            Matrix4f m = poses.get(c);
            for (ModelPart.Polygon polygon : cubes.get(c).polygons) {
                ModelPart.Vertex[] v = polygon.vertices();
                if (v.length < 3) continue;
                Vector3f p0 = m.transformPosition(v[0].worldX(), v[0].worldY(), v[0].worldZ(), new Vector3f());
                m.transformDirection(polygon.normal().x(), polygon.normal().y(), polygon.normal().z(), n).normalize();
                // The eye is at the view-space origin; a face is front-facing when its normal points back at it.
                float facing = -n.dot(p0.normalize());
                for (int i = 0; i < v.length; i++) {
                    ModelPart.Vertex va = v[i], vb = v[(i + 1) % v.length];
                    m.transformPosition(va.worldX(), va.worldY(), va.worldZ(), a);
                    m.transformPosition(vb.worldX(), vb.worldY(), vb.worldZ(), b);
                    Edge edge = Edge.of(a, b);
                    EdgeUse use = edges.computeIfAbsent(edge, ignored -> new EdgeUse());
                    use.count++;
                    if (facing > .02f) use.front = true;
                    else if (facing < -.02f) use.back = true;
                    else use.tangent = true;
                }
            }
        }
        List<Edge> silhouette = new ArrayList<>();
        for (Map.Entry<Edge, EdgeUse> entry : edges.entrySet()) {
            EdgeUse use = entry.getValue();
            if (use.front && (use.count == 1 || use.back || use.tangent)) silhouette.add(entry.getKey());
        }
        if (silhouette.isEmpty()) return;
        int rgb = Glow.color();
        // Points are already in view space, so draw with an identity pose.
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.linesTranslucent(), (matrix, out) -> {
            for (Edge e : silhouette) {
                Glow.segment(matrix, out, e.ax / QUANT, e.ay / QUANT, e.az / QUANT,
                        e.bx / QUANT, e.by / QUANT, e.bz / QUANT, rgb);
            }
        });
    }

    private record Edge(long ax, long ay, long az, long bx, long by, long bz) {
        static Edge of(Vector3f a, Vector3f b) {
            long ax = Math.round(a.x * QUANT), ay = Math.round(a.y * QUANT), az = Math.round(a.z * QUANT);
            long bx = Math.round(b.x * QUANT), by = Math.round(b.y * QUANT), bz = Math.round(b.z * QUANT);
            boolean swap = ax > bx || (ax == bx && (ay > by || (ay == by && az > bz)));
            return swap ? new Edge(bx, by, bz, ax, ay, az) : new Edge(ax, ay, az, bx, by, bz);
        }
    }

    private static final class EdgeUse { int count; boolean front, back, tangent; }
}
