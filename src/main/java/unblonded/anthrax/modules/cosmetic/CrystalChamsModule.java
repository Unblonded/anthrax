package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.ChamsModule;
import unblonded.anthrax.modules.visual.WorldOverlays;

/** Recolours, resizes and speeds up end crystals, optionally through walls, with an optional wireframe box. */
public final class CrystalChamsModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final CrystalChamsModule INSTANCE = new CrystalChamsModule();
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private final Setting.Mode mode = setting(Setting.Mode.standalone("Mode", "Tinted", "Tinted", "Through walls", "Normal"),
            "Tinted colours the crystal, Through walls draws it flat and visible behind blocks, Normal keeps the texture (scale and spin still apply).");
    private final Palette palette = palette("Theme");
    private final Setting.Num opacity = setting(Setting.Num.standalone("Opacity", 0.8, 0.1, 1, 0.05), "");
    private final Setting.Num scale = setting(Setting.Num.standalone("Scale", 1.0, 0.3, 2.0, 0.05), "Size of the crystal model.");
    private final Setting.Num spin = setting(Setting.Num.standalone("Spin speed", 1.0, 0, 5, 0.1), "How fast crystals turn and bob. 0 freezes them.");
    private final Setting.Bool wire = setting(Setting.Bool.standalone("Wireframe box", false), "Outline each crystal with a box.");

    private CrystalChamsModule() { super("Crystal Chams", "Recolour, resize and speed up end crystals, optionally see-through walls"); }

    public boolean recolour() { return isEnabled() && !mode.get().equals("Normal"); }
    public float scale() { return isEnabled() ? (float) (double) scale.get() : 1f; }
    public float spin() { return isEnabled() ? (float) (double) spin.get() : 1f; }

    public RenderType renderType() {
        return mode.get().equals("Through walls") ? ChamsModule.INSTANCE.renderType() : RenderTypes.entityTranslucent(TEXTURE);
    }

    public int tint() { return ((int) Math.round(opacity.get() * 255) << 24) | palette.rgb(); }

    @Override public boolean active() { return isEnabled() && wire.get(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        int rgb = palette.rgb();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof EndCrystal) || e.distanceToSqr(eye) > 64 * 64) continue;
            Vec3 p = e.getPosition(partial);
            double h = 0.9 * scale.get();
            Gizmos.cuboid(new AABB(p.x - h / 1.5, p.y, p.z - h / 1.5, p.x + h / 1.5, p.y + h * 2, p.z + h / 1.5),
                    GizmoStyle.stroke(WorldOverlays.argb(220, rgb), 2f)).setAlwaysOnTop();
        }
    }
}
