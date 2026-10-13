package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Longer reach through the vanilla interaction range attributes (servers still check reach on their side). */
public final class ReachModule extends FeatureModule {
    public static final ReachModule INSTANCE = new ReachModule();
    private final Setting.Num entity = setting(Setting.Num.standalone("Entity reach", 4.0, 3.0, 6.0, 0.1), "How far you can hit (vanilla 3).");
    private final Setting.Num block = setting(Setting.Num.standalone("Block reach", 5.0, 4.5, 6.0, 0.1), "How far you can mine and place (vanilla 4.5).");

    private ReachModule() { super("Reach", "Increases how far you can hit entities, using the vanilla interaction range attribute", "Combat", false); }

    @Override public void tick(Minecraft mc) {
        var op = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;
        Moves.boost(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE, "reach_entity", entity.get() - 3.0, op);
        Moves.boost(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE, "reach_block", block.get() - 4.5, op);
    }

    @Override protected void onToggle(boolean enabled) {
        if (enabled) return;
        Moves.clear(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE, "reach_entity");
        Moves.clear(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE, "reach_block");
    }
}
