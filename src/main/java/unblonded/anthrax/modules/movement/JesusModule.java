package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Lets you walk on water (and lava, optionally) by floating you at the surface. */
public final class JesusModule extends FeatureModule {
    public static final JesusModule INSTANCE = new JesusModule();
    private final Setting.Bool lava = setting(Setting.Bool.standalone("Lava too", false), "Also float on lava.");

    private JesusModule() { super("Jesus", "Lets you walk on water. Hold Shift to sink", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || p.isShiftKeyDown() || p.getAbilities().flying) return;
        boolean inFluid = p.isInWater() || (lava.get() && p.isInLava());
        if (!inFluid) return;
        Vec3 v = p.getDeltaMovement();
        // rise to the surface, then stay there
        double fluidTop = p.getFluidHeight(net.minecraft.tags.FluidTags.WATER);
        if (lava.get()) fluidTop = Math.max(fluidTop, p.getFluidHeight(net.minecraft.tags.FluidTags.LAVA));
        p.setDeltaMovement(v.x, fluidTop > 0.4 ? 0.11 : Math.max(v.y, 0.0), v.z);
        p.resetFallDistance();
    }
}
