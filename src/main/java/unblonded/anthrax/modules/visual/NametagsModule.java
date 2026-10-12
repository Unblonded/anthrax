package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Premium nametags: glass pills with health, ping, distance, totem pops and equipment, readable at any
 * range and through walls. Drawn on screen by the HUD; vanilla tags are hidden for covered entities.
 */
public final class NametagsModule extends FeatureModule {
    public static final NametagsModule INSTANCE = new NametagsModule();

    private final Setting.Bool health = setting(Setting.Bool.standalone("Health", true));
    private final Setting.Bool ping = setting(Setting.Bool.standalone("Ping", true));
    private final Setting.Bool distance = setting(Setting.Bool.standalone("Distance", true));
    private final Setting.Bool pops = setting(Setting.Bool.standalone("Totem pops", true));
    private final Setting.Bool equipment = setting(Setting.Bool.standalone("Armor and held items", true));
    private final Setting.Bool mobs = setting(Setting.Bool.standalone("Mobs too", false));
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 128, 8, 512, 8));

    private NametagsModule() {
        super("Nametags", "Clean nametags with health, ping, pops and armor, visible through walls", "Visuals", false);
    }

    /** Whether this entity gets an Anthrax nametag (and therefore no vanilla one). */
    public boolean replaces(Entity e) {
        if (!isEnabled() || e == Minecraft.getInstance().player) return false;
        if (e instanceof Player p) return !p.isSpectator();
        return mobs.get() && e instanceof LivingEntity && !(e instanceof ArmorStand);
    }

    public boolean health() { return health.get(); }
    public boolean ping() { return ping.get(); }
    public boolean distance() { return distance.get(); }
    public boolean pops() { return pops.get(); }
    public boolean equipment() { return equipment.get(); }
    public double range() { return range.get(); }
}
