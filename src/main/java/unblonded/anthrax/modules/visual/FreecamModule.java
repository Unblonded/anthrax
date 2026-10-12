package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Detached spectator-style camera. Your player stays still (movement keys and mouse drive the camera
 * instead); the world renders from the camera's position with occlusion culling off, so nothing pops
 * out when the camera is inside blocks. Movement eases in and glides to a stop; the scroll wheel
 * changes speed. Purely client-side.
 */
public final class FreecamModule extends FeatureModule {
    public static final FreecamModule INSTANCE = new FreecamModule();

    private final Setting.Num speed = setting(Setting.Num.standalone("Speed", 1.0, 0.1, 5, 0.1));
    private final Setting.Bool smooth = setting(Setting.Bool.standalone("Smooth movement", true));
    private final Setting.Bool scrollSpeed = setting(Setting.Bool.standalone("Scroll to change speed", true));
    private final Setting.Bool vertical = setting(Setting.Bool.standalone("Fly where you look", false));
    private Vec3 pos = Vec3.ZERO, prevPos = Vec3.ZERO, velocity = Vec3.ZERO;
    private float yaw, pitch, speedScale = 1f;

    private FreecamModule() {
        super("Freecam", "Fly the camera around freely while your player stays put", "Visuals", false);
    }

    @Override protected void onToggle(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (enabled) {
            if (mc.player == null) { setEnabled(false); return; }
            pos = prevPos = mc.gameRenderer.mainCamera().position();
            velocity = Vec3.ZERO;
            speedScale = 1f;
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
        }
        // the visible-section graph was traced from the old camera; rebuild it from the new one
        if (mc.levelRenderer != null) mc.levelRenderer.sectionOcclusionGraph().invalidate();
    }

    public boolean active() { return isEnabled() && Minecraft.getInstance().player != null; }

    @Override public void tick(Minecraft mc) {
        if (mc.player == null) { setEnabled(false); return; }
        prevPos = pos;
        var o = mc.options;
        boolean input = mc.gui.screen() == null;
        double forward = input ? (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0) : 0;
        double strafe = input ? (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0) : 0;
        double up = input ? (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0) : 0;
        double s = speed.get() * speedScale * (input && o.keySprint.isDown() ? 2.5 : 1);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3 fwd = vertical.get()
                ? new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr))
                : new Vec3(-Math.sin(yr), 0, Math.cos(yr));
        Vec3 left = new Vec3(Math.cos(yr), 0, Math.sin(yr));
        Vec3 move = fwd.scale(forward).add(left.scale(strafe)).add(0, up, 0);
        if (move.lengthSqr() > 1e-6) move = move.normalize();
        Vec3 target = move.scale(s);
        // ease toward the wanted velocity: quick to start, a short glide when you let go
        velocity = smooth.get() ? velocity.lerp(target, move.lengthSqr() > 0 ? 0.45 : 0.3) : target;
        if (velocity.lengthSqr() < 1e-5) velocity = Vec3.ZERO;
        pos = pos.add(velocity);
    }

    public void turn(double dYaw, double dPitch) {
        yaw += (float) dYaw * 0.15f;
        pitch = Mth.clamp(pitch + (float) dPitch * 0.15f, -90f, 90f);
    }

    /** Returns true if the scroll was used to change the freecam speed. */
    public boolean onScroll(double amount) {
        if (!active() || !scrollSpeed.get() || Minecraft.getInstance().gui.screen() != null) return false;
        speedScale = Mth.clamp(speedScale * (amount > 0 ? 1.2f : 1f / 1.2f), 0.1f, 10f);
        return true;
    }

    @Override public String hudInfo() { return String.format("%.1fx", speed.get() * speedScale); }

    public Vec3 position(float partial) { return prevPos.lerp(pos, partial); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
}
