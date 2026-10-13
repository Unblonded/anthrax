package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Pulls you down a short drop when you walk off a ledge, so you land sooner. */
public final class ReverseStepModule extends FeatureModule {
    public static final ReverseStepModule INSTANCE = new ReverseStepModule();
    private final Setting.Num height = setting(Setting.Num.standalone("Max drop", 3, 1, 10, 0.5), "Only pulls you down onto ground within this many blocks.");
    private final Setting.Num speed = setting(Setting.Num.standalone("Pull speed", 1.0, 0.3, 3.0, 0.1), "Downward speed while snapping to the ground.");
    private boolean wasOnGround;

    private ReverseStepModule() { super("Reverse Step", "Pulls you down quickly when you walk off a ledge so you land sooner", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean onGround = p.onGround();
        if (wasOnGround && !onGround && p.getDeltaMovement().y <= 0 && !mc.options.keyJump.isDown()
                && !p.isInWater() && !p.getAbilities().flying && !p.onClimbable()) {
            var box = p.getBoundingBox();
            for (double d = 0.5; d <= height.get(); d += 0.5) {
                if (!mc.level.noCollision(p, box.move(0, -d, 0))) {
                    Vec3 v = p.getDeltaMovement();
                    p.setDeltaMovement(v.x, -speed.get(), v.z);
                    break;
                }
            }
        }
        wasOnGround = onGround;
    }
}
