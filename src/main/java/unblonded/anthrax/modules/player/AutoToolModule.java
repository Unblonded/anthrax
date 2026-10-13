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

/** Switches to the fastest tool for the block you are mining, and back afterwards. */
public final class AutoToolModule extends FeatureModule {
    public static final AutoToolModule INSTANCE = new AutoToolModule();
    private final Setting.Bool switchBack = setting(Setting.Bool.standalone("Switch back", true), "Returns to the slot you had once you stop mining.");
    private int previous = -1;

    private AutoToolModule() { super("Auto Tool", "Switches to the right tool for the block you are mining", "Player", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.level == null) return;
        boolean mining = mc.options.keyAttack.isDown() && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
        if (!mining) {
            if (switchBack.get() && previous >= 0) { p.getInventory().setSelectedSlot(previous); previous = -1; }
            return;
        }
        var state = mc.level.getBlockState(((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos());
        int current = p.getInventory().getSelectedSlot(), best = current;
        float bestSpeed = p.getInventory().getItem(current).getDestroySpeed(state);
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            float sp = s.getDestroySpeed(state);
            // avoid burning the last durability of a tool
            if (s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 2) continue;
            if (sp > bestSpeed + 0.01f) { bestSpeed = sp; best = i; }
        }
        if (best != current) {
            if (previous < 0) previous = current;
            p.getInventory().setSelectedSlot(best);
        }
    }
}
