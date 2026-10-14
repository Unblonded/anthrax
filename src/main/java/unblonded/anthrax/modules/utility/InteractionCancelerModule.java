package unblonded.anthrax.modules.utility;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.function.Predicate;

/**
 * Makes the crosshair pass straight through decorative or non-target entities, so you hit what is
 * behind them (see EntityPickMixin). The entities still render and collide normally.
 */
public final class InteractionCancelerModule extends FeatureModule {
    public static final InteractionCancelerModule INSTANCE = new InteractionCancelerModule();

    private final Setting.Bool villagers = setting(Setting.Bool.standalone("Villagers and traders", true));
    private final Setting.Bool frames = setting(Setting.Bool.standalone("Item frames", true));
    private final Setting.Bool stands = setting(Setting.Bool.standalone("Armor stands", true));
    private final Setting.Bool minecarts = setting(Setting.Bool.standalone("Minecarts", true));

    private final Predicate<Entity> filter = entity -> EntitySelector.CAN_BE_PICKED.test(entity) && !isGhost(entity) && !unblonded.anthrax.modules.cosmetic.PetsModule.isPet(entity);

    private InteractionCancelerModule() {
        super("Interaction Canceler", "Aim and click through villagers, frames, stands and carts", "Utility", false);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                level.isClientSide() && isGhost(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    /** Picking predicate used by the local player's crosshair raycast. */
    /** The client-only pet can never be clicked. */
    private static final Predicate<Entity> NOT_PET = e -> EntitySelector.CAN_BE_PICKED.test(e) && !unblonded.anthrax.modules.cosmetic.PetsModule.isPet(e);

    public static Predicate<Entity> pickFilter() {
        return INSTANCE.isEnabled() ? INSTANCE.filter : NOT_PET;
    }

    private boolean isGhost(Entity entity) {
        if (!isEnabled()) return false;
        return (villagers.get() && entity instanceof AbstractVillager)
                || (frames.get() && entity instanceof ItemFrame)
                || (stands.get() && entity instanceof ArmorStand)
                || (minecarts.get() && entity instanceof AbstractMinecart);
    }
}
