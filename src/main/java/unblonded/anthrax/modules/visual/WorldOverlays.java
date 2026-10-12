package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.phys.Vec3;

/**
 * One shared gizmo pass per frame for the lightweight world overlays (storage ESP, tracers, holes,
 * logout spots, breadcrumbs, trajectories), so each frame builds a single collector instead of one per module.
 */
public final class WorldOverlays {
    private WorldOverlays() {}

    public interface Overlay {
        boolean active();
        void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look);
    }

    private static final Overlay[] OVERLAYS = {
            StorageEspModule.INSTANCE, TracersModule.INSTANCE, HoleEspModule.INSTANCE,
            LogoutSpotsModule.INSTANCE, BreadcrumbsModule.INSTANCE, TrajectoriesModule.INSTANCE,
            unblonded.anthrax.modules.world.AmethystEspModule.INSTANCE
    };

    public static void render(LevelRenderer renderer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean any = false;
        for (Overlay o : OVERLAYS) if (o.active()) { any = true; break; }
        if (!any) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        Vec3 look = mc.player.getViewVector(partial);
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            for (Overlay o : OVERLAYS) {
                if (!o.active()) continue;
                try { o.draw(mc, partial, eye, look); }
                catch (RuntimeException e) { System.err.println("Anthrax overlay failed: " + e); }
            }
        }
        renderer.addMainThreadGizmos(collector.drainGizmos());
    }

    public static int argb(int alpha, int rgb) { return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF); }
}
