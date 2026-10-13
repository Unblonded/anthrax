package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Climb walls by walking into them. */
public final class SpiderModule extends FeatureModule {
    public static final SpiderModule INSTANCE = new SpiderModule();
    private final Setting.Num speed = setting(Setting.Num.standalone("Climb speed", 0.2, 0.05, 0.6, 0.05), "Upward speed while pressed against a wall.");

    private SpiderModule() { super("Spider", "Climb walls by walking into them", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !p.horizontalCollision || !Moves.moving(mc)) return;
        Vec3 v = p.getDeltaMovement();
        if (v.y < speed.get()) p.setDeltaMovement(v.x, speed.get(), v.z);
        p.resetFallDistance();
    }
}
