package unblonded.anthrax.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Small shared helpers for movement, player and world modules: attribute boosts, input, block placement. */
public final class Moves {
    private Moves() {}

    /** Keeps one transient modifier on the local player's attribute at {@code value}; 0 removes it. */
    public static void boost(Holder<Attribute> attribute, String id, double value, AttributeModifier.Operation op) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        Identifier key = Identifier.fromNamespaceAndPath("anthrax", id);
        AttributeModifier current = inst.getModifier(key);
        if (Math.abs(value) < 1e-6) { if (current != null) inst.removeModifier(key); return; }
        if (current != null && Math.abs(current.amount() - value) < 1e-6 && current.operation() == op) return;
        if (current != null) inst.removeModifier(key);
        inst.addTransientModifier(new AttributeModifier(key, value, op));
    }

    public static void clear(Holder<Attribute> attribute, String id) { boost(attribute, id, 0, AttributeModifier.Operation.ADD_VALUE); }

    /** True while any movement key is held. */
    public static boolean moving(Minecraft mc) {
        var o = mc.options;
        return o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown();
    }

    /** Horizontal wish direction from the movement keys and the player's yaw, unit length (or zero). */
    public static Vec3 wishDir(Minecraft mc, float yaw) {
        var o = mc.options;
        double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        if (f == 0 && s == 0) return Vec3.ZERO;
        double r = Math.toRadians(yaw);
        Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r)), left = new Vec3(Math.cos(r), 0, Math.sin(r));
        return fwd.scale(f).add(left.scale(s)).normalize();
    }

    /** Hotbar slot holding a placeable block, preferring the selected one; -1 if none. */
    public static int blockSlot(LocalPlayer p) {
        if (p.getMainHandItem().getItem() instanceof BlockItem) return p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    /**
     * Places the held block at {@code target} by clicking a solid neighbour's face, the way a player would.
     * With {@code air} it clicks the target itself when nothing is next to it (works on servers that do not
     * check the clicked block). Returns true if a placement was sent.
     */
    public static boolean place(Minecraft mc, BlockPos target, boolean air) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) return false;
        if (!mc.level.getBlockState(target).canBeReplaced()) return false;
        for (Direction d : Direction.values()) {
            BlockPos n = target.relative(d);
            if (mc.level.getBlockState(n).canBeReplaced()) continue;
            Direction face = d.getOpposite();
            Vec3 hit = Vec3.atCenterOf(n).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
            return click(mc, new BlockHitResult(hit, face, n, false));
        }
        if (!air) return false;
        return click(mc, new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
    }

    private static boolean click(Minecraft mc, BlockHitResult hit) {
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
        return result.consumesAction();
    }
}
