package unblonded.anthrax.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Warns when armour or the held item is about to break. */
public final class DurabilityAlertModule extends FeatureModule {
    public static final DurabilityAlertModule INSTANCE = new DurabilityAlertModule();
    private final Setting.Num percent = setting(Setting.Num.standalone("Warn below (%)", 10, 1, 50, 1), "Warns once an item drops below this much durability.");
    private final Setting.Bool sound = setting(Setting.Bool.standalone("Sound", true), "Plays a sound with the warning.");
    private final java.util.Set<String> warned = new java.util.HashSet<>();
    private int tick;

    private DurabilityAlertModule() { super("Durability Alert", "Warns when armour or a weapon is about to break", "Player", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || ++tick % 20 != 0) return;
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack s = p.getItemBySlot(slot);
            String key = slot.getName();
            if (!s.isDamageableItem()) { warned.remove(key); continue; }
            double left = 100.0 * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage();
            if (left > percent.get()) { warned.remove(key); continue; }
            if (!warned.add(key)) continue;
            Notifications.warn("Durability", s.getHoverName().getString() + " at " + Math.round(left) + "%");
            if (sound.get()) p.playSound(net.minecraft.sounds.SoundEvents.ITEM_BREAK.value(), 0.6f, 1.4f);
        }
    }
}
