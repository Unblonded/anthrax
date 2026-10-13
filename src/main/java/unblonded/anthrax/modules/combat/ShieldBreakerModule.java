package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Switches to an axe when you attack a blocking player, then switches back. */
public final class ShieldBreakerModule extends FeatureModule {
    public static final ShieldBreakerModule INSTANCE = new ShieldBreakerModule();
    private final Setting.Num hold = setting(Setting.Num.standalone("Switch back after (ticks)", 6, 1, 40, 1), "How long to keep the axe out.");
    private int previous = -1, timer;

    private ShieldBreakerModule() { super("Shield Breaker", "Switches to an axe in your hotbar when you attack a player who is blocking, then switches back", "Combat", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (previous >= 0 && --timer <= 0) { p.getInventory().setSelectedSlot(previous); previous = -1; }
        if (!mc.options.keyAttack.isDown() || !(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof net.minecraft.world.entity.player.Player target) || !target.isBlocking()) return;
        if (p.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)) return;
        for (int i = 0; i < 9; i++) {
            if (!p.getInventory().getItem(i).is(net.minecraft.tags.ItemTags.AXES)) continue;
            if (previous < 0) previous = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(i);
            timer = (int) hold.get();
            return;
        }
    }
}
