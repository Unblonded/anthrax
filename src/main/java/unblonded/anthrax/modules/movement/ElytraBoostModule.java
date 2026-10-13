package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Speeds up elytra flight without rockets while you hold forward. */
public final class ElytraBoostModule extends FeatureModule {
    public static final ElytraBoostModule INSTANCE = new ElytraBoostModule();
    private final Setting.Num accel = setting(Setting.Num.standalone("Acceleration", 0.05, 0.01, 0.3, 0.01), "Speed added each tick toward where you look.");
    private final Setting.Num max = setting(Setting.Num.standalone("Max speed", 2.5, 0.5, 6.0, 0.1), "Speed cap in blocks per tick.");
    private final Setting.Bool climb = setting(Setting.Bool.standalone("Space climbs", true), "Hold Space to climb.");

    private ElytraBoostModule() { super("Elytra Boost", "Boosts elytra flight without rockets. Steer with your mouse; Space climbs", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !p.isFallFlying()) return;
        Vec3 v = p.getDeltaMovement();
        if (mc.options.keyUp.isDown()) v = v.add(p.getLookAngle().scale(accel.get()));
        if (climb.get() && mc.options.keyJump.isDown()) v = v.add(0, accel.get(), 0);
        if (v.length() > max.get()) v = v.normalize().scale(max.get());
        p.setDeltaMovement(v);
    }
}
