package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Climbs ladders and vines faster than vanilla. */
public final class FastLadderModule extends FeatureModule {
    public static final FastLadderModule INSTANCE = new FastLadderModule();
    private final Setting.Num speed = setting(Setting.Num.standalone("Climb speed", 0.3, 0.12, 0.8, 0.02), "Upward speed on ladders and vines (vanilla is about 0.12). Holding Space climbs at double.");

    private FastLadderModule() { super("Fast Ladder", "Climbs ladders and vines much faster. Hold Space to climb faster still", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !p.onClimbable() || p.isShiftKeyDown()) return;
        boolean up = p.horizontalCollision || mc.options.keyJump.isDown();
        if (!up) return;
        double s = speed.get() * (mc.options.keyJump.isDown() ? 2 : 1);
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, Math.max(v.y, s), v.z);
    }
}
