package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unblonded.anthrax.modules.Safe;
import unblonded.anthrax.modules.cosmetic.BlockEffectsModule;
import unblonded.anthrax.modules.cosmetic.PlaceAnimationModule;

/** Cosmetics: blocks you break and blocks you place. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CosmeticGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void anthrax$broken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !BlockEffectsModule.INSTANCE.isEnabled()) return;
        BlockState state = mc.level.getBlockState(pos);
        Safe.run(BlockEffectsModule.INSTANCE, () -> BlockEffectsModule.INSTANCE.onBreak(pos, state));
    }

    @Unique private boolean anthrax$heldBlock;

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void anthrax$beforeUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        anthrax$heldBlock = player.getItemInHand(hand).getItem() instanceof BlockItem;
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void anthrax$placed(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
        if (!PlaceAnimationModule.INSTANCE.isEnabled() && !BlockEffectsModule.INSTANCE.isEnabled()) return;
        if (!anthrax$heldBlock) return;
        Minecraft mc = Minecraft.getInstance();
        // the client places its prediction straight away: find which of the two spots changed to a placed block
        BlockPos side = hit.getBlockPos().relative(hit.getDirection());
        BlockPos at = !mc.level.getBlockState(side).isAir() && !mc.level.getBlockState(side).canBeReplaced() ? side : hit.getBlockPos();
        BlockState state = mc.level.getBlockState(at);
        if (state.isAir()) return;
        Safe.run(PlaceAnimationModule.INSTANCE, () -> PlaceAnimationModule.INSTANCE.onPlace(at, state));
        Safe.run(BlockEffectsModule.INSTANCE, () -> BlockEffectsModule.INSTANCE.onPlace(at, state));
    }
}
