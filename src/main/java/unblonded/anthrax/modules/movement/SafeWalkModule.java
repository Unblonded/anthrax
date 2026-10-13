package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Stops you at block edges as if you were sneaking (see PlayerEdgeMixin). */
public final class SafeWalkModule extends FeatureModule {
    public static final SafeWalkModule INSTANCE = new SafeWalkModule();
    private final Setting.Bool notWhileJumping = setting(Setting.Bool.standalone("Off while jumping", true), "Holding Space lets you jump off edges.");

    private SafeWalkModule() { super("Safe Walk", "Stops you at block edges so you do not walk off", "Movement", false); }

    public boolean holdEdges() {
        return isEnabled() && !(notWhileJumping.get() && Minecraft.getInstance().options.keyJump.isDown());
    }
}
