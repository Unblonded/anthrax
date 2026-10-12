package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.concurrent.ThreadLocalRandom;

/** Attacks whatever your crosshair is on the moment your cooldown is ready (legit-looking aura). */
public final class TriggerBotModule extends FeatureModule {
    private final Setting.Num cooldown = setting(Setting.Num.standalone("Cooldown %", 95, 10, 100, 1));
    private final Setting.Num minDelay = setting(Setting.Num.standalone("Min reaction (ms)", 40, 0, 300, 5));
    private final Setting.Num maxDelay = setting(Setting.Num.standalone("Max reaction (ms)", 90, 0, 400, 5));
    private final Setting.Bool crits = setting(Setting.Bool.standalone("Wait for crits", true));
    private final Setting.Bool players = setting(Setting.Bool.standalone("Players", true));
    private final Setting.Bool hostiles = setting(Setting.Bool.standalone("Hostile mobs", true));
    private final Setting.Bool animals = setting(Setting.Bool.standalone("Animals", false));
    private final Setting.Bool weaponOnly = setting(Setting.Bool.standalone("Only holding weapon", true));
    private Entity seen;
    private long fireAt;

    public TriggerBotModule() {
        super("Trigger Bot", "Hit what your crosshair is on as soon as your cooldown is ready", "Combat", false);
    }

    @Override public void tick(Minecraft mc) {
        var self = mc.player;
        if (self == null || mc.gameMode == null || mc.gui.screen() != null || self.isUsingItem()) { seen = null; return; }
        if (weaponOnly.get() && !CombatUtil.isWeapon(self.getMainHandItem())) { seen = null; return; }
        Entity e = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (e == null || !CombatUtil.attackable(self, e, players.get(), hostiles.get(), animals.get(), false)) { seen = null; return; }
        long now = System.currentTimeMillis();
        if (e != seen) {
            // a human needs a moment to react to a new target
            seen = e;
            long lo = (long) Math.min(minDelay.get(), maxDelay.get()), hi = (long) Math.max(minDelay.get(), maxDelay.get());
            fireAt = now + lo + (hi > lo ? ThreadLocalRandom.current().nextLong(hi - lo + 1) : 0);
        }
        if (now < fireAt || CombatUtil.cooldown(self) < cooldown.get() / 100.0) return;
        if (crits.get() && CombatUtil.critPending(self)) return;
        CombatUtil.attack(mc, e);
    }

    @Override protected void onToggle(boolean enabled) { seen = null; }
}
