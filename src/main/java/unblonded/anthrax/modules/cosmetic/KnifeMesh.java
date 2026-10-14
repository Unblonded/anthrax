package unblonded.anthrax.modules.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * A tiny box-mesh builder for the Weapons module: boxes placed with a matrix stack, each corner coloured by a
 * shade function, collected into one vertex array and replayed into the buffer when the frame is drawn.
 */
final class KnifeMesh {
    /** Colour (RGB) at a corner of a box, from that corner's local coordinates. */
    interface Shade { int at(float x, float y, float z); }

    private final ArrayDeque<Matrix4f> stack = new ArrayDeque<>();
    private Matrix4f m = new Matrix4f();
    /** Per vertex: x y z nx ny nz rgb. */
    private float[] data = new float[7 * 4 * 6 * 64];
    private int size;

    KnifeMesh push() { stack.push(new Matrix4f(m)); return this; }
    KnifeMesh pop() { m = stack.pop(); return this; }
    KnifeMesh translate(float x, float y, float z) { m.translate(x, y, z); return this; }
    KnifeMesh rotX(float deg) { m.rotateX((float) Math.toRadians(deg)); return this; }
    KnifeMesh rotY(float deg) { m.rotateY((float) Math.toRadians(deg)); return this; }
    KnifeMesh rotZ(float deg) { m.rotateZ((float) Math.toRadians(deg)); return this; }
    KnifeMesh scale(float s) { m.scale(s); return this; }
    KnifeMesh scaleX(float s) { m.scale(s, 1, 1); return this; }

    void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) { box(x0, y0, z0, x1, y1, z1, (x, y, z) -> rgb); }

    void box(float x0, float y0, float z0, float x1, float y1, float z1, Shade shade) {
        // six faces, each wound counter-clockwise seen from outside
        face(shade, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        face(shade, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(shade, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(shade, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        face(shade, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(shade, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    /** A wedge: a box whose top face is squeezed to {@code topScale} of its width (for points and bevels). */
    void taper(float x0, float y0, float z0, float x1, float y1, float z1, float topX0, float topX1, Shade shade) {
        face(shade, 0, 0, -1, x1, y0, z0, x0, y0, z0, topX0, y1, z0, topX1, y1, z0);
        face(shade, 0, 0, 1, x0, y0, z1, x1, y0, z1, topX1, y1, z1, topX0, y1, z1);
        face(shade, -1, 0, 0, x0, y0, z0, x0, y0, z1, topX0, y1, z1, topX0, y1, z0);
        face(shade, 1, 0, 0, x1, y0, z1, x1, y0, z0, topX1, y1, z0, topX1, y1, z1);
        face(shade, 0, 1, 0, topX0, y1, z1, topX1, y1, z1, topX1, y1, z0, topX0, y1, z0);
        face(shade, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private final Vector3f tmp = new Vector3f();

    private void face(Shade shade, float nx, float ny, float nz, float... p) {
        if (size + 28 > data.length) data = Arrays.copyOf(data, data.length * 2);
        Vector3f n = m.transformDirection(new Vector3f(nx, ny, nz)).normalize();
        for (int i = 0; i < 4; i++) {
            float lx = p[i * 3], ly = p[i * 3 + 1], lz = p[i * 3 + 2];
            m.transformPosition(lx, ly, lz, tmp);
            data[size++] = tmp.x; data[size++] = tmp.y; data[size++] = tmp.z;
            data[size++] = n.x; data[size++] = n.y; data[size++] = n.z;
            data[size++] = Float.intBitsToFloat(shade.at(lx, ly, lz));
        }
    }

    /** Colour at a point of a strip: {@code u} along it (0..1), {@code v} across it (0 on side A, 1 on side B). */
    interface StripShade { int at(float u, float v); }

    /**
     * A flat shape between two outlines, extruded through Z: side A at half-thickness {@code ta}, side B at
     * {@code tb} (a thin B makes a bevelled edge). Front, back, both rims and (unless {@code closed}) the end caps.
     */
    void strip(float[] ax, float[] ay, float ta, float[] bx, float[] by, float tb, boolean closed, StripShade shade) {
        int n = ax.length, segs = closed ? n : n - 1;
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % n;
            float u0 = i / (float) (n - 1), u1 = closed && j == 0 ? 1 : j / (float) (n - 1);
            int ca0 = shade.at(u0, 0), ca1 = shade.at(u1, 0), cb0 = shade.at(u0, 1), cb1 = shade.at(u1, 1);
            // front (+Z) and back (-Z)
            quad(ax[i], ay[i], ta, bx[i], by[i], tb, bx[j], by[j], tb, ax[j], ay[j], ta, ca0, cb0, cb1, ca1);
            quad(ax[j], ay[j], -ta, bx[j], by[j], -tb, bx[i], by[i], -tb, ax[i], ay[i], -ta, ca1, cb1, cb0, ca0);
            // rims along A and B
            quad(ax[j], ay[j], ta, ax[j], ay[j], -ta, ax[i], ay[i], -ta, ax[i], ay[i], ta, ca1, ca1, ca0, ca0);
            quad(bx[i], by[i], tb, bx[i], by[i], -tb, bx[j], by[j], -tb, bx[j], by[j], tb, cb0, cb0, cb1, cb1);
        }
        if (!closed) {
            int l = n - 1;
            quad(ax[0], ay[0], -ta, bx[0], by[0], -tb, bx[0], by[0], tb, ax[0], ay[0], ta, shade.at(0, 0), shade.at(0, 1), shade.at(0, 1), shade.at(0, 0));
            quad(ax[l], ay[l], ta, bx[l], by[l], tb, bx[l], by[l], -tb, ax[l], ay[l], -ta, shade.at(1, 0), shade.at(1, 1), shade.at(1, 1), shade.at(1, 0));
        }
    }

    /** One quad with its own flat normal (from the corners) and a colour per corner. Both sides are drawn. */
    private void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                      int c0, int c1, int c2, int c3) {
        Vector3f p0 = m.transformPosition(x0, y0, z0, new Vector3f()), p1 = m.transformPosition(x1, y1, z1, new Vector3f());
        Vector3f p2 = m.transformPosition(x2, y2, z2, new Vector3f()), p3 = m.transformPosition(x3, y3, z3, new Vector3f());
        Vector3f n = new Vector3f(p2).sub(p0).cross(new Vector3f(p3).sub(p1));
        if (n.lengthSquared() < 1e-14f) n.set(0, 0, 1); else n.normalize();
        put(p0, n, c0); put(p1, n, c1); put(p2, n, c2); put(p3, n, c3);
    }

    private void put(Vector3f p, Vector3f n, int rgb) {
        if (size + 7 > data.length) data = Arrays.copyOf(data, data.length * 2);
        data[size++] = p.x; data[size++] = p.y; data[size++] = p.z;
        data[size++] = n.x; data[size++] = n.y; data[size++] = n.z;
        data[size++] = Float.intBitsToFloat(rgb);
    }

    /** A frozen copy of the vertices, safe to draw later in the frame. */
    float[] bake() { return Arrays.copyOf(data, size); }

    static void emit(float[] v, PoseStack.Pose pose, VertexConsumer buf, int light) {
        for (int i = 0; i < v.length; i += 7) {
            int rgb = Float.floatToRawIntBits(v[i + 6]);
            buf.addVertex(pose, v[i], v[i + 1], v[i + 2])
                    .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)
                    .setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, v[i + 3], v[i + 4], v[i + 5]);
        }
    }
}
