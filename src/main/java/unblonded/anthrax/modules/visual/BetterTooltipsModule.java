package unblonded.anthrax.modules.visual;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Item tooltip extras: a grid preview of shulker box contents, your ender chest's contents (remembered from the
 * last time you opened it), filled map pictures and food values. The previews go through ItemStackTooltipMixin.
 */
public final class BetterTooltipsModule extends FeatureModule {
    public static final BetterTooltipsModule INSTANCE = new BetterTooltipsModule();
    private final Setting.Bool shulkers = setting(Setting.Bool.standalone("Shulker box preview", true), "Shows the items inside a shulker box as a grid.");
    private final Setting.Bool ender = setting(Setting.Bool.standalone("Ender chest preview", true), "Shows your ender chest's contents on ender chest items (open it once to load them).");
    private final Setting.Bool food = setting(Setting.Bool.standalone("Food values", true), "Hunger and saturation restored.");
    private final Setting.Bool maps = setting(Setting.Bool.standalone("Map preview", true), "Shows a filled map's picture in its tooltip.");
    private final Setting.Num mapSize = setting(Setting.Num.standalone("Map preview size", 96, 48, 160, 8), "Width of the map picture, in GUI pixels.");
    private final Setting.Bool tint = setting(Setting.Bool.standalone("Tint by box colour", true), "Colours the preview background like the shulker box.");
    private List<ItemStack> enderContents;

    /** Grid of items to show under a tooltip. */
    public record ContainerPreview(List<ItemStack> items, int color) implements TooltipComponent {}

    /** A filled map to draw under a tooltip. */
    public record MapPreview(net.minecraft.world.level.saveddata.maps.MapId id, int size) implements TooltipComponent {}

    private BetterTooltipsModule() { super("Better Tooltips", "Adds previews to item tooltips: shulker box contents, your ender chest, filled maps and food values", "Visuals", false); }

    /** Fabric hooks, registered once at start-up. */
    public static void register() {
        ClientTooltipComponentCallback.EVENT.register(data -> data instanceof ContainerPreview p ? new Grid(p) : data instanceof MapPreview m ? new MapPicture(m) : null);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!INSTANCE.isEnabled() || !INSTANCE.food.get()) return;
            var f = stack.get(DataComponents.FOOD);
            if (f == null) return;
            lines.add(Component.literal("Food " + f.nutrition() + "   Saturation " + String.format("%.1f", f.saturation())).withStyle(ChatFormatting.GOLD));
        });
    }

    @Override public void tick(Minecraft mc) {
        if (mc.player == null || !(mc.player.containerMenu instanceof ChestMenu menu) || mc.gui.screen() == null) return;
        if (!mc.gui.screen().getTitle().getString().equals(Component.translatable("container.enderchest").getString())) return;
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 27 && i < menu.slots.size(); i++) items.add(menu.slots.get(i).getItem().copy());
        enderContents = items;
    }

    /** The preview for this stack, if any (called from the tooltip mixin). */
    public Optional<TooltipComponent> preview(ItemStack stack) {
        if (!isEnabled()) return Optional.empty();
        if (shulkers.get() && stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock box) {
            var contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) return Optional.empty();
            List<ItemStack> items = new ArrayList<>(contents.allItemsCopyStream().toList());
            if (items.stream().allMatch(ItemStack::isEmpty)) return Optional.empty();
            while (items.size() < 27) items.add(ItemStack.EMPTY);
            int color = 0x30FFFFFF;
            if (tint.get() && box.getColor() != null) color = 0x50000000 | (box.getColor().getTextureDiffuseColor() & 0xFFFFFF);
            return Optional.of(new ContainerPreview(items, color));
        }
        if (maps.get() && stack.is(Items.FILLED_MAP)) {
            var id = stack.get(DataComponents.MAP_ID);
            if (id != null) return Optional.of(new MapPreview(id, (int) mapSize.get()));
        }
        if (ender.get() && stack.is(Items.ENDER_CHEST) && enderContents != null)
            return Optional.of(new ContainerPreview(enderContents, 0x50153030));
        return Optional.empty();
    }

    /** Draws the map picture with its markers, like a cartography table. */
    private static final class MapPicture implements ClientTooltipComponent {
        private final MapPreview data;
        private final net.minecraft.client.renderer.state.MapRenderState state = new net.minecraft.client.renderer.state.MapRenderState();
        MapPicture(MapPreview data) { this.data = data; }
        @Override public int getHeight(Font font) { return data.size() + 6; }
        @Override public int getWidth(Font font) { return data.size() + 2; }
        @Override public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
            Minecraft mc = Minecraft.getInstance();
            var saved = mc.level == null ? null : mc.level.getMapData(data.id());
            g.fill(x, y, x + data.size() + 2, y + data.size() + 2, 0x60000000);
            if (saved == null) {
                g.text(font, "Map not loaded yet", x + 4, y + data.size() / 2 - 8, 0xFFAAAAAA);
                g.text(font, "(hold it once)", x + 4, y + data.size() / 2 + 2, 0xFF888888);
                return;
            }
            mc.getMapRenderer().extractRenderState(data.id(), saved, state);
            g.pose().pushMatrix();
            g.pose().translate(x + 1, y + 1);
            g.pose().scale(data.size() / 128f, data.size() / 128f);
            g.map(state);
            g.pose().popMatrix();
        }
    }

    /** Draws the 9-wide grid with item counts. */
    private static final class Grid implements ClientTooltipComponent {
        private final ContainerPreview data;
        Grid(ContainerPreview data) { this.data = data; }
        private int rows() { return Math.max(1, (data.items().size() + 8) / 9); }
        @Override public int getHeight(Font font) { return rows() * 18 + 6; }
        @Override public int getWidth(Font font) { return 9 * 18 + 2; }
        @Override public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
            g.fill(x, y, x + 9 * 18 + 2, y + rows() * 18 + 2, data.color());
            List<ItemStack> items = data.items();
            for (int i = 0; i < items.size(); i++) {
                int sx = x + 1 + (i % 9) * 18, sy = y + 1 + (i / 9) * 18;
                g.fill(sx, sy, sx + 17, sy + 17, 0x40000000);
                ItemStack s = items.get(i);
                if (s.isEmpty()) continue;
                g.item(s, sx, sy);
                g.itemDecorations(font, s, sx, sy);
            }
        }
    }
}
