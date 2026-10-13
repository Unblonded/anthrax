package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Walk up blocks without jumping, using the vanilla step height attribute. */
public final class StepModule extends FeatureModule {
    public static final StepModule INSTANCE = new StepModule();
    private final Setting.Num height = setting(Setting.Num.standalone("Height", 1.0, 0.6, 3.0, 0.1), "Highest step you walk straight up, in blocks.");
    private final Setting.Bool sneakOff = setting(Setting.Bool.standalone("Not while sneaking", true), "Sneaking turns stepping off, so you can creep along edges.");

    private StepModule() { super("Step", "Walk up blocks without jumping, up to the set height", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        if (mc.player == null) return;
        double extra = sneakOff.get() && mc.player.isShiftKeyDown() ? 0 : height.get() - 0.6;
        Moves.boost(Attributes.STEP_HEIGHT, "step", Math.max(0, extra), AttributeModifier.Operation.ADD_VALUE);
    }

    @Override protected void onToggle(boolean enabled) { if (!enabled) Moves.clear(Attributes.STEP_HEIGHT, "step"); }
}
