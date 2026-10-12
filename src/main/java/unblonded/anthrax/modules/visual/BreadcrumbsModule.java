package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayDeque;

/** A fading trail of where you have been in this dimension. */
public final class BreadcrumbsModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final BreadcrumbsModule INSTANCE = new BreadcrumbsModule();

    private final Setting.Num length = setting(Setting.Num.standalone("Trail length", 600, 50, 4000, 50));
    private final Setting.Num spacing = setting(Setting.Num.standalone("Point spacing", 0.6, 0.2, 4, 0.1));
    private final Setting.Bool throughWalls = setting(Setting.Bool.standalone("Through walls", false));
    private final ArrayDeque<Vec3> points = new ArrayDeque<>();
    private ClientLevel level;

    private BreadcrumbsModule() {
        super("Breadcrumbs", "Draw a trail of where you have walked", "Visuals", false);
    }

    @Override public boolean active() { return isEnabled() && points.size() > 1; }

    @Override public void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (mc.level != level) { level = mc.level; points.clear(); }
        Vec3 p = mc.player.position().add(0, 0.1, 0);
        if (points.isEmpty() || points.peekLast().distanceToSqr(p) >= spacing.get() * spacing.get()) points.addLast(p);
        while (points.size() > (int) length.get()) points.removeFirst();
    }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        int n = points.size(), i = 0;
        Vec3 prev = null;
        for (Vec3 p : points) {
            if (prev != null) {
                float t = i / (float) n;
                int color = WorldOverlays.argb(Math.round(40 + 200 * t), lerp(0xB76BFF, 0x4AF5FF, t));
                var line = Gizmos.line(prev, p, color, 2f);
                if (throughWalls.get()) line.setAlwaysOnTop();
            }
            prev = p;
            i++;
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    @Override protected void onToggle(boolean enabled) { points.clear(); }
}
