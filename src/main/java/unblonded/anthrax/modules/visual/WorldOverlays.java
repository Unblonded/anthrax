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
            unblonded.anthrax.modules.world.AmethystEspModule.INSTANCE,
            unblonded.anthrax.modules.world.NewChunksModule.INSTANCE,
            ItemEspModule.INSTANCE, SpawnerNotifierModule.INSTANCE, AirPlacePreview.INSTANCE,
            unblonded.anthrax.modules.hvh.CoverPlannerModule.INSTANCE, unblonded.anthrax.modules.hvh.TrapEarlyWarningModule.INSTANCE,
            TargetEspModule.INSTANCE, OreEspModule.INSTANCE, StashFinderModule.INSTANCE, BlockHighlightModule.INSTANCE,
            unblonded.anthrax.modules.cosmetic.AuroraModule.INSTANCE, unblonded.anthrax.modules.cosmetic.BlockEffectsModule.INSTANCE, unblonded.anthrax.modules.cosmetic.BreakAnimationModule.INSTANCE, unblonded.anthrax.modules.cosmetic.ContrailsModule.INSTANCE, unblonded.anthrax.modules.cosmetic.CrystalChamsModule.INSTANCE, unblonded.anthrax.modules.cosmetic.DeathEffectsModule.INSTANCE, unblonded.anthrax.modules.cosmetic.ExplosionEffectsModule.INSTANCE, unblonded.anthrax.modules.cosmetic.FootstepsModule.INSTANCE, unblonded.anthrax.modules.cosmetic.JumpCirclesModule.INSTANCE, unblonded.anthrax.modules.cosmetic.PlaceAnimationModule.INSTANCE, unblonded.anthrax.modules.cosmetic.PlacePreviewModule.INSTANCE, unblonded.anthrax.modules.cosmetic.PlayerAuraModule.INSTANCE, unblonded.anthrax.modules.cosmetic.ProjectileTrailsModule.INSTANCE, unblonded.anthrax.modules.cosmetic.SkyboxModule.INSTANCE, unblonded.anthrax.modules.cosmetic.TeleportEffectsModule.INSTANCE, unblonded.anthrax.modules.cosmetic.TrailsModule.INSTANCE
    };

    /** Outline where Air Place will put the held block. */
    private enum AirPlacePreview implements Overlay {
        INSTANCE;
        @Override public boolean active() { return unblonded.anthrax.modules.world.AirPlaceModule.INSTANCE.showPreview(); }
        @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
            var at = unblonded.anthrax.modules.world.AirPlaceModule.INSTANCE.spot(mc);
            if (at != null) Gizmos.cuboid(at, net.minecraft.gizmos.GizmoStyle.stroke(argb(0xC0, 0xFFFFFF), 1.5f));
        }
    }

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
                if (o instanceof unblonded.anthrax.modules.FeatureModule m) unblonded.anthrax.modules.Safe.run(m, () -> o.draw(mc, partial, eye, look));
                else unblonded.anthrax.modules.Safe.run(o.getClass().getSimpleName(), () -> o.draw(mc, partial, eye, look));
            }
        }
        renderer.addMainThreadGizmos(collector.drainGizmos());
    }

    public static int argb(int alpha, int rgb) { return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF); }
}
