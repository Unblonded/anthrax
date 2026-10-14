package unblonded.anthrax.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.util.HashSet;
import java.util.Set;

/** Boxes around dropped items through walls, coloured by rarity, with a per-item filter and name labels. */
public final class ItemEspModule extends FeatureModule implements WorldOverlays.Overlay {
    public static final ItemEspModule INSTANCE = new ItemEspModule();
    private static final String DEFAULT_LIST = "diamond, netherite_ingot, netherite_scrap, ancient_debris, totem_of_undying, enchanted_golden_apple, "
            + "elytra, shulker_box, ender_pearl, end_crystal, nether_star, emerald, diamond_block, netherite_block";
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 48, 8, 128, 8), "Only items this close.");
    private final Setting.Mode filter = setting(Setting.Mode.standalone("Filter", "Off", "Off", "Only listed", "Hide listed"),
            "Only listed shows just the items in the list; Hide listed shows everything except them.");
    private final Setting.Text list = setting(Setting.Text.standalone("Item list", DEFAULT_LIST, 1024),
            "Item ids separated by commas, e.g. diamond, elytra. Any colour of shulker box matches shulker_box.");
    private final Setting.Bool highlightListed = setting(Setting.Bool.standalone("Highlight listed", true), "Listed items get a bright green box and a beam, whatever the filter.");
    private final Setting.Bool valuableOnly = setting(Setting.Bool.standalone("Only rare items", false), "Hides common items (listed items always show).");
    private final Setting.Bool beams = setting(Setting.Bool.standalone("Beams", false), "A vertical line above each item so they are easy to spot.");
    private final Setting.Bool labels = setting(Setting.Bool.standalone("Names", true), "Item name and stack size above each item.");
    private final Setting.Num labelRange = setting(Setting.Num.standalone("Name range", 24, 4, 128, 4), "Only label items this close, to keep the screen clear.");

    private volatile Set<Item> listed = Set.of();
    private boolean anyShulker;
    private String parsedFrom = "";

    private ItemEspModule() { super("Item ESP", "Highlights dropped items through walls, with a per-item filter, names and optional beams", "Visuals", false); }

    @Override public void tick(Minecraft mc) {
        String raw = list.get();
        if (raw.equals(parsedFrom)) return;
        parsedFrom = raw;
        Set<Item> set = new HashSet<>();
        boolean shulker = false;
        for (String s : raw.split(",")) {
            String id = s.trim().toLowerCase(java.util.Locale.ROOT);
            if (id.isEmpty()) continue;
            if (id.equals("shulker_box") || id.equals("minecraft:shulker_box")) shulker = true;
            Identifier ident = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
            if (ident != null) BuiltInRegistries.ITEM.getOptional(ident).ifPresent(set::add);
        }
        listed = Set.copyOf(set);
        anyShulker = shulker;
    }

    private boolean isListed(Item item) {
        if (listed.contains(item)) return true;
        return anyShulker && item instanceof net.minecraft.world.item.BlockItem b && b.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock;
    }

    @Override public boolean active() { return isEnabled(); }

    @Override public void draw(Minecraft mc, float partial, Vec3 eye, Vec3 look) {
        double r2 = range.get() * range.get(), l2 = labelRange.get() * labelRange.get();
        String mode = filter.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity item) || mc.player.distanceToSqr(e) > r2) continue;
            var stack = item.getItem();
            boolean onList = isListed(stack.getItem());
            if (mode.equals("Only listed") && !onList) continue;
            if (mode.equals("Hide listed") && onList) continue;
            var rarity = stack.getRarity();
            if (valuableOnly.get() && rarity == net.minecraft.world.item.Rarity.COMMON && !onList) continue;
            boolean highlight = onList && highlightListed.get() && !mode.equals("Hide listed");
            int color = highlight ? 0x4DFF7A : switch (rarity) {
                case UNCOMMON -> 0xFFE45C; case RARE -> 0x55FFFF; case EPIC -> 0xFF55FF; default -> 0xFFFFFF;
            };
            Vec3 pos = e.getPosition(partial);
            var box = e.getBoundingBox().move(pos.subtract(e.position())).inflate(highlight ? 0.1 : 0.05);
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(WorldOverlays.argb(0xE0, color), highlight ? 2.5f : 1.5f, WorldOverlays.argb(0x30, color))).setAlwaysOnTop();
            if (beams.get() || highlight) Gizmos.line(pos, pos.add(0, highlight ? 6 : 3, 0), WorldOverlays.argb(0xA0, color), highlight ? 2.5f : 1.5f).setAlwaysOnTop();
            if (labels.get() && mc.player.distanceToSqr(e) <= l2) {
                String text = stack.getHoverName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
                Gizmos.billboardText(text, pos.add(0, box.getYsize() + 0.35, 0), TextGizmo.Style.forColorAndCentered(WorldOverlays.argb(0xFF, color)).withScale(0.4f)).setAlwaysOnTop();
            }
        }
    }
}
