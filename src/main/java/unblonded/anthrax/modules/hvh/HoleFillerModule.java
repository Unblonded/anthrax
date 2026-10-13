package unblonded.anthrax.modules.hvh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.combat.CombatUtil;
import unblonded.anthrax.modules.combat.ServerSafety;
import unblonded.anthrax.modules.visual.WorldOverlays;

import java.util.ArrayList;
import java.util.List;

/** Fills the safe 1x1 holes near enemies so they cannot hide from crystals. */
public final class HoleFillerModule extends FeatureModule {
    public static final HoleFillerModule INSTANCE = new HoleFillerModule();
    private final Setting.Num enemyRange = setting(Setting.Num.standalone("Holes within (of enemy)", 3, 1, 6, 0.5), "Only fill holes this close to an enemy.");
    private final Setting.Num targetRange = setting(Setting.Num.standalone("Enemy range", 10, 4, 16, 1), "Only care about enemies this close to you.");
    private final Setting.Num perTick = setting(Setting.Num.standalone("Blocks per tick", 1, 1, 4, 1), "Holes to fill each tick.");
    private final Setting.Bool webs = setting(Setting.Bool.standalone("Use webs if no obsidian", true), "A web in a hole also stops them standing in it.");
    private final Setting.Bool skipOwn = setting(Setting.Bool.standalone("Keep holes near you", true), "Leaves holes within 2 blocks of you open for yourself.");

    private HoleFillerModule() { super("Hole Filler", "Fills the safe 1x1 holes around enemies so they can't escape crystal damage", "HVH", false); }

    @Override public void tick(Minecraft mc) {
        if (!ServerSafety.ready(mc)) return;
        LocalPlayer self = mc.player;
        List<Player> foes = Hvh.enemies(mc, targetRange.get());
        if (foes.isEmpty()) return;
        int slot = Hvh.slot(self, s -> s.is(Items.OBSIDIAN) || s.is(Items.CRYING_OBSIDIAN));
        if (slot < 0 && webs.get()) slot = Hvh.slot(self, s -> s.is(Items.COBWEB));
        if (slot < 0) return;
        int budget = (int) perTick.get(), r = 5;
        BlockPos at = self.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 2, r))) {
            if (budget <= 0) return;
            if (!ServerSafety.canReach(self, pos) || !Hvh.safeHole(mc, pos)) continue;
            if (skipOwn.get() && pos.distSqr(at) <= 4) continue;
            boolean nearFoe = false;
            for (Player p : foes) {
                if (p.blockPosition().equals(pos)) { nearFoe = false; break; }   // already in it: too late to fill
                if (Vec3.atCenterOf(pos).distanceToSqr(p.position()) <= enemyRange.get() * enemyRange.get()) nearFoe = true;
            }
            if (nearFoe && Hvh.place(mc, pos.immutable(), slot, false)) budget--;
        }
    }
}
