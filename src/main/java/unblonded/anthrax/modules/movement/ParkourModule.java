package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Jumps automatically at the edge of a block, so you only steer. */
public final class ParkourModule extends FeatureModule {
    public static final ParkourModule INSTANCE = new ParkourModule();
    private final Setting.Num edge = setting(Setting.Num.standalone("Edge distance", 0.05, 0.0, 0.4, 0.01), "How close to the edge before jumping.");
    private final Setting.Bool notSneaking = setting(Setting.Bool.standalone("Not while sneaking", true), "Sneaking turns it off so you can stop at edges.");

    private ParkourModule() { super("Parkour", "Jumps automatically at ledges so you only need to steer", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !p.onGround() || mc.options.keyJump.isDown() || !Moves.moving(mc)) return;
        if (notSneaking.get() && p.isShiftKeyDown()) return;
        Vec3 v = p.getDeltaMovement();
        var ahead = p.getBoundingBox().move(v.x, -0.6, v.z).inflate(-edge.get(), 0, -edge.get());
        if (mc.level.noCollision(p, ahead)) p.jumpFromGround();
    }
}
