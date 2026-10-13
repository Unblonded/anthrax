package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Flight. Creative uses the vanilla flying ability; Velocity drives your motion directly. */
public final class FlyModule extends FeatureModule {
    public static final FlyModule INSTANCE = new FlyModule();
    private final Setting.Mode mode = setting(Setting.Mode.standalone("Mode", "Creative", "Creative", "Velocity"),
            "Creative: vanilla creative flight. Velocity: you move exactly with the keys, no drift.");
    private final Setting.Num speed = setting(Setting.Num.standalone("Speed", 1.0, 0.1, 5.0, 0.1), "Flight speed.");
    private final Setting.Bool antiKick = setting(Setting.Bool.standalone("Anti kick", true), "Dips slightly every couple of seconds so servers do not see you hovering.");
    private int ticks;

    private FlyModule() { super("Fly", "Lets you fly. Space goes up, Shift goes down. Servers that check movement may pull you back", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        ticks++;
        double dip = antiKick.get() && ticks % 40 == 0 ? -0.04 : 0;
        if (mode.get().equals("Creative")) {
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
            p.getAbilities().setFlyingSpeed((float) (0.05 * speed.get()));
            if (dip != 0) p.setDeltaMovement(p.getDeltaMovement().add(0, dip, 0));
            return;
        }
        restore(p);
        Vec3 wish = Moves.wishDir(mc, p.getYRot()).scale(speed.get());
        double y = (mc.options.keyJump.isDown() ? speed.get() : 0) - (mc.options.keyShift.isDown() ? speed.get() : 0);
        p.setDeltaMovement(wish.x, y + dip, wish.z);
        p.resetFallDistance();
    }

    private static void restore(LocalPlayer p) {
        if (p.isCreative() || p.isSpectator()) return;
        p.getAbilities().flying = false;
        p.getAbilities().mayfly = false;
        p.getAbilities().setFlyingSpeed(0.05f);
    }

    @Override protected void onToggle(boolean enabled) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!enabled && p != null) {
            restore(p);
            if (p.isCreative() || p.isSpectator()) p.getAbilities().setFlyingSpeed(0.05f);
        }
    }
}
