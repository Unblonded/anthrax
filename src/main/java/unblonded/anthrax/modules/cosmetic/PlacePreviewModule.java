package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.visual.WorldOverlays;

/** A ghost box where the block in your hand will go if you right-click now. */
public final class PlacePreviewModule extends CosmeticModule implements WorldOverlays.Overlay {
    public static final PlacePreviewModule INSTANCE = new PlacePreviewModule();
    private final Palette palette = palette("White");
    private final Setting.Num fill = setting(Setting.Num.standalone("Fill opacity", 18, 0, 80, 2), "");
    private final Setting.Bool pulse = setting(Setting.Bool.standalone("Breathe", true), "The ghost gently pulses.");
    private final Setting.Bool offhand = setting(Setting.Bool.standalone("Use offhand block", true), "Preview the offhand block when the main hand has none.");

    private PlacePreviewModule() { super("Place Preview", "Shows a ghost box where the block in your hand will be placed"); }

    @Override public boolean active() { return isEnabled(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        ItemStack held = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(held.getItem() instanceof BlockItem) && offhand.get()) held = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        if (!(held.getItem() instanceof BlockItem)) return;
        BlockPos at = hit.getBlockPos();
        if (!mc.level.getBlockState(at).canBeReplaced()) at = at.relative(hit.getDirection());
        if (!mc.level.getBlockState(at).canBeReplaced() || !mc.level.isInWorldBounds(at)) return;
        double breathe = pulse.get() ? 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0) : 1;
        int rgb = palette.rgb();
        int fa = (int) Math.round(fill.get() / 100 * 255 * (0.6 + 0.4 * breathe));
        Gizmos.cuboid(new AABB(at).deflate(0.002), GizmoStyle.strokeAndFill(WorldOverlays.argb((int) (150 + 80 * breathe), rgb), 1.5f, WorldOverlays.argb(fa, rgb)));
    }
}
