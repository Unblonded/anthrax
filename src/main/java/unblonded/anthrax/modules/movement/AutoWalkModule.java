package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Holds the forward key for you. */
public final class AutoWalkModule extends FeatureModule {
    public static final AutoWalkModule INSTANCE = new AutoWalkModule();
    private final Setting.Bool sprint = setting(Setting.Bool.standalone("Sprint", false), "Also hold sprint while walking.");

    private AutoWalkModule() { super("Auto Walk", "Walks forward automatically", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        if (mc.player == null) return;
        boolean free = mc.gui.screen() == null;
        mc.options.keyUp.setDown(free);
        if (sprint.get()) mc.options.keySprint.setDown(free);
    }

    @Override protected void onToggle(boolean enabled) {
        if (enabled) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false);
        if (sprint.get()) mc.options.keySprint.setDown(false);
    }
}
