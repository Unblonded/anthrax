package unblonded.anthrax.modules.world;

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

/** Places the held block in the air at your crosshair when you right-click nothing. */
public final class AirPlaceModule extends FeatureModule {
    public static final AirPlaceModule INSTANCE = new AirPlaceModule();
    private final Setting.Num distance = setting(Setting.Num.standalone("Distance", 4, 1, 5, 0.5), "How far in front of you the block goes.");
    private final Setting.Bool preview = setting(Setting.Bool.standalone("Show preview", true), "Outlines where the block will go.");
    private boolean wasDown;

    private AirPlaceModule() { super("Air Place", "Places blocks in the air along your crosshair, up to your reach", "World", false); }

    public BlockPos spot(Minecraft mc) {
        if (mc.player == null || mc.hitResult == null || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return null;
        if (!(mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem)) return null;
        return BlockPos.containing(mc.player.getEyePosition().add(mc.player.getLookAngle().scale(distance.get())));
    }

    public boolean showPreview() { return isEnabled() && preview.get(); }

    @Override public void tick(Minecraft mc) {
        boolean down = mc.options.keyUse.isDown() && mc.gui.screen() == null;
        if (down && !wasDown) {
            BlockPos at = spot(mc);
            if (at != null) Moves.place(mc, at, true);
        }
        wasDown = down;
    }
}
