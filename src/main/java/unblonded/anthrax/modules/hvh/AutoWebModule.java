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

/** Places cobwebs in enemies to slow them down. */
public final class AutoWebModule extends FeatureModule {
    public static final AutoWebModule INSTANCE = new AutoWebModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Target range", 4.5, 2, 6, 0.5), "Only web enemies this close.");
    private final Setting.Bool head = setting(Setting.Bool.standalone("Head too", false), "Also web their head block (holds them longer, costs a web).");
    private final Setting.Bool predict = setting(Setting.Bool.standalone("Predict movement", true), "Aim at where they are heading if they are moving.");
    private final Setting.Num delay = setting(Setting.Num.standalone("Delay (ticks)", 4, 0, 20, 1), "Ticks between webs.");
    private int cooldown;

    private AutoWebModule() { super("Auto Web", "Places cobwebs in enemies to slow them down", "HVH", false); }

    @Override public void tick(Minecraft mc) {
        if (!ServerSafety.ready(mc) || cooldown-- > 0) return;
        LocalPlayer self = mc.player;
        Player target = Hvh.nearestEnemy(mc, range.get());
        if (target == null) return;
        int slot = Hvh.slot(self, s -> s.is(Items.COBWEB));
        if (slot < 0) return;
        Vec3 at = target.position();
        if (predict.get()) {
            Vec3 v = target.getDeltaMovement();
            at = at.add(v.x * 3, 0, v.z * 3);
        }
        BlockPos feet = BlockPos.containing(at);
        if (mc.level.getBlockState(feet).is(net.minecraft.world.level.block.Blocks.COBWEB) && !head.get()) return;
        boolean placed = Hvh.place(mc, feet, slot, true);
        if (!placed && head.get()) placed = Hvh.place(mc, feet.above(), slot, true);
        if (placed) cooldown = (int) delay.get();
    }
}
