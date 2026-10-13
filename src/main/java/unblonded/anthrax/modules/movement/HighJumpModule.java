package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Higher jumps, using the vanilla jump strength attribute. */
public final class HighJumpModule extends FeatureModule {
    public static final HighJumpModule INSTANCE = new HighJumpModule();
    private final Setting.Num boost = setting(Setting.Num.standalone("Multiplier", 1.5, 1.0, 4.0, 0.1), "How much stronger each jump is.");

    private HighJumpModule() { super("High Jump", "Makes your jumps higher", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        Moves.boost(Attributes.JUMP_STRENGTH, "high_jump", boost.get() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override protected void onToggle(boolean enabled) { if (!enabled) Moves.clear(Attributes.JUMP_STRENGTH, "high_jump"); }
}
