package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Detached spectator-style camera. Your player stays still (movement keys and mouse drive the camera
 * instead); the world renders from the camera's position. Purely client-side.
 */
public final class FreecamModule extends FeatureModule {
    public static final FreecamModule INSTANCE = new FreecamModule();

    private final Setting.Num speed = setting(Setting.Num.standalone("Speed", 1.0, 0.1, 5, 0.1));
    private Vec3 pos = Vec3.ZERO, prevPos = Vec3.ZERO;
    private float yaw, pitch;

    private FreecamModule() {
        super("Freecam", "Fly the camera around freely while your player stays put", "Visuals", false);
    }

    @Override protected void onToggle(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (enabled) {
            if (mc.player == null) { setEnabled(false); return; }
            pos = prevPos = mc.gameRenderer.mainCamera().position();
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
        }
    }

    public boolean active() { return isEnabled() && Minecraft.getInstance().player != null; }

    @Override public void tick(Minecraft mc) {
        if (mc.player == null) { setEnabled(false); return; }
        prevPos = pos;
        if (mc.gui.screen() != null) return;
        var o = mc.options;
        double forward = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double strafe = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        double vertical = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        double s = speed.get() * (o.keySprint.isDown() ? 2.5 : 1);
        double yr = Math.toRadians(yaw);
        Vec3 fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr)), left = new Vec3(Math.cos(yr), 0, Math.sin(yr));
        Vec3 move = fwd.scale(forward).add(left.scale(strafe));
        if (move.lengthSqr() > 1e-6) move = move.normalize();
        pos = pos.add(move.scale(s)).add(0, vertical * s, 0);
    }

    public void turn(double dYaw, double dPitch) {
        yaw += (float) dYaw * 0.15f;
        pitch = Mth.clamp(pitch + (float) dPitch * 0.15f, -90f, 90f);
    }

    public Vec3 position(float partial) { return prevPos.lerp(pos, partial); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
}
