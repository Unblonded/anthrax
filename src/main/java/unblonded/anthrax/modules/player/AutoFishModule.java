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

/** Casts, waits for a bite, reels in and recasts. */
public final class AutoFishModule extends FeatureModule {
    public static final AutoFishModule INSTANCE = new AutoFishModule();
    private final Setting.Num recast = setting(Setting.Num.standalone("Recast delay (ticks)", 15, 5, 60, 1), "Pause between reeling in and casting again.");
    private final Setting.Bool castFirst = setting(Setting.Bool.standalone("Cast when turned on", true), "Throws the line straight away if it is not out.");
    private int wait;

    private AutoFishModule() { super("Auto Fish", "Fishes automatically: casts, detects bites, reels in and recasts", "Player", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.gui.screen() != null) return;
        InteractionHand hand = p.getMainHandItem().is(Items.FISHING_ROD) ? InteractionHand.MAIN_HAND
                : p.getOffhandItem().is(Items.FISHING_ROD) ? InteractionHand.OFF_HAND : null;
        if (hand == null) return;
        if (wait > 0) { if (--wait == 0) use(mc, hand); return; }
        var hook = p.fishing;
        if (hook == null) {
            if (castFirst.get()) { use(mc, hand); wait = 0; }
            return;
        }
        boolean biting = hook.getEntityData().get(unblonded.anthrax.mixin.FishingHookAccessor.anthrax$biting());
        if (biting) { use(mc, hand); wait = (int) recast.get(); }
    }

    private static void use(Minecraft mc, InteractionHand hand) {
        if (mc.gameMode.useItem(mc.player, hand).consumesAction()) mc.player.swing(hand);
    }
}
