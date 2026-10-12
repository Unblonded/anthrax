package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Switches to your hardest-hitting hotbar weapon right before every attack (yours or an aura's), to
 * an axe when the target is holding up a shield, and optionally back to the previous slot afterwards.
 */
public final class AutoWeaponModule extends FeatureModule {
    public static final AutoWeaponModule INSTANCE = new AutoWeaponModule();

    private final Setting.Bool shieldBreaker = setting(Setting.Bool.standalone("Axe on shields", true));
    private final Setting.Bool switchBack = setting(Setting.Bool.standalone("Switch back", false));
    private final Setting.Bool livingOnly = setting(Setting.Bool.standalone("Only on mobs and players", true));
    private int previous = -1, restoreIn;

    private AutoWeaponModule() {
        super("Auto Weapon", "Switch to your best weapon (or an axe against shields) when attacking", "Combat", false);
    }

    /** Called from the attack hook before the attack packet is sent. */
    public void onAttack(Minecraft mc, Entity target) {
        if (!isEnabled() || mc.player == null) return;
        if (livingOnly.get() && !(target instanceof LivingEntity)) return;
        var self = mc.player;
        int slot = -1;
        if (shieldBreaker.get() && target instanceof Player p && p.isBlocking())
            slot = CombatUtil.findHotbar(self, s -> s.is(ItemTags.AXES));
        if (slot < 0) slot = CombatUtil.bestWeapon(self);
        int current = self.getInventory().getSelectedSlot();
        if (slot < 0 || slot == current) return;
        if (CombatUtil.attackDamage(self.getInventory().getItem(slot)) <= CombatUtil.attackDamage(self.getMainHandItem())
                && !(target instanceof Player p && p.isBlocking())) return;
        if (switchBack.get() && previous < 0) previous = current;
        self.getInventory().setSelectedSlot(slot);
        restoreIn = 3;
    }

    @Override public void tick(Minecraft mc) {
        if (previous < 0 || mc.player == null) return;
        if (--restoreIn > 0) return;
        mc.player.getInventory().setSelectedSlot(previous);
        previous = -1;
    }

    @Override protected void onToggle(boolean enabled) { previous = -1; }
}
