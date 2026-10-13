package unblonded.anthrax.modules.visual;

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

/** Boxes around dropped items through walls, coloured by rarity. */
public final class ItemEspModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final ItemEspModule INSTANCE = new ItemEspModule();
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 48, 8, 128, 8), "Only items this close.");
    private final Setting.Bool beams = setting(Setting.Bool.standalone("Beams", false), "A vertical line above each item so they are easy to spot.");
    private final Setting.Bool valuableOnly = setting(Setting.Bool.standalone("Only rare items", false), "Hides common items.");

    private ItemEspModule() { super("Item ESP", "Highlights dropped items through walls, with optional beams", "Visuals", false); }

    @Override public boolean active() { return isEnabled(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        double r2 = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.item.ItemEntity item) || mc.player.distanceToSqr(e) > r2) continue;
            var rarity = item.getItem().getRarity();
            if (valuableOnly.get() && rarity == net.minecraft.world.item.Rarity.COMMON) continue;
            int color = switch (rarity) {
                case UNCOMMON -> 0xFFE45C; case RARE -> 0x55FFFF; case EPIC -> 0xFF55FF; default -> 0xFFFFFF;
            };
            Vec3 pos = e.getPosition(partial);
            var box = e.getBoundingBox().move(pos.subtract(e.position())).inflate(0.05);
            net.minecraft.gizmos.Gizmos.cuboid(box, net.minecraft.gizmos.GizmoStyle.strokeAndFill(WorldOverlays.argb(0xE0, color), 1.5f, WorldOverlays.argb(0x30, color))).setAlwaysOnTop();
            if (beams.get()) net.minecraft.gizmos.Gizmos.line(pos, pos.add(0, 3, 0), WorldOverlays.argb(0xA0, color), 1.5f).setAlwaysOnTop();
        }
    }
}
