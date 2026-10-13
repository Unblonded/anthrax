package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Lets your boat fly: Space goes up, Shift... dismounts in vanilla, so the down key is configurable. */
public final class BoatFlyModule extends FeatureModule {
    public static final BoatFlyModule INSTANCE = new BoatFlyModule();
    private final Setting.Num speed = setting(Setting.Num.standalone("Speed", 1.0, 0.2, 4.0, 0.1), "Horizontal speed while steering.");
    private final Setting.Num climb = setting(Setting.Num.standalone("Climb speed", 0.4, 0.1, 2.0, 0.1), "Up and down speed.");
    private final Setting.Mode down = setting(Setting.Mode.standalone("Go down with", "Sprint", "Sprint", "Nothing (glide down)"), "Shift leaves the boat, so sprint goes down instead.");

    private BoatFlyModule() { super("Boat Fly", "Lets your boat fly. Steer with the movement keys; Space goes up, Sprint goes down", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !(p.getVehicle() instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat boat)) return;
        Vec3 wish = Moves.wishDir(mc, p.getYRot()).scale(speed.get());
        double y = mc.options.keyJump.isDown() ? climb.get()
                : down.get().equals("Sprint") && mc.options.keySprint.isDown() ? -climb.get() : -0.02;
        boat.setDeltaMovement(wish == Vec3.ZERO ? boat.getDeltaMovement().multiply(0.9, 0, 0.9).add(0, y, 0) : new Vec3(wish.x, y, wish.z));
        boat.setYRot(p.getYRot());
    }
}
