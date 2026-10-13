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

/** Builds an obsidian cage around the nearest enemy. */
public final class AutoTrapModule extends FeatureModule {
    public static final AutoTrapModule INSTANCE = new AutoTrapModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Target range", 5, 2, 8, 0.5), "Only trap enemies this close.");
    private final Setting.Mode shape = setting(Setting.Mode.standalone("Cage", "Full", "Full", "Feet and top", "Top only"),
            "Full: four sides at feet and head height plus a roof. Feet and top: legs and roof only. Top only: just a roof, to stop them jumping out.");
    private final Setting.Num perTick = setting(Setting.Num.standalone("Blocks per tick", 2, 1, 8, 1), "How many blocks to place each tick.");
    private final Setting.Bool ender = setting(Setting.Bool.standalone("Ender chests too", true), "Use ender chests when you run out of obsidian.");
    private final Setting.Bool disableDone = setting(Setting.Bool.standalone("Turn off when done", false), "Switch off once the cage is complete.");
    private Player target;

    private AutoTrapModule() { super("Auto Trap", "Builds a cage of obsidian around the nearest enemy", "HVH", false); }

    @Override public void tick(Minecraft mc) {
        if (!ServerSafety.ready(mc)) return;
        LocalPlayer self = mc.player;
        target = Hvh.nearestEnemy(mc, range.get());
        if (target == null) return;
        int slot = Hvh.slot(self, s -> s.is(Items.OBSIDIAN) || s.is(Items.CRYING_OBSIDIAN));
        if (slot < 0 && ender.get()) slot = Hvh.slot(self, s -> s.is(Items.ENDER_CHEST));
        if (slot < 0) { Notifications.error("Auto Trap", "No obsidian in your hotbar"); setEnabled(false); return; }
        int budget = (int) perTick.get();
        boolean missing = false;
        for (BlockPos pos : cage(target.blockPosition())) {
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
            missing = true;
            if (budget > 0 && Hvh.place(mc, pos, slot, false)) budget--;
        }
        if (!missing && disableDone.get()) { Notifications.success("Auto Trap", target.getName().getString() + " trapped"); setEnabled(false); }
    }

    /** Cage blocks in build order: supports first, then sides, then the roof. */
    private List<BlockPos> cage(BlockPos feet) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos head = feet.above(), roof = feet.above(2);
        String s = shape.get();
        if (!s.equals("Top only")) for (Direction d : Hvh.SIDES) out.add(feet.relative(d));
        if (s.equals("Full")) for (Direction d : Hvh.SIDES) out.add(head.relative(d));
        // the roof needs something to sit on: a block beside the head, then the roof itself
        out.add(head.relative(Direction.NORTH));
        out.add(roof.relative(Direction.NORTH));
        out.add(roof);
        return out;
    }

    @Override public String hudInfo() { return target == null ? null : target.getName().getString(); }
}
