package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.Optional;

/**
 * Predicts where a held projectile will land (bow, crossbow, trident, pearl, snowball, egg, potions,
 * experience bottles) using vanilla speeds, gravity and drag, and marks the block or entity it hits.
 */
public final class TrajectoriesModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final TrajectoriesModule INSTANCE = new TrajectoriesModule();

    private final Setting.Bool alwaysShow = setting(Setting.Bool.standalone("Show before drawing the bow", true));
    private final Setting.Num width = setting(Setting.Num.standalone("Line width", 2, .5, 5, .25));

    /** speed, gravity, drag, pitch offset */
    private record Ballistics(float speed, float gravity, float drag, float pitchOffset) {}

    private TrajectoriesModule() {
        super("Trajectories", "Show where arrows, pearls, tridents and potions will land", "Visuals", false);
    }

    @Override public boolean active() { return isEnabled(); }

    private Ballistics ballistics(LocalPlayer p, ItemStack stack) {
        if (stack.getItem() instanceof BowItem) {
            float power = p.isUsingItem() ? BowItem.getPowerForTime(p.getTicksUsingItem()) : (alwaysShow.get() ? 1f : 0f);
            return power <= 0.1f ? null : new Ballistics(power * 3f, 0.05f, 0.99f, 0);
        }
        if (stack.getItem() instanceof CrossbowItem) return CrossbowItem.isCharged(stack) ? new Ballistics(3.15f, 0.05f, 0.99f, 0) : null;
        if (stack.is(Items.TRIDENT)) return (p.isUsingItem() || alwaysShow.get()) ? new Ballistics(2.5f, 0.05f, 0.99f, 0) : null;
        if (stack.is(Items.ENDER_PEARL) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) return new Ballistics(1.5f, 0.03f, 0.99f, 0);
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) return new Ballistics(0.5f, 0.05f, 0.99f, -20);
        if (stack.is(Items.EXPERIENCE_BOTTLE)) return new Ballistics(0.7f, 0.07f, 0.99f, -20);
        return null;
    }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        LocalPlayer p = mc.player;
        ItemStack stack = p.getMainHandItem();
        Ballistics b = ballistics(p, stack);
        if (b == null) { stack = p.getOffhandItem(); b = ballistics(p, stack); }
        if (b == null) return;

        float yaw = p.getViewYRot(partial), pitch = p.getViewXRot(partial) + b.pitchOffset;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3 vel = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)).normalize().scale(b.speed);
        vel = vel.add(p.getDeltaMovement().x, p.onGround() ? 0 : p.getDeltaMovement().y, p.getDeltaMovement().z);
        Vec3 pos = p.getEyePosition(partial).subtract(0, 0.1, 0);

        int color = 0xFF4AF5FF;
        Vec3 prev = pos;
        HitResult hit = null;
        Entity hitEntity = null;
        for (int step = 0; step < 300; step++) {
            Vec3 next = prev.add(vel);
            BlockHitResult blockHit = mc.level.clip(new ClipContext(prev, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (blockHit.getType() != HitResult.Type.MISS) next = blockHit.getLocation();
            // entities along this segment (skip ourselves for the first few steps)
            AABB sweep = new AABB(prev, next).inflate(1.0);
            double best = Double.MAX_VALUE;
            for (Entity e : mc.level.getEntities(p, sweep, e -> e.isPickable() && !e.isSpectator())) {
                Optional<Vec3> clip = e.getBoundingBox().inflate(0.3).clip(prev, next);
                if (clip.isPresent() && prev.distanceToSqr(clip.get()) < best) {
                    best = prev.distanceToSqr(clip.get());
                    hitEntity = e;
                    next = clip.get();
                }
            }
            Gizmos.line(prev, next, WorldOverlays.argb(0xE0, color), (float) width.get());
            if (hitEntity != null) { hit = null; break; }
            if (blockHit.getType() != HitResult.Type.MISS) { hit = blockHit; break; }
            prev = next;
            vel = vel.scale(b.drag).subtract(0, b.gravity, 0);
            if (prev.y < mc.level.getMinY() - 16) break;
        }
        if (hitEntity != null) {
            Gizmos.cuboid(hitEntity.getBoundingBox(), GizmoStyle.strokeAndFill(0xF0FF4D6D, 2f, 0x40FF4D6D)).setAlwaysOnTop();
        } else if (hit instanceof BlockHitResult bh) {
            Gizmos.cuboid(bh.getBlockPos(), GizmoStyle.strokeAndFill(0xF04AF5FF, 2f, 0x304AF5FF)).setAlwaysOnTop();
        }
    }
}
