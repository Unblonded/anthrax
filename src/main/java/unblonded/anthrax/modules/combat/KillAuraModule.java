package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Attacks the best target in reach on a full attack cooldown. Picks by distance, health or angle,
 * optionally waits for jump crits, can turn your view onto the target (snap or smooth) and pauses
 * while you eat or use items. Friends are never hit.
 */
public final class KillAuraModule extends FeatureModule {
    public static final KillAuraModule INSTANCE = new KillAuraModule();

    private final Setting.Num range = setting(Setting.Num.standalone("Range", 3.0, 1, 6, 0.1), "How far away a target can be (measured to the nearest point of its hitbox).");
    private final Setting.Num cooldown = setting(Setting.Num.standalone("Cooldown %", 92, 10, 100, 1), "Only swing once your attack has recharged this much. 90%+ gives full-damage hits.");
    private final Setting.Mode priority = setting(Setting.Mode.standalone("Priority", "Distance", "Distance", "Health", "Angle"), "Which target to pick when several are in reach.");
    private final Setting.Mode rotate = setting(Setting.Mode.standalone("Rotate", "Smooth", "Smooth", "Snap", "None"), "Turn your view onto the target. Smooth looks natural, Snap is instant, None never moves the camera.");
    private final Setting.Num turnSpeed = setting(Setting.Num.standalone("Turn speed", 540, 90, 1440, 30), "How fast Smooth rotation turns, in degrees per second.");
    private final Setting.Num fov = setting(Setting.Num.standalone("Field of view", 180, 10, 180, 5), "Only attack targets within this angle of where you look (180 = anywhere around you).");
    private final Setting.Bool crits = setting(Setting.Bool.standalone("Wait for crits", true), "While you are jumping, hold the swing until you start falling so it lands as a critical hit.");
    private final Setting.Bool players = setting(Setting.Bool.standalone("Players", true));
    private final Setting.Bool hostiles = setting(Setting.Bool.standalone("Hostile mobs", true));
    private final Setting.Bool animals = setting(Setting.Bool.standalone("Animals", false));
    private final Setting.Bool invisible = setting(Setting.Bool.standalone("Invisible entities", false));
    private final Setting.Bool walls = setting(Setting.Bool.standalone("Through walls", false), "Also attack targets you cannot see.");
    private final Setting.Bool weaponOnly = setting(Setting.Bool.standalone("Only holding weapon", false));
    private final Setting.Bool pauseUsing = setting(Setting.Bool.standalone("Pause while eating", true));
    private final Setting.Num jitter = setting(Setting.Num.standalone("Random delay (ms)", 0, 0, 150, 5), "Adds a random pause between swings so the rhythm is less mechanical.");
    private LivingEntity target;
    private long nextAllowed;

    private KillAuraModule() {
        super("Kill Aura", "Hit the best target in reach on a full cooldown, with crits and smooth aim", "Combat", false);
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        target = null;
        if (self == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null || self.isSpectator()) return;
        if (pauseUsing.get() && self.isUsingItem()) return;
        if (ServerSafety.paused()) return;
        if (weaponOnly.get() && !CombatUtil.isWeapon(self.getMainHandItem())) return;

        double reachSq = range.get() * range.get(), best = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!CombatUtil.attackable(self, e, players.get(), hostiles.get(), animals.get(), invisible.get())) continue;
            double d = CombatUtil.reachSq(self, e);
            if (d > reachSq || (!walls.get() && !self.hasLineOfSight(e))) continue;
            float angle = CombatUtil.angleTo(self, CombatUtil.aimPoint(self, e, 1f));
            if (angle > fov.get()) continue;
            LivingEntity living = (LivingEntity) e;
            double score = switch (priority.get()) {
                case "Health" -> living.getHealth() + living.getAbsorptionAmount();
                case "Angle" -> angle;
                default -> d;
            };
            if (score < best) { best = score; target = living; }
        }
        if (target == null) return;
        CombatUtil.markTarget(target);

        if (rotate.get().equals("Snap")) CombatUtil.turnToward(self, CombatUtil.aimPoint(self, target, 1f), 1f, 0, 0);
        // smooth aim must be roughly on target before swinging, or it looks like hitting the air
        if (rotate.get().equals("Smooth") && CombatUtil.angleTo(self, CombatUtil.aimPoint(self, target, 1f)) > 35) return;
        if (CombatUtil.cooldown(self) < cooldown.get() / 100.0) return;
        if (crits.get() && CombatUtil.critPending(self)) return;
        long now = System.currentTimeMillis();
        if (now < nextAllowed) return;
        CombatUtil.attack(mc, target);
        nextAllowed = jitter.get() <= 0 ? 0 : now + ThreadLocalRandom.current().nextLong((long) jitter.get() + 1);
    }

    @Override public void frame(Minecraft mc, float dt) {
        LivingEntity t = target;
        if (t == null || mc.player == null || mc.gui.screen() != null || !rotate.get().equals("Smooth")) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        CombatUtil.turnToward(mc.player, CombatUtil.aimPoint(mc.player, t, partial), partial, (float) turnSpeed.get(), dt);
    }

    public LivingEntity target() { return target; }

    @Override public String hudInfo() { return target == null ? priority.get() : target.getName().getString(); }
    @Override protected void onToggle(boolean enabled) {
        target = null;
        // TP Aura replaces Kill Aura: only one of them runs
        if (enabled) unblonded.anthrax.modules.hvh.TpAuraModule.INSTANCE.setEnabled(false);
    }
}
