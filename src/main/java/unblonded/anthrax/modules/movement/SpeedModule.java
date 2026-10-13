package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Faster ground movement. Attribute mode scales your walk speed; Strafe mode also keeps speed in the air. */
public final class SpeedModule extends FeatureModule {
    public static final SpeedModule INSTANCE = new SpeedModule();
    private final Setting.Mode mode = setting(Setting.Mode.standalone("Mode", "Attribute", "Attribute", "Strafe"),
            "Attribute: scales your movement speed (safer, grounded only). Strafe: also steers at full speed in the air.");
    private final Setting.Num speed = setting(Setting.Num.standalone("Multiplier", 1.4, 1.0, 3.0, 0.05), "How much faster you move.");
    private final Setting.Bool autoJump = setting(Setting.Bool.standalone("Auto jump (Strafe)", false), "Hop automatically while moving in Strafe mode.");

    private SpeedModule() { super("Speed", "Increases your ground speed", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        Moves.boost(Attributes.MOVEMENT_SPEED, "speed", speed.get() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        // Target Strafe steers while it has a target; Speed only supplies the pace then
        if (unblonded.anthrax.modules.hvh.TargetStrafeModule.INSTANCE.steering()) return;
        if (!mode.get().equals("Strafe") || p.isInWater() || p.isFallFlying() || p.getAbilities().flying) return;
        Vec3 wish = Moves.wishDir(mc, p.getYRot());
        if (wish == Vec3.ZERO) return;
        if (autoJump.get() && p.onGround()) p.jumpFromGround();
        Vec3 v = p.getDeltaMovement();
        double s = Math.max(Math.hypot(v.x, v.z), 0.2873 * speed.get() * (p.onGround() ? 1 : 0.92));
        p.setDeltaMovement(wish.x * s, v.y, wish.z * s);
    }

    public double multiplier() { return speed.get(); }

    @Override protected void onToggle(boolean enabled) { if (!enabled) Moves.clear(Attributes.MOVEMENT_SPEED, "speed"); }
}
