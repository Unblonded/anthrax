package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.client.FriendsModule;

/**
 * Raises your offhand shield when you are about to be hit. Smart timing only blocks enemies that are facing you
 * and in reach, drops the shield when your own attack is charged so you can hit back, skips axe users (an axe
 * disables the shield) and blocks incoming arrows.
 */
public final class AutoBlockModule extends FeatureModule {
    public static final AutoBlockModule INSTANCE = new AutoBlockModule();
    private final Setting.Mode mode = setting(Setting.Mode.standalone("Timing", "Smart", "Smart", "Always near"),
            "Smart blocks only real threats and lowers the shield to let you hit back; Always near keeps it up whenever an enemy is close.");
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 4, 2, 8, 0.5), "Enemies this close count as threats.");
    private final Setting.Bool mobs = setting(Setting.Bool.standalone("Hostile mobs too", true), "Also block for hostile mobs, not only players.");
    private final Setting.Bool facing = setting(Setting.Bool.standalone("Only if facing you", true), "Smart: ignore enemies looking away from you.");
    private final Setting.Bool counter = setting(Setting.Bool.standalone("Lower to hit back", true), "Smart: drop the shield while your attack is charged and an enemy is in your reach, so you can strike.");
    private final Setting.Bool axes = setting(Setting.Bool.standalone("Lower vs axes", true), "Smart: don't block a player holding an axe (it would disable your shield).");
    private final Setting.Bool arrows = setting(Setting.Bool.standalone("Block projectiles", true), "Raise the shield for arrows and tridents headed at you.");
    private final Setting.Num hold = setting(Setting.Num.standalone("Minimum hold (ticks)", 6, 0, 20, 1), "Keep the shield up at least this long once raised, so it doesn't flicker.");
    private boolean holding;
    private int heldFor;

    private AutoBlockModule() { super("Auto Block", "Raises your shield when you're about to be hit, and lowers it so you can hit back. Requires a shield in your offhand", "Combat", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        boolean can = p.getOffhandItem().is(Items.SHIELD) && mc.gui.screen() == null;
        boolean want = can && (mode.get().equals("Always near") ? anyNear(mc, p) : smart(mc, p));
        if (holding) heldFor++;
        if (!want && holding && heldFor < hold.get() && can) want = true;
        if (want && !holding) { mc.options.keyUse.setDown(true); holding = true; heldFor = 0; }
        else if (!want && holding) { mc.options.keyUse.setDown(false); holding = false; }
    }

    private boolean hostile(Entity e, LocalPlayer p) {
        if (e == p || !e.isAlive()) return false;
        if (e instanceof Player other) return !other.isSpectator() && !FriendsModule.isFriend(other);
        return mobs.get() && e instanceof Enemy;
    }

    private boolean anyNear(Minecraft mc, LocalPlayer p) {
        double r2 = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering()) if (hostile(e, p) && p.distanceToSqr(e) <= r2) return true;
        return false;
    }

    private boolean smart(Minecraft mc, LocalPlayer p) {
        if (arrows.get() && incoming(mc, p)) return true;
        // the shield takes a moment to become active, so look a block further than the enemy's reach
        double r = range.get() + 1;
        boolean threat = false, inMyReach = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!hostile(e, p) || !(e instanceof LivingEntity l)) continue;
            double d = p.distanceTo(e);
            if (d > r) continue;
            if (d <= 3.0) inMyReach = true;
            if (axes.get() && e instanceof Player && l.getMainHandItem().is(ItemTags.AXES)) continue;
            if (facing.get() && !facesMe(l, p)) continue;
            threat = true;
        }
        if (!threat) return false;
        // between our hits: block while our attack recharges, drop it once we can strike
        return !(counter.get() && inMyReach && p.getAttackStrengthScale(0.5f) >= 0.9f);
    }

    /** The entity's view points at us (within about 50 degrees). */
    private static boolean facesMe(LivingEntity e, LocalPlayer p) {
        Vec3 look = e.getViewVector(1f);
        Vec3 to = p.getEyePosition().subtract(e.getEyePosition()).normalize();
        return look.dot(to) > 0.64;
    }

    /** An arrow or trident in flight that will pass within about a block of us in the next second. */
    private static boolean incoming(Minecraft mc, LocalPlayer p) {
        Vec3 me = p.position().add(0, p.getBbHeight() / 2, 0);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Projectile proj) || !(e instanceof AbstractArrow) || proj.getOwner() == p) continue;
            Vec3 v = e.getDeltaMovement();
            if (v.lengthSqr() < 0.04 || e.distanceToSqr(p) > 20 * 20) continue;
            Vec3 rel = me.subtract(e.position());
            double t = rel.dot(v) / v.lengthSqr();           // ticks until closest approach
            if (t < 0 || t > 20) continue;
            if (e.position().add(v.scale(t)).distanceToSqr(me) < 1.6 * 1.6) return true;
        }
        return false;
    }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled && holding) { Minecraft.getInstance().options.keyUse.setDown(false); holding = false; }
    }

    @Override public String hudInfo() { return holding ? "Blocking" : null; }
}
