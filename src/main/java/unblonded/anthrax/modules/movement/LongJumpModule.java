package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Launches you further when you jump. */
public final class LongJumpModule extends FeatureModule {
    public static final LongJumpModule INSTANCE = new LongJumpModule();
    private final Setting.Num boost = setting(Setting.Num.standalone("Boost", 2.0, 1.0, 5.0, 0.1), "Horizontal speed multiplier at take-off.");
    private final Setting.Bool once = setting(Setting.Bool.standalone("Turn off after a jump", false), "Switches itself off after one boosted jump.");
    private boolean wasOnGround;

    private LongJumpModule() { super("Long Jump", "Launches you further when you jump. Best for short use on servers without an anticheat", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean ground = p.onGround();
        Vec3 v = p.getDeltaMovement();
        if (wasOnGround && !ground && v.y > 0.2 && Moves.moving(mc)) {
            Vec3 wish = Moves.wishDir(mc, p.getYRot());
            double s = Math.max(Math.hypot(v.x, v.z), 0.28) * boost.get();
            p.setDeltaMovement(wish.x * s, v.y, wish.z * s);
            if (once.get()) mc.execute(() -> setEnabled(false));
        }
        wasOnGround = ground;
    }
}
