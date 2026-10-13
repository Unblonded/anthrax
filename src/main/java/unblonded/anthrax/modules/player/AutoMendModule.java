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

/** Throws experience bottles at your feet while you sneak, to repair Mending gear. */
public final class AutoMendModule extends FeatureModule {
    public static final AutoMendModule INSTANCE = new AutoMendModule();
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ticks)", 3, 1, 20, 1), "Ticks between bottles.");
    private final Setting.Bool onlyDamaged = setting(Setting.Bool.standalone("Stop when repaired", true), "Only throws while something you wear or hold is damaged.");
    private int cooldown;

    private AutoMendModule() { super("Auto Mend", "Throws experience bottles while you sneak to repair Mending gear", "Player", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || !p.isShiftKeyDown() || mc.gui.screen() != null) return;
        if (cooldown-- > 0) return;
        if (onlyDamaged.get() && !anyDamaged(p)) return;
        int slot = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(Items.EXPERIENCE_BOTTLE)) { slot = i; break; }
        if (slot < 0) return;
        int prev = p.getInventory().getSelectedSlot();
        float pitch = p.getXRot();
        p.getInventory().setSelectedSlot(slot);
        p.setXRot(90f);
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        p.setXRot(pitch);
        p.getInventory().setSelectedSlot(prev);
        cooldown = (int) delay.get();
    }

    private static boolean anyDamaged(LocalPlayer p) {
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.isDamaged()) return true;
        }
        return false;
    }
}
