package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/**
 * Equips the best armor in your inventory, one piece at a time with a configurable delay. Pieces are
 * rated by armor, toughness and protection enchantments; blast protection can be preferred for
 * crystal PvP, and an equipped elytra is never swapped out.
 */
public final class AutoArmorModule extends FeatureModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ms)", 150, 0, 1000, 25));
    private final Setting.Mode protection = setting(Setting.Mode.standalone("Prefer", "Protection", "Protection", "Blast protection"));
    private final Setting.Bool keepElytra = setting(Setting.Bool.standalone("Keep elytra on", true));
    private final Setting.Bool inInventoryOnly = setting(Setting.Bool.standalone("Only while inventory closed", false));
    private long nextAction;

    public AutoArmorModule() {
        super("Auto Armor", "Automatically wear your best armor", "Combat", false);
    }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || p.isCreative() || System.currentTimeMillis() < nextAction) return;
        if (p.containerMenu != p.inventoryMenu || !p.inventoryMenu.getCarried().isEmpty()) return;
        if (inInventoryOnly.get() && mc.gui.screen() != null) return;
        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            ItemStack worn = p.getItemBySlot(slot);
            if (slot == EquipmentSlot.CHEST && keepElytra.get() && worn.is(Items.ELYTRA)) continue;
            double wornScore = score(mc, worn, slot);
            int bestIndex = -1;
            double bestScore = wornScore;
            for (int inv = 0; inv < 36; inv++) {
                ItemStack s = p.getInventory().getItem(inv);
                double sc = score(mc, s, slot);
                if (sc > bestScore + 0.01) { bestScore = sc; bestIndex = inv; }
            }
            if (bestIndex < 0) continue;
            int menuSlot = bestIndex < 9 ? bestIndex + 36 : bestIndex;
            int armorMenuSlot = 5 + i;
            int menu = p.inventoryMenu.containerId;
            if (worn.isEmpty()) {
                mc.gameMode.handleContainerInput(menu, menuSlot, 0, ContainerInput.QUICK_MOVE, p);
            } else {
                mc.gameMode.handleContainerInput(menu, menuSlot, 0, ContainerInput.PICKUP, p);
                mc.gameMode.handleContainerInput(menu, armorMenuSlot, 0, ContainerInput.PICKUP, p);
                mc.gameMode.handleContainerInput(menu, menuSlot, 0, ContainerInput.PICKUP, p);
            }
            nextAction = System.currentTimeMillis() + (long) delay.get();
            return;
        }
    }

    private double score(Minecraft mc, ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0;
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null || eq.slot() != slot) return 0;
        var mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        double armor = 0, toughness = 0;
        if (mods != null) {
            armor = mods.compute(Attributes.ARMOR, 0, slot);
            toughness = mods.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
        }
        double score = armor * 10 + toughness * 4;
        if (mc.level != null) {
            var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> prot = registry.getOrThrow(Enchantments.PROTECTION);
            Holder<Enchantment> blast = registry.getOrThrow(Enchantments.BLAST_PROTECTION);
            boolean preferBlast = protection.get().equals("Blast protection");
            score += EnchantmentHelper.getItemEnchantmentLevel(prot, stack) * (preferBlast ? 2 : 4);
            score += EnchantmentHelper.getItemEnchantmentLevel(blast, stack) * (preferBlast ? 5 : 1.5);
        }
        // Prefer undamaged pieces slightly; never swap in something about to break.
        if (stack.isDamageableItem()) {
            double left = 1 - stack.getDamageValue() / (double) Math.max(1, stack.getMaxDamage());
            if (left < 0.05) return 0.5;
            score += left;
        }
        return score;
    }
}
