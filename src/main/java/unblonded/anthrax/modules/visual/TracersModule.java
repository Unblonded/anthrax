package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.client.FriendsModule;

/** Lines from the crosshair to players (friends in green), and optionally hostile mobs and items. */
public final class TracersModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final TracersModule INSTANCE = new TracersModule();

    private final Setting.Bool players = setting(Setting.Bool.standalone("Players", true));
    private final Setting.Bool friends = setting(Setting.Bool.standalone("Friends", true));
    private final Setting.Bool hostiles = setting(Setting.Bool.standalone("Hostile mobs", false));
    private final Setting.Bool items = setting(Setting.Bool.standalone("Items", false));
    private final Setting.Mode coloring = setting(Setting.Mode.standalone("Player color", "Distance", "Distance", "Theme", "White"));
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 192, 16, 512, 16));
    private final Setting.Num width = setting(Setting.Num.standalone("Line width", 1.5, .5, 4, .25));

    private TracersModule() {
        super("Tracers", "Lines to players, mobs and items; friends drawn in green", "Visuals", false);
    }

    @Override public boolean active() { return isEnabled(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        Vec3 from = eye.add(look.scale(2));
        double rangeSq = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || e.isRemoved()) continue;
            int color;
            if (e instanceof Player p) {
                if (p.isSpectator()) continue;
                boolean friend = FriendsModule.isFriend(p);
                if (friend ? !friends.get() : !players.get()) continue;
                color = friend ? FriendsModule.COLOR : playerColor(Math.sqrt(mc.player.distanceToSqr(e)));
            } else if (e instanceof Enemy) {
                if (!hostiles.get()) continue;
                color = 0xFFFF4D4D;
            } else if (e instanceof ItemEntity) {
                if (!items.get()) continue;
                color = 0xFFFFE08A;
            } else continue;
            if (mc.player.distanceToSqr(e) > rangeSq) continue;
            Vec3 to = e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0);
            Gizmos.line(from, to, WorldOverlays.argb(0xD0, color), (float) width.get()).setAlwaysOnTop();
        }
    }

    private int playerColor(double dist) {
        return switch (coloring.get()) {
            case "White" -> 0xFFFFFFFF;
            case "Theme" -> 0xFF4AF5FF;
            default -> {
                // red when close, green when far
                float t = (float) Math.min(1, dist / 60.0);
                int r = Math.round(255 * (1 - t)), g = Math.round(80 + 175 * t);
                yield 0xFF000000 | (r << 16) | (g << 8) | 0x50;
            }
        };
    }
}
