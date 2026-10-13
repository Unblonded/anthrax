package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Makes charged attacks critical hits (see CriticalsMixin). Packet sends two tiny position updates so the
 * server sees you falling; Mini jump and Jump do a real hop and land the hit on the way down.
 */
public final class CriticalsModule extends FeatureModule {
    public static final CriticalsModule INSTANCE = new CriticalsModule();
    private final Setting.Mode mode = setting(Setting.Mode.standalone("Mode", "Packet", "Packet", "Mini jump", "Jump"),
            "Packet is instant but stricter anticheats spot it. Mini jump is a small real hop. Jump is a full jump.");
    private final Setting.Bool onlyCharged = setting(Setting.Bool.standalone("Only charged hits", true), "Skip hits made before your attack has recharged (they would be weak anyway).");
    private final Setting.Bool players = setting(Setting.Bool.standalone("Only players", false), "Only crit players, not mobs.");
    private Entity pending;
    private int pendingTicks;
    /** Set while we replay a deferred attack, so the mixin lets it through. */
    public boolean replaying;

    private CriticalsModule() { super("Criticals", "Makes charged attacks critical hits. Packet is instant. Mini jump and Jump perform a real hop and time the hit on the way down", "Combat", false); }

    /** Called before every attack; returns true if the attack should be held back until the hop comes down. */
    public boolean beforeAttack(Minecraft mc, Entity target) {
        LocalPlayer p = mc.player;
        if (replaying || p == null || !p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()) return false;
        if (players.get() && !(target instanceof net.minecraft.world.entity.player.Player)) return false;
        if (onlyCharged.get() && p.getAttackStrengthScale(0.5f) < 0.9f) return false;
        switch (mode.get()) {
            case "Packet" -> {
                double x = p.getX(), y = p.getY(), z = p.getZ();
                var conn = mc.getConnection();
                conn.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false, p.horizontalCollision));
                conn.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, p.horizontalCollision));
                return false;
            }
            case "Mini jump" -> { p.setDeltaMovement(p.getDeltaMovement().x, 0.25, p.getDeltaMovement().z); }
            default -> p.jumpFromGround();
        }
        pending = target;
        pendingTicks = 20;
        return true;
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (pending == null || p == null || mc.gameMode == null) return;
        if (--pendingTicks <= 0 || !pending.isAlive()) { pending = null; return; }
        // land the hit once we are falling (that is what makes it a crit)
        if (!p.onGround() && p.getDeltaMovement().y < 0 && p.fallDistance > 0) {
            Entity t = pending;
            pending = null;
            if (!p.isWithinEntityInteractionRange(t, 0.0)) return;
            replaying = true;
            try { CombatUtil.attack(mc, t); } finally { replaying = false; }
        }
    }

    @Override protected void onToggle(boolean enabled) { pending = null; }
}
