package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Marks where players logged out. A player who vanishes from the world AND from the tab list left the
 * server (as opposed to walking out of render distance); their last box is kept until they rejoin.
 */
public final class LogoutSpotsModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final LogoutSpotsModule INSTANCE = new LogoutSpotsModule();

    private record Seen(String name, AABB box, float health) {}
    private record Spot(UUID id, String name, AABB box, float health, long time) {}

    private final Setting.Bool notify = setting(Setting.Bool.standalone("Notify", true));
    private final Map<UUID, Seen> seen = new HashMap<>();
    private final List<Spot> spots = new ArrayList<>();
    private ClientLevel level;

    private LogoutSpotsModule() {
        super("Logout Spots", "Show where players logged out, until they come back", "Visuals", false);
    }

    @Override public boolean active() { return isEnabled() && !spots.isEmpty(); }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
        if (mc.level != level) { level = mc.level; seen.clear(); spots.clear(); }
        Map<UUID, Seen> now = new HashMap<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            now.put(p.getUUID(), new Seen(p.getName().getString(), p.getBoundingBox(), p.getHealth()));
        }
        for (Map.Entry<UUID, Seen> e : seen.entrySet()) {
            if (now.containsKey(e.getKey()) || mc.getConnection().getPlayerInfo(e.getKey()) != null) continue;
            Seen s = e.getValue();
            spots.removeIf(sp -> sp.id.equals(e.getKey()));
            spots.add(new Spot(e.getKey(), s.name, s.box, s.health, System.currentTimeMillis()));
            if (notify.get()) Notifications.info("Logout", s.name + " logged out at "
                    + (int) s.box.getCenter().x + " " + (int) s.box.minY + " " + (int) s.box.getCenter().z);
        }
        spots.removeIf(sp -> now.containsKey(sp.id) || mc.getConnection().getPlayerInfo(sp.id) != null);
        seen.clear();
        seen.putAll(now);
    }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        for (Spot s : spots) {
            Gizmos.cuboid(s.box, GizmoStyle.strokeAndFill(0xE0FFC94A, 1.5f, 0x30FFC94A)).setAlwaysOnTop();
            long mins = (System.currentTimeMillis() - s.time) / 60000;
            Vec3 top = new Vec3(s.box.getCenter().x, s.box.maxY + 0.5, s.box.getCenter().z);
            double dist = eye.distanceTo(top);
            float scale = (float) (TextGizmo.Style.DEFAULT_SCALE * Math.max(1.0, dist / 6.0));
            Gizmos.billboardText(s.name + "  " + String.format("%.0f hp", s.health) + "  " + mins + "m ago", top,
                    TextGizmo.Style.forColorAndCentered(0xFFFFC94A).withScale(scale)).setAlwaysOnTop();
        }
    }

    @Override protected void onToggle(boolean enabled) { seen.clear(); spots.clear(); }
    @Override public String hudInfo() { return spots.isEmpty() ? null : Integer.toString(spots.size()); }
}
