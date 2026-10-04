package unblonded.anthrax.modules.visual;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayList;
import java.util.List;

/** Displays nearby players with the status details shown by Packet Edit's player list. */
public final class PlayerListModule extends FeatureModule {
    private final Setting.Bool freeze = setting(Setting.Bool.standalone("Freeze list", false));
    private final List<String> frozen = new ArrayList<>();
    private List<String> current = List.of();
    private boolean wasFrozen;

    public PlayerListModule() {
        super("Show Player List", "Show nearby players, distance, health, armor, and held items", "Visuals", false);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("anthrax", "nearby_player_list"), (graphics, delta) -> {
            if (!isEnabled() || Minecraft.getInstance().player == null) return;
            List<String> entries = freeze.get() ? frozen : current;
            if (entries.isEmpty()) return;
            int width = 0;
            for (String line : entries) width = Math.max(width, Minecraft.getInstance().font.width(line));
            int x = 8, y = 30, lineH = 11;
            graphics.fill(x - 4, y - 4, x + width + 5, y + entries.size() * lineH + 4, 0xB0101020);
            graphics.textRenderer().accept(x, y, Component.literal("Nearby Players"));
            y += lineH;
            for (String line : entries) { graphics.textRenderer().accept(x, y, Component.literal(line)); y += lineH; }
        });
    }

    @Override public void tick(Minecraft client) {
        if (client.player == null || client.level == null) { current = List.of(); frozen.clear(); wasFrozen = false; return; }
        List<String> entries = new ArrayList<>();
        for (Player other : client.level.players()) {
            if (other == client.player) continue;
            String main = itemName(other.getMainHandItem());
            String off = itemName(other.getOffhandItem());
            String text = String.format("%s %.1fm | %.1f HP | armor %d | %s / %s%s%s",
                    other.getName().getString(), Math.sqrt(client.player.distanceToSqr(other)), other.getHealth(), other.getArmorValue(),
                    main, off, other.isSprinting() ? " | sprinting" : "", other.isCrouching() ? " | sneaking" : "");
            entries.add(text);
        }
        current = List.copyOf(entries);
        boolean freezeNow = freeze.get();
        if (freezeNow && !wasFrozen) {
            frozen.clear();
            frozen.addAll(current);
        } else if (!freezeNow) {
            frozen.clear();
        }
        wasFrozen = freezeNow;
    }

    private static String itemName(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }
}
