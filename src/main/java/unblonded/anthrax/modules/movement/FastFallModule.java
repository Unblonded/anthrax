package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Drops you quickly after stepping off a ledge. */
public final class FastFallModule extends FeatureModule {
    public static final FastFallModule INSTANCE = new FastFallModule();
    private final Setting.Num speed = setting(Setting.Num.standalone("Fall speed", 1.5, 0.5, 4.0, 0.1), "Downward speed once you start falling, in blocks per tick.");

    private FastFallModule() { super("Fast Fall", "Drops you quickly after stepping off a ledge", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || p.onGround() || p.isFallFlying() || p.getAbilities().flying || p.isInWater() || p.isInLava() || p.onClimbable()) return;
        Vec3 v = p.getDeltaMovement();
        if (v.y < -0.1 && !mc.options.keyJump.isDown()) p.setDeltaMovement(v.x, -speed.get(), v.z);
    }
}
