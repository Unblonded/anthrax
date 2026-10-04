package unblonded.anthrax.modules.utility;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.AABB;
import java.util.IdentityHashMap;
import java.util.Map;
import unblonded.anthrax.modules.FeatureModule;

/** Prevents accidental interaction with commonly decorative or non-target entities. */
public final class InteractionCancelerModule extends FeatureModule {
    private final Map<Entity, AABB> suppressed = new IdentityHashMap<>();
    private net.minecraft.client.multiplayer.ClientLevel lastLevel;
    public InteractionCancelerModule() {
        super("Interaction Canceler", "Ignore interactions with configured entities", "Utility", false);
        ClientTickEvents.END_CLIENT_TICK.register(this::suppressGhostEntityHitboxes);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!isEnabled() || !level.isClientSide()) return InteractionResult.PASS;
            if (isGhost(entity)) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
    }

    private void suppressGhostEntityHitboxes(Minecraft client) {
        if (client.level != lastLevel) {
            restoreSuppressed();
            lastLevel = client.level;
        }
        if (client.level == null || !isEnabled()) {
            restoreSuppressed();
            return;
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (isGhost(entity) && !suppressed.containsKey(entity)) {
                suppressed.put(entity, entity.getBoundingBox());
                entity.setBoundingBox(new AABB(0, 0, 0, 0, 0, 0));
            }
        }
    }

    private void restoreSuppressed() {
        suppressed.keySet().forEach(entity -> {
            if (!entity.isRemoved()) entity.refreshDimensions();
        });
        suppressed.clear();
    }

    private boolean isGhost(Entity entity) {
        EntityType<?> type = entity.getType();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        return id.equals("villager") || id.equals("wandering_trader")
                || id.equals("item_frame") || id.equals("glow_item_frame") || id.equals("armor_stand")
                || id.endsWith("minecart");
    }
}
