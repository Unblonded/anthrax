package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Eats golden apples when your health drops (enchanted first) and regular food when hungry, by
 * switching to the item and holding use until it is eaten, then switching back.
 */
public final class AutoEatModule extends FeatureModule {
    private final Setting.Num health = setting(Setting.Num.standalone("Gapple below health", 10, 1, 19, 1));
    private final Setting.Num hunger = setting(Setting.Num.standalone("Eat below hunger", 14, 1, 19, 1));
    private final Setting.Bool gapples = setting(Setting.Bool.standalone("Golden apples", true));
    private final Setting.Bool food = setting(Setting.Bool.standalone("Regular food", true));
    private int previous = -1, eatingSlot = -1, ticks;

    public AutoEatModule() {
        super("Auto Eat", "Eat golden apples at low health and food when hungry, then switch back", "Combat", false);
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || mc.gameMode == null || self.isCreative() || self.isSpectator()) { stop(mc); return; }
        if (eatingSlot >= 0) {
            ticks++;
            ItemStack held = self.getInventory().getItem(eatingSlot);
            // done when the item changed, or the eat clearly is not happening
            if (self.getInventory().getSelectedSlot() != eatingSlot || held.isEmpty() || !held.has(DataComponents.FOOD)
                    || ticks > 60 || (ticks > 6 && !self.isUsingItem())) { stop(mc); return; }
            mc.options.keyUse.setDown(true);
            return;
        }
        if (mc.gui.screen() != null) return;
        float hp = self.getHealth() + self.getAbsorptionAmount();
        int slot = -1;
        if (gapples.get() && hp <= health.get()) {
            slot = CombatUtil.findHotbar(self, s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
            if (slot < 0) slot = CombatUtil.findHotbar(self, s -> s.is(Items.GOLDEN_APPLE));
        }
        if (slot < 0 && food.get() && self.getFoodData().getFoodLevel() <= hunger.get()) slot = bestFood(self);
        if (slot < 0) return;
        previous = self.getInventory().getSelectedSlot();
        self.getInventory().setSelectedSlot(slot);
        eatingSlot = slot;
        ticks = 0;
        mc.options.keyUse.setDown(true);
    }

    private static int bestFood(LocalPlayer self) {
        int best = -1, bestNutrition = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = self.getInventory().getItem(i);
            var props = s.get(DataComponents.FOOD);
            if (props == null || s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO)
                    || s.is(Items.PUFFERFISH) || s.is(Items.CHORUS_FRUIT) || s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)
                    || s.is(Items.SUSPICIOUS_STEW)) continue;
            if (props.nutrition() > bestNutrition) { bestNutrition = props.nutrition(); best = i; }
        }
        return best;
    }

    private void stop(Minecraft mc) {
        if (eatingSlot < 0) return;
        mc.options.keyUse.setDown(false);
        if (mc.player != null && previous >= 0) mc.player.getInventory().setSelectedSlot(previous);
        eatingSlot = previous = -1;
    }

    public boolean eating() { return eatingSlot >= 0; }

    @Override protected void onToggle(boolean enabled) { if (!enabled) stop(Minecraft.getInstance()); }
}
