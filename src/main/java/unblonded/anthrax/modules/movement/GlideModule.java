package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Slows your fall, like Slow Falling. */
public final class GlideModule extends FeatureModule {
    public static final GlideModule INSTANCE = new GlideModule();
    private final Setting.Num fall = setting(Setting.Num.standalone("Fall speed", 0.08, 0.0, 0.5, 0.01), "Fastest you fall while gliding, in blocks per tick.");
    private final Setting.Bool sneakFall = setting(Setting.Bool.standalone("Sneak to drop", true), "Hold Shift to fall normally.");

    private GlideModule() { super("Glide", "Slows your fall, similar to Slow Falling", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || p.onGround() || p.isFallFlying() || p.getAbilities().flying || p.isInWater() || p.onClimbable()) return;
        if (sneakFall.get() && p.isShiftKeyDown()) return;
        Vec3 v = p.getDeltaMovement();
        if (v.y < -fall.get()) { p.setDeltaMovement(v.x, -fall.get(), v.z); p.resetFallDistance(); }
    }
}
