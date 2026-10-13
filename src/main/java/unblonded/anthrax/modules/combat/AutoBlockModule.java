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

/** Raises your offhand shield when an enemy gets close. */
public final class AutoBlockModule extends FeatureModule {
    public static final AutoBlockModule INSTANCE = new AutoBlockModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 4, 2, 8, 0.5), "Raise the shield when an enemy is this close.");
    private final Setting.Bool mobs = setting(Setting.Bool.standalone("Hostile mobs too", true), "Also block for hostile mobs, not only players.");
    private boolean holding;

    private AutoBlockModule() { super("Auto Block", "Raises your shield when an enemy gets close. Requires a shield in your offhand", "Combat", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        boolean want = p.getOffhandItem().is(Items.SHIELD) && mc.gui.screen() == null && threat(mc, p);
        if (want) { mc.options.keyUse.setDown(true); holding = true; }
        else if (holding) { mc.options.keyUse.setDown(false); holding = false; }
    }

    private boolean threat(Minecraft mc, LocalPlayer p) {
        double r2 = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (e == p || !e.isAlive() || p.distanceToSqr(e) > r2) continue;
            if (e instanceof net.minecraft.world.entity.player.Player other && !unblonded.anthrax.modules.client.FriendsModule.isFriend(other)) return true;
            if (mobs.get() && e instanceof net.minecraft.world.entity.monster.Enemy) return true;
        }
        return false;
    }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled && holding) { Minecraft.getInstance().options.keyUse.setDown(false); holding = false; }
    }
}
