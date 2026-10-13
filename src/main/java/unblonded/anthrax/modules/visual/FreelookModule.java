package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Rotates the camera without turning your body (see CameraMixin / EntityTurnMixin). */
public final class FreelookModule extends FeatureModule {
    public static final FreelookModule INSTANCE = new FreelookModule();
    private final Setting.Mode view = setting(Setting.Mode.standalone("View", "Third person", "Third person", "First person"), "Orbit the camera around you, or just look around from your eyes.");
    private final Setting.Num distance = setting(Setting.Num.standalone("Distance", 4, 2, 12, 0.5), "Camera distance in third person.");
    private float yaw, pitch;

    private FreelookModule() { super("Freelook", "Rotates the camera around you without turning your body. Toggle again to return to your normal view", "Visuals", false); }

    @Override protected void onToggle(boolean enabled) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (enabled && p != null) { yaw = p.getYRot(); pitch = p.getXRot(); }
    }

    public boolean active() { return isEnabled() && Minecraft.getInstance().player != null && !FreecamModule.INSTANCE.active(); }

    public void turn(double dYaw, double dPitch) {
        yaw += (float) dYaw * 0.15f;
        pitch = net.minecraft.util.Mth.clamp(pitch + (float) dPitch * 0.15f, -90f, 90f);
    }

    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean thirdPerson() { return view.get().equals("Third person"); }

    /** Camera position: your eyes, or pulled back along the view, stopping short of walls. */
    public Vec3 position(Vec3 eye) {
        if (!thirdPerson()) return eye;
        Minecraft mc = Minecraft.getInstance();
        double r = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3 back = new Vec3(Math.sin(r) * Math.cos(pr), Math.sin(pr), -Math.cos(r) * Math.cos(pr));
        Vec3 want = eye.add(back.scale(distance.get()));
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, want, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return want;
        return hit.getLocation().add(back.scale(-0.2));
    }
}
