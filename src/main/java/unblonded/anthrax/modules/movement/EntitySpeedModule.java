package unblonded.anthrax.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Makes the mount you are riding move faster. */
public final class EntitySpeedModule extends FeatureModule {
    public static final EntitySpeedModule INSTANCE = new EntitySpeedModule();
    private final Setting.Num speed = setting(Setting.Num.standalone("Speed", 1.0, 0.2, 4.0, 0.1), "Mount speed while you hold a movement key, in blocks per tick.");

    private EntitySpeedModule() { super("Entity Speed", "Makes the mount you are riding move faster", "Movement", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || p.getVehicle() == null) return;
        var mount = p.getVehicle();
        Vec3 wish = Moves.wishDir(mc, p.getYRot());
        if (wish == Vec3.ZERO) return;
        Vec3 v = mount.getDeltaMovement();
        mount.setDeltaMovement(wish.x * speed.get(), v.y, wish.z * speed.get());
    }
}
