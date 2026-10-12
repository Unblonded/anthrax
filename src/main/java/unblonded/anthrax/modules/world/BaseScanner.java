package unblonded.anthrax.modules.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one loaded chunk into evidence of player activity: weighted counts of blocks that rarely or
 * never generate naturally, plus "structure markers" (bells, trial spawners, purpur...) that explain
 * away blocks a natural structure would also contain. Block states are counted straight from each
 * section's palette, so a chunk costs a few hundred microseconds. Client thread only.
 */
final class BaseScanner {

    /** What kind of activity a block is evidence of; also drives colors and base classification. */
    enum Category {
        STORAGE("Storage", 0xFFFFB347), VALUABLE("Valuables", 0xFF42F5E6), REDSTONE("Redstone", 0xFFFF4D4D),
        PORTAL("Portals", 0xFFB76BFF), BUILD("Builds", 0xFF7CC8FF), FARM("Farms", 0xFF7CFF7C),
        ACTIVITY("Activity", 0xFFFFE08A);
        final String label;
        final int color;
        Category(String label, int color) { this.label = label; this.color = color; }
    }

    /** Natural structures recognised from signature blocks. */
    enum Structure { VILLAGE, MINESHAFT, STRONGHOLD, DUNGEON, ANCIENT_CITY, TRIAL_CHAMBER, BASTION, FORTRESS,
        END_CITY, MONUMENT, TEMPLE, RUINED_PORTAL, TRAIL_RUINS, IGLOO }

    enum Dim { OVERWORLD, NETHER, END }

    /** One kind of evidence. Weight is per block, cap limits how much a single kind can contribute per chunk. */
    static final class Feature {
        final int id;
        final String name, plural;
        final Category category;
        final float weight;
        final int cap;
        final boolean track;
        final EnumSet<Structure> explainedBy;
        final EnumSet<Dim> natural;

        Feature(int id, String name, String plural, Category category, float weight, int cap, boolean track,
                EnumSet<Structure> explainedBy, EnumSet<Dim> natural) {
            this.id = id; this.name = name; this.plural = plural; this.category = category; this.weight = weight;
            this.cap = cap; this.track = track; this.explainedBy = explainedBy; this.natural = natural;
        }
    }

    /**
     * Facts read from block states and block entities. Unlike features these are not scored for
     * "is this a base", they describe a base once found: storage inventory, live machines, ripeness,
     * oxidation, raid evidence and so on.
     */
    enum Signal { CHEST_SINGLE, CHEST_HALF, TRAPPED, BARREL, SHULKER, ENDER_CHEST, HOPPER, DISPENSER, CRAFTER,
        FURNACE, LIT_FURNACE, POWERED, JUKEBOX_DISC, LECTERN_BOOK, BREWING_BOTTLE, ANCHOR_CHARGED, COMPOSTER_FILLED,
        CROPS, CROPS_MATURE, BED_PART, TNT, FIRE, TRAP, IRON_DOOR_PART, OBSIDIAN, CANDLE_LIT,
        COPPER_FRESH, COPPER_EXPOSED, COPPER_WEATHERED, COPPER_OXIDIZED, COPPER_WAXED,
        PORTAL_FRAME, PORTAL_FRAME_EYE, END_PORTAL, NETHER_PORTAL, SPAWNER, VALUABLE, BEACON, BUDDING_AMETHYST,
        /** Raid damage: flowing lava high up (lava casts) and ground columns blown out into bowls. */
        LAVA_FLOW, CRATER }

    private enum SigKind { CHEST, TRAPPED, BARREL, SHULKER, ENDER, HOPPER, DISPENSER, CRAFTER, FURNACE, LAMP, PISTON,
        POWERABLE, JUKEBOX, LECTERN, BREWING, ANCHOR, COMPOSTER, CROP, BED, TNT, FIRE, TRAP, IRON_DOOR, OBSIDIAN, CANDLE,
        COPPER_0, COPPER_1, COPPER_2, COPPER_3, PORTAL_FRAME, END_PORTAL, NETHER_PORTAL, SPAWNER, VALUABLE, BEACON, BUDDING }

    private static final Map<Block, SigKind> SIGNALS = new IdentityHashMap<>();
    private static final Set<Block> WAXED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** Amethyst bud blocks by growth stage (1 small .. 4 cluster). */
    private static final Map<Block, Integer> BUD_STAGE = new IdentityHashMap<>();

    static final List<Feature> FEATURES = new ArrayList<>();
    private static final Map<Block, Feature> BY_BLOCK = new IdentityHashMap<>();
    private static final Map<Block, Marker> MARKERS = new IdentityHashMap<>();
    private static boolean built;

    // entity-sourced features (filled by the entity pass, not by block palettes)
    static Feature ITEM_FRAME, ARMOR_STAND, PAINTING, END_CRYSTAL, VILLAGER_HALL, ANIMAL_PEN, AMETHYST_GROWTH, MINED_ORE;

    private enum Marker { BELL, COBWEB, RAIL, END_PORTAL_FRAME, INFESTED_BRICKS, SPAWNER, MOSSY_COBBLE, REINFORCED_DEEPSLATE,
        DEEP_BRICKS, TRIAL_SPAWNER, VAULT, GILDED_BLACKSTONE, NETHER_BRICK_FENCE, NETHER_BRICKS, PURPUR, PRISMARINE,
        TNT, STICKY_PISTON, CRYING_OBSIDIAN, SUSPICIOUS, SNOW_BLOCK, CHISELED_SANDSTONE }

    private static EnumSet<Structure> by(Structure... s) {
        EnumSet<Structure> set = EnumSet.noneOf(Structure.class);
        for (Structure x : s) set.add(x);
        return set;
    }
    private static EnumSet<Dim> naturalIn(Dim... d) {
        EnumSet<Dim> set = EnumSet.noneOf(Dim.class);
        for (Dim x : d) set.add(x);
        return set;
    }

    private static Feature feature(String name, String plural, Category c, float w, int cap, boolean track,
                                   EnumSet<Structure> explained, EnumSet<Dim> natural) {
        Feature f = new Feature(FEATURES.size(), name, plural, c, w, cap, track, explained, natural);
        FEATURES.add(f);
        return f;
    }

    /** Built lazily (registry must be complete); classification is by registry id so it never depends on tags. */
    static synchronized void build() {
        if (built) return;
        built = true;
        EnumSet<Structure> none = EnumSet.noneOf(Structure.class);
        EnumSet<Dim> never = EnumSet.noneOf(Dim.class);
        Structure V = Structure.VILLAGE;

        Feature shulker = feature("shulker box", "shulker boxes", Category.STORAGE, 40, 54, true, none, never);
        Feature ender = feature("ender chest", "ender chests", Category.STORAGE, 25, 8, true, by(Structure.END_CITY), never);
        Feature chest = feature("chest", "chests", Category.STORAGE, 4, 60, true, by(V, Structure.MINESHAFT, Structure.STRONGHOLD,
                Structure.DUNGEON, Structure.ANCIENT_CITY, Structure.TRIAL_CHAMBER, Structure.BASTION, Structure.FORTRESS,
                Structure.END_CITY, Structure.TEMPLE, Structure.RUINED_PORTAL, Structure.IGLOO, Structure.TRAIL_RUINS), never);
        Feature trapped = feature("trapped chest", "trapped chests", Category.STORAGE, 10, 30, true, none, never);
        Feature barrel = feature("barrel", "barrels", Category.STORAGE, 5, 40, true, by(V, Structure.TRAIL_RUINS), never);
        Feature hopper = feature("hopper", "hoppers", Category.STORAGE, 8, 60, true, none, never);
        Feature dispenser = feature("dispenser", "dispensers", Category.REDSTONE, 5, 40, false, by(Structure.TEMPLE, Structure.TRIAL_CHAMBER), never);
        Feature crafter = feature("crafter", "crafters", Category.REDSTONE, 12, 20, true, by(Structure.TRIAL_CHAMBER), never);
        Feature furnace = feature("furnace", "furnaces", Category.BUILD, 3, 30, false, by(V, Structure.IGLOO), never);
        Feature beacon = feature("beacon", "beacons", Category.VALUABLE, 60, 5, true, none, never);
        Feature netherite = feature("netherite block", "netherite blocks", Category.VALUABLE, 60, 10, true, none, never);
        Feature diamond = feature("diamond block", "diamond blocks", Category.VALUABLE, 35, 20, true, none, never);
        Feature emerald = feature("emerald block", "emerald blocks", Category.VALUABLE, 20, 20, true, none, never);
        Feature gold = feature("gold block", "gold blocks", Category.VALUABLE, 8, 20, true, by(Structure.BASTION, Structure.RUINED_PORTAL, Structure.MONUMENT), never);
        Feature iron = feature("iron block", "iron blocks", Category.VALUABLE, 4, 30, false, none, never);
        Feature conduit = feature("conduit", "conduits", Category.VALUABLE, 40, 4, true, none, never);
        Feature enchanting = feature("enchanting table", "enchanting tables", Category.VALUABLE, 20, 4, true, none, never);
        Feature anvil = feature("anvil", "anvils", Category.VALUABLE, 12, 6, true, by(V), never);
        Feature brewing = feature("brewing stand", "brewing stands", Category.VALUABLE, 10, 8, true, by(V, Structure.IGLOO, Structure.END_CITY), never);
        Feature anchor = feature("respawn anchor", "respawn anchors", Category.VALUABLE, 20, 6, true, none, never);
        Feature lodestone = feature("lodestone", "lodestones", Category.VALUABLE, 25, 6, true, none, never);
        Feature wire = feature("redstone dust", "redstone dust", Category.REDSTONE, 1.2f, 400, false, by(Structure.TEMPLE, Structure.ANCIENT_CITY), never);
        Feature repeater = feature("repeater", "repeaters", Category.REDSTONE, 4, 60, false, by(Structure.TEMPLE), never);
        Feature comparator = feature("comparator", "comparators", Category.REDSTONE, 6, 40, false, none, never);
        Feature piston = feature("piston", "pistons", Category.REDSTONE, 4, 60, false, by(Structure.TEMPLE), never);
        Feature observer = feature("observer", "observers", Category.REDSTONE, 8, 60, false, none, never);
        Feature rtorch = feature("redstone torch", "redstone torches", Category.REDSTONE, 2, 40, false, by(Structure.ANCIENT_CITY), never);
        Feature lamp = feature("redstone lamp", "redstone lamps", Category.REDSTONE, 4, 40, false, none, never);
        Feature misc = feature("redstone part", "redstone parts", Category.REDSTONE, 4, 40, false, none, never);
        Feature lever = feature("lever", "levers", Category.REDSTONE, 1.5f, 30, false, by(Structure.TEMPLE, Structure.STRONGHOLD), never);
        Feature slime = feature("slime/honey block", "slime/honey blocks", Category.REDSTONE, 2, 80, false, none, never);
        Feature portal = feature("nether portal", "nether portal blocks", Category.PORTAL, 2, 42, true, none, never);
        Feature obsidian = feature("obsidian", "obsidian", Category.PORTAL, 0.25f, 160, false, by(Structure.RUINED_PORTAL), naturalIn(Dim.END));
        Feature concrete = feature("concrete", "concrete", Category.BUILD, 0.8f, 600, false, none, never);
        Feature glazed = feature("glazed terracotta", "glazed terracotta", Category.BUILD, 0.6f, 200, false, by(Structure.TRAIL_RUINS), never);
        Feature glass = feature("glass", "glass", Category.BUILD, 0.35f, 400, false, by(V), never);
        Feature pane = feature("glass pane", "glass panes", Category.BUILD, 0.25f, 300, false, by(V), never);
        Feature wool = feature("wool", "wool", Category.BUILD, 0.25f, 400, false, by(V, Structure.ANCIENT_CITY), never);
        Feature carpet = feature("carpet", "carpets", Category.BUILD, 0.4f, 200, false, by(V, Structure.IGLOO, Structure.ANCIENT_CITY), never);
        Feature bed = feature("bed", "beds", Category.BUILD, 3, 16, false, by(V, Structure.IGLOO), never);
        Feature sign = feature("sign", "signs", Category.BUILD, 3, 40, true, none, never);
        Feature banner = feature("banner", "banners", Category.BUILD, 5, 20, false, none, never);
        Feature pot = feature("flower pot", "flower pots", Category.BUILD, 1.5f, 30, false, by(V), never);
        Feature lantern = feature("lantern", "lanterns", Category.BUILD, 0.8f, 60, false, by(V), never);
        Feature seaLantern = feature("sea lantern", "sea lanterns", Category.BUILD, 1.5f, 60, false, by(Structure.MONUMENT, Structure.TRAIL_RUINS), never);
        Feature endRod = feature("end rod", "end rods", Category.BUILD, 1.5f, 60, false, by(Structure.END_CITY), never);
        Feature quartz = feature("quartz block", "quartz blocks", Category.BUILD, 0.4f, 300, false, none, never);
        Feature copper = feature("copper block", "copper blocks", Category.BUILD, 0.4f, 300, false, by(Structure.TRIAL_CHAMBER), never);
        Feature glowstone = feature("glowstone", "glowstone", Category.BUILD, 0.8f, 100, false, none, naturalIn(Dim.NETHER));
        Feature shroomlight = feature("shroomlight", "shroomlights", Category.BUILD, 0.8f, 100, false, none, naturalIn(Dim.NETHER));
        Feature torch = feature("torch", "torches", Category.ACTIVITY, 0.6f, 150, false, by(V, Structure.STRONGHOLD), never);
        Feature table = feature("crafting table", "crafting tables", Category.ACTIVITY, 3, 6, false, by(V), never);
        Feature scaffold = feature("scaffolding", "scaffolding", Category.ACTIVITY, 1, 60, false, none, never);
        Feature ladder = feature("ladder", "ladders", Category.ACTIVITY, 0.3f, 60, false, by(Structure.STRONGHOLD, Structure.IGLOO), never);
        Feature bookshelf = feature("bookshelf", "bookshelves", Category.ACTIVITY, 0.3f, 100, false, by(Structure.STRONGHOLD, V), never);
        Feature door = feature("door", "doors", Category.ACTIVITY, 0.8f, 30, false, by(V, Structure.STRONGHOLD, Structure.TRIAL_CHAMBER), never);
        Feature rail = feature("rail", "rails", Category.ACTIVITY, 0.4f, 300, false, by(Structure.MINESHAFT), never);
        Feature powered = feature("powered rail", "powered rails", Category.ACTIVITY, 2, 100, false, none, never);
        Feature crops = feature("crop", "crops", Category.FARM, 0.3f, 400, false, by(V), never);
        Feature farmland = feature("farmland", "farmland", Category.FARM, 0.2f, 400, false, by(V), never);
        Feature stems = feature("melon/pumpkin stem", "melon/pumpkin stems", Category.FARM, 0.8f, 100, false, by(V), never);
        Feature wart = feature("nether wart", "nether wart", Category.FARM, 1, 100, false, by(Structure.FORTRESS), never);
        Feature composter = feature("composter", "composters", Category.FARM, 1, 10, false, by(V), never);
        Feature hay = feature("hay bale", "hay bales", Category.FARM, 0.2f, 100, false, by(V), never);

        ITEM_FRAME = feature("item frame", "item frames", Category.BUILD, 5, 30, false, by(Structure.END_CITY), never);
        ARMOR_STAND = feature("armor stand", "armor stands", Category.BUILD, 4, 20, false, none, never);
        PAINTING = feature("painting", "paintings", Category.BUILD, 3, 20, false, none, never);
        END_CRYSTAL = feature("end crystal", "end crystals", Category.VALUABLE, 10, 10, false, none, naturalIn(Dim.END));
        VILLAGER_HALL = feature("penned villager", "penned villagers", Category.FARM, 3, 40, false, by(V), never);
        ANIMAL_PEN = feature("penned animal", "penned animals", Category.FARM, 0.6f, 80, false, none, never);
        // Scored directly from the amethyst clock, not from block counts (see BaseFinderModule.cluster).
        AMETHYST_GROWTH = feature("player-grown geode", "player-grown geodes", Category.ACTIVITY, 0, 0, false, none, never);
        // Scored from the seed: ores that generated here and are now air (see MinedOreAnalysis).
        MINED_ORE = feature("mined-out ore", "mined-out ores", Category.ACTIVITY, 0, 0, false, none, never);

        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
            Feature f = switch (id) {
                case "ender_chest" -> ender;
                case "chest" -> chest;
                case "trapped_chest" -> trapped;
                case "barrel" -> barrel;
                case "hopper" -> hopper;
                case "dispenser", "dropper" -> dispenser;
                case "crafter" -> crafter;
                case "furnace", "blast_furnace", "smoker" -> furnace;
                case "beacon" -> beacon;
                case "netherite_block" -> netherite;
                case "diamond_block" -> diamond;
                case "emerald_block" -> emerald;
                case "gold_block" -> gold;
                case "iron_block" -> iron;
                case "conduit" -> conduit;
                case "enchanting_table" -> enchanting;
                case "anvil", "chipped_anvil", "damaged_anvil" -> anvil;
                case "brewing_stand" -> brewing;
                case "respawn_anchor" -> anchor;
                case "lodestone" -> lodestone;
                case "redstone_wire" -> wire;
                case "repeater" -> repeater;
                case "comparator" -> comparator;
                case "piston", "sticky_piston", "piston_head" -> piston;
                case "observer" -> observer;
                case "redstone_torch", "redstone_wall_torch" -> rtorch;
                case "redstone_lamp" -> lamp;
                case "target", "daylight_detector", "note_block", "calibrated_sculk_sensor" -> misc;
                case "lever" -> lever;
                case "slime_block", "honey_block" -> slime;
                case "nether_portal" -> portal;
                case "obsidian" -> obsidian;
                case "glass", "tinted_glass" -> glass;
                case "glass_pane" -> pane;
                case "flower_pot" -> pot;
                case "lantern", "soul_lantern" -> lantern;
                case "sea_lantern" -> seaLantern;
                case "end_rod" -> endRod;
                case "quartz_block", "quartz_bricks", "quartz_pillar", "smooth_quartz", "chiseled_quartz_block" -> quartz;
                case "glowstone" -> glowstone;
                case "shroomlight" -> shroomlight;
                case "torch", "wall_torch", "soul_torch", "soul_wall_torch", "copper_torch", "copper_wall_torch" -> torch;
                case "crafting_table" -> table;
                case "scaffolding" -> scaffold;
                case "ladder" -> ladder;
                case "bookshelf", "chiseled_bookshelf" -> bookshelf;
                case "rail" -> rail;
                case "powered_rail", "detector_rail", "activator_rail" -> powered;
                case "wheat", "carrots", "potatoes", "beetroots", "torchflower_crop", "pitcher_crop" -> crops;
                case "farmland" -> farmland;
                case "melon_stem", "pumpkin_stem", "attached_melon_stem", "attached_pumpkin_stem" -> stems;
                case "nether_wart" -> wart;
                case "composter" -> composter;
                case "hay_block" -> hay;
                default -> null;
            };
            if (f == null) {
                if (id.endsWith("shulker_box")) f = shulker;
                else if (id.endsWith("_concrete")) f = concrete;
                else if (id.endsWith("_glazed_terracotta")) f = glazed;
                else if (id.endsWith("stained_glass")) f = glass;
                else if (id.endsWith("stained_glass_pane")) f = pane;
                else if (id.endsWith("_wool")) f = wool;
                else if (id.endsWith("_carpet") && !id.equals("moss_carpet") && !id.equals("pale_moss_carpet")) f = carpet;
                else if (id.endsWith("_bed")) f = bed;
                else if (id.endsWith("_sign")) f = sign;
                else if (id.endsWith("_banner")) f = banner;
                else if (id.startsWith("potted_")) f = pot;
                else if (id.endsWith("_door")) f = door;
                else if (id.contains("copper") && !id.contains("ore") && !id.equals("raw_copper_block")
                        && !id.contains("torch") && !id.contains("lantern")) f = copper;
            }
            if (f != null) BY_BLOCK.put(block, f);

            SigKind k = switch (id) {
                case "chest" -> SigKind.CHEST;
                case "trapped_chest" -> SigKind.TRAPPED;
                case "barrel" -> SigKind.BARREL;
                case "ender_chest" -> SigKind.ENDER;
                case "hopper" -> SigKind.HOPPER;
                case "dispenser", "dropper" -> SigKind.DISPENSER;
                case "crafter" -> SigKind.CRAFTER;
                case "furnace", "blast_furnace", "smoker" -> SigKind.FURNACE;
                case "redstone_lamp" -> SigKind.LAMP;
                case "piston", "sticky_piston" -> SigKind.PISTON;
                case "repeater", "comparator", "observer" -> SigKind.POWERABLE;
                case "jukebox" -> SigKind.JUKEBOX;
                case "lectern" -> SigKind.LECTERN;
                case "brewing_stand" -> SigKind.BREWING;
                case "respawn_anchor" -> SigKind.ANCHOR;
                case "composter" -> SigKind.COMPOSTER;
                case "tnt" -> SigKind.TNT;
                case "fire", "soul_fire" -> SigKind.FIRE;
                case "tripwire_hook", "stone_pressure_plate", "light_weighted_pressure_plate", "heavy_weighted_pressure_plate" -> SigKind.TRAP;
                case "iron_door" -> SigKind.IRON_DOOR;
                case "obsidian" -> SigKind.OBSIDIAN;
                case "end_portal_frame" -> SigKind.PORTAL_FRAME;
                case "end_portal" -> SigKind.END_PORTAL;
                case "nether_portal" -> SigKind.NETHER_PORTAL;
                case "spawner" -> SigKind.SPAWNER;
                case "beacon" -> SigKind.BEACON;
                case "diamond_block", "netherite_block", "emerald_block" -> SigKind.VALUABLE;
                case "budding_amethyst" -> SigKind.BUDDING;
                default -> null;
            };
            if (k == null) {
                if (id.endsWith("shulker_box")) k = SigKind.SHULKER;
                else if (id.endsWith("_bed")) k = SigKind.BED;
                else if (id.endsWith("candle")) k = SigKind.CANDLE;
                else if (block instanceof CropBlock) k = SigKind.CROP;
                else if (id.endsWith("_pressure_plate")) k = SigKind.TRAP;
                else if (id.contains("copper") && !id.contains("ore") && !id.startsWith("raw_") && !id.contains("torch")
                        && !id.contains("lantern") && !id.contains("chain") && !id.contains("bars")) {
                    String bare = id.startsWith("waxed_") ? id.substring(6) : id;
                    k = bare.startsWith("exposed_") ? SigKind.COPPER_1 : bare.startsWith("weathered_") ? SigKind.COPPER_2
                            : bare.startsWith("oxidized_") ? SigKind.COPPER_3 : SigKind.COPPER_0;
                    if (id.startsWith("waxed_")) WAXED.add(block);
                }
            }
            if (k != null) SIGNALS.put(block, k);
            switch (id) {
                case "small_amethyst_bud" -> BUD_STAGE.put(block, 1);
                case "medium_amethyst_bud" -> BUD_STAGE.put(block, 2);
                case "large_amethyst_bud" -> BUD_STAGE.put(block, 3);
                case "amethyst_cluster" -> BUD_STAGE.put(block, 4);
                default -> {}
            }

            Marker m = switch (id) {
                case "bell" -> Marker.BELL;
                case "cobweb" -> Marker.COBWEB;
                case "rail" -> Marker.RAIL;
                case "end_portal_frame" -> Marker.END_PORTAL_FRAME;
                case "infested_stone_bricks", "infested_mossy_stone_bricks", "infested_cracked_stone_bricks" -> Marker.INFESTED_BRICKS;
                case "spawner" -> Marker.SPAWNER;
                case "mossy_cobblestone" -> Marker.MOSSY_COBBLE;
                case "reinforced_deepslate" -> Marker.REINFORCED_DEEPSLATE;
                case "deepslate_bricks", "deepslate_tiles", "cracked_deepslate_bricks", "cracked_deepslate_tiles" -> Marker.DEEP_BRICKS;
                case "trial_spawner" -> Marker.TRIAL_SPAWNER;
                case "vault" -> Marker.VAULT;
                case "gilded_blackstone" -> Marker.GILDED_BLACKSTONE;
                case "nether_brick_fence" -> Marker.NETHER_BRICK_FENCE;
                case "nether_bricks" -> Marker.NETHER_BRICKS;
                case "purpur_block", "purpur_pillar" -> Marker.PURPUR;
                case "prismarine_bricks", "dark_prismarine" -> Marker.PRISMARINE;
                case "tnt" -> Marker.TNT;
                case "sticky_piston" -> Marker.STICKY_PISTON;
                case "crying_obsidian" -> Marker.CRYING_OBSIDIAN;
                case "suspicious_gravel", "suspicious_sand" -> Marker.SUSPICIOUS;
                case "snow_block" -> Marker.SNOW_BLOCK;
                case "chiseled_sandstone" -> Marker.CHISELED_SANDSTONE;
                default -> null;
            };
            if (m != null) MARKERS.put(block, m);
        }
    }

    /** Raw evidence for one chunk. Scores are computed later, when neighbouring structure markers are known. */
    static final class ChunkReport {
        final int chunkX, chunkZ, layer;
        final int[] blockCounts = new int[FEATURES.size()];
        final int[] entityCounts = new int[FEATURES.size()];
        final EnumSet<Structure> structures = EnumSet.noneOf(Structure.class);
        final List<Key> keys = new ArrayList<>();
        final int[] signals = new int[Signal.values().length];
        final List<String> signs = new ArrayList<>();
        final Set<String> heads = new LinkedHashSet<>();
        /** Amethyst clock: open or budded faces around budding amethyst, and their summed growth stage. */
        int amethystFaces, amethystStageSum, amethystBudding;
        /** Ores the world seed puts in this chunk layer that players have dug out; -1 until analysed. */
        int minedOres = -1, seedOres;
        /** Player-dug tunnel air blocks in this chunk layer (straight, flat, narrow passages). */
        int tunnelBlocks;
        EntityIntel entities = EntityIntel.EMPTY;
        int keyYSum, keyYCount;
        long scannedAt;

        ChunkReport(int chunkX, int chunkZ, int layer) { this.chunkX = chunkX; this.chunkZ = chunkZ; this.layer = layer; }

        int count(Feature f) { return blockCounts[f.id] + entityCounts[f.id]; }

        int sig(Signal s) { return signals[s.ordinal()]; }

        boolean empty() {
            for (int i = 0; i < blockCounts.length; i++) if (blockCounts[i] != 0 || entityCounts[i] != 0) return false;
            // Chunks that only carry structure markers or amethyst still matter (suppression, the amethyst clock).
            return structures.isEmpty() && amethystFaces == 0 && entities == EntityIntel.EMPTY
                    && sig(Signal.CRATER) == 0 && sig(Signal.LAVA_FLOW) == 0 && minedOres <= 0 && tunnelBlocks == 0;
        }
    }

    /** Per-chunk entity facts gathered by the entity pass. */
    static final class EntityIntel {
        static final EntityIntel EMPTY = new EntityIntel();
        int framesFull, framesEmpty, elytraFrames, standsDressed, standsBare, drops, xpOrbs, villagers, vehicles;
        /** Raid aftermath: valuables and block items lying around, live TNT and withers. */
        int lootDrops, debrisDrops, tntEntities, withers;
        final Set<String> named = new LinkedHashSet<>();
        final Set<String> owners = new LinkedHashSet<>();
        final Set<String> players = new LinkedHashSet<>();
    }

    /** A tracked block worth highlighting in the world. */
    record Key(BlockPos pos, Feature feature) {}

    private static final int MAX_KEYS_PER_CHUNK = 96;

    /**
     * An immutable copy of a chunk's blocks plus the block-entity text the scan needs. Taken on the
     * client thread (a few array copies), then analysed on the Base Finder worker thread, so scanning
     * never stalls the game.
     */
    /** A piece of block-entity text and the height it was found at. */
    record Tagged(int y, String text) {}

    /** Layer index: deepslate (below y=0) and surface (y=0 and up) are analysed separately. */
    static final int DEEP = 0, SURFACE = 1;
    static int layerOf(double y) { return y < 0 ? DEEP : SURFACE; }

    record Snapshot(int chunkX, int chunkZ, LevelChunkSection[] sections, int minSectionY,
                    List<Tagged> signs, List<Tagged> heads) {
        BlockState state(int lx, int y, int lz) {
            int index = (y >> 4) - minSectionY;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || index < 0 || index >= sections.length || sections[index] == null)
                return null;
            return sections[index].getBlockState(lx, y & 15, lz);
        }
    }

    static Snapshot snapshot(LevelChunk chunk) {
        LevelChunkSection[] live = chunk.getSections();
        LevelChunkSection[] copy = new LevelChunkSection[live.length];
        for (int i = 0; i < live.length; i++)
            if (live[i] != null && !live[i].hasOnlyAir()) copy[i] = live[i].copy();
        List<Tagged> signs = new ArrayList<>();
        List<Tagged> heads = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            int y = be.getBlockPos().getY();
            if (be instanceof SignBlockEntity sign && signs.size() < 16) {
                String text = (signText(sign.getFrontText()) + " " + signText(sign.getBackText())).trim().replaceAll("\\s+", " ");
                if (!text.isEmpty()) signs.add(new Tagged(y, text));
            } else if (be instanceof SkullBlockEntity skull && skull.getOwnerProfile() != null) {
                skull.getOwnerProfile().name().ifPresent(n -> heads.add(new Tagged(y, n)));
            }
        }
        return new Snapshot(chunk.getPos().x(), chunk.getPos().z(), copy, chunk.getMinSectionY(), signs, heads);
    }

    /**
     * Thread-safe: works only on the snapshot. Returns one report per layer ({@link #DEEP}, {@link #SURFACE}),
     * so a cave stash and a surface base above it are never blended together.
     */
    static ChunkReport[] scan(Snapshot snap) {
        build();
        ChunkReport[] layers = {new ChunkReport(snap.chunkX(), snap.chunkZ(), DEEP), new ChunkReport(snap.chunkX(), snap.chunkZ(), SURFACE)};
        int[][] markerSets = new int[2][Marker.values().length];
        LevelChunkSection[] sections = snap.sections();
        int baseX = snap.chunkX() << 4, baseZ = snap.chunkZ() << 4;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null) continue;
            boolean[] trackHere = {false};
            int baseY = (snap.minSectionY() + i) << 4;
            final ChunkReport report = layers[layerOf(baseY)];
            final int[] markers = markerSets[layerOf(baseY)];
            // Ancient cities sit near y=-51; deepslate bricks/tiles that deep are almost never player-made.
            boolean deep = baseY < -16;
            section.getStates().count((state, n) -> {
                Block block = state.getBlock();
                Feature f = BY_BLOCK.get(block);
                if (f != null) {
                    report.blockCounts[f.id] += n;
                    if (f.track) trackHere[0] = true;
                }
                Marker m = MARKERS.get(block);
                if (m != null && (m != Marker.DEEP_BRICKS || deep)) markers[m.ordinal()] += n;
                SigKind k = SIGNALS.get(block);
                if (k != null) signal(report, k, state, n);
                // flowing lava up at build height is almost always a lava cast poured over a base
                if (baseY >= 48 && block == net.minecraft.world.level.block.Blocks.LAVA && !state.getFluidState().isSource())
                    add(report, Signal.LAVA_FLOW, n);
            });
            if (report.sig(Signal.BUDDING_AMETHYST) > 0 && section.maybeHas(st -> SIGNALS.get(st.getBlock()) == SigKind.BUDDING))
                amethyst(snap, report, section, baseY);
            if (!trackHere[0] || report.keys.size() >= MAX_KEYS_PER_CHUNK) continue;
            for (int y = 0; y < 16 && report.keys.size() < MAX_KEYS_PER_CHUNK; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Feature f = BY_BLOCK.get(state.getBlock());
                        if (f == null || !f.track) continue;
                        // A portal is one feature, not 21; keep just its first block.
                        if (f.name.equals("nether portal") && report.keyYCount > 0 && hasKey(report, f)) continue;
                        report.keys.add(new Key(new BlockPos(baseX + x, baseY + y, baseZ + z), f));
                        report.keyYSum += baseY + y;
                        report.keyYCount++;
                        if (report.keys.size() >= MAX_KEYS_PER_CHUNK) break;
                    }
        }
        long now = System.currentTimeMillis();
        craters(snap, layers[SURFACE]);
        for (int l = 0; l < 2; l++) {
            detectStructures(layers[l], markerSets[l]);
            layers[l].scannedAt = now;
        }
        for (Tagged t : snap.signs()) layers[layerOf(t.y())].signs.add(t.text());
        for (Tagged t : snap.heads()) layers[layerOf(t.y())].heads.add(t.text());
        return layers;
    }

    private static void signal(ChunkReport r, SigKind k, BlockState state, int n) {
        switch (k) {
            case CHEST -> add(r, state.hasProperty(BlockStateProperties.CHEST_TYPE)
                    && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE ? Signal.CHEST_HALF : Signal.CHEST_SINGLE, n);
            case TRAPPED -> add(r, Signal.TRAPPED, n);
            case BARREL -> add(r, Signal.BARREL, n);
            case SHULKER -> add(r, Signal.SHULKER, n);
            case ENDER -> add(r, Signal.ENDER_CHEST, n);
            case HOPPER -> add(r, Signal.HOPPER, n);
            case DISPENSER -> add(r, Signal.DISPENSER, n);
            case CRAFTER -> add(r, Signal.CRAFTER, n);
            case FURNACE -> {
                add(r, Signal.FURNACE, n);
                if (is(state, BlockStateProperties.LIT)) add(r, Signal.LIT_FURNACE, n);
            }
            case LAMP -> { if (is(state, BlockStateProperties.LIT)) add(r, Signal.POWERED, n); }
            case PISTON -> { if (is(state, BlockStateProperties.EXTENDED)) add(r, Signal.POWERED, n); }
            case POWERABLE -> { if (is(state, BlockStateProperties.POWERED)) add(r, Signal.POWERED, n); }
            case JUKEBOX -> { if (is(state, BlockStateProperties.HAS_RECORD)) add(r, Signal.JUKEBOX_DISC, n); }
            case LECTERN -> { if (is(state, BlockStateProperties.HAS_BOOK)) add(r, Signal.LECTERN_BOOK, n); }
            case BREWING -> {
                int bottles = (is(state, BlockStateProperties.HAS_BOTTLE_0) ? 1 : 0) + (is(state, BlockStateProperties.HAS_BOTTLE_1) ? 1 : 0)
                        + (is(state, BlockStateProperties.HAS_BOTTLE_2) ? 1 : 0);
                add(r, Signal.BREWING_BOTTLE, bottles * n);
            }
            case ANCHOR -> {
                if (state.hasProperty(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)
                        && state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) > 0) add(r, Signal.ANCHOR_CHARGED, n);
            }
            case COMPOSTER -> {
                if (state.hasProperty(BlockStateProperties.LEVEL_COMPOSTER)
                        && state.getValue(BlockStateProperties.LEVEL_COMPOSTER) > 0) add(r, Signal.COMPOSTER_FILLED, n);
            }
            case CROP -> {
                add(r, Signal.CROPS, n);
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) add(r, Signal.CROPS_MATURE, n);
            }
            case BED -> add(r, Signal.BED_PART, n);
            case TNT -> add(r, Signal.TNT, n);
            case FIRE -> add(r, Signal.FIRE, n);
            case TRAP -> add(r, Signal.TRAP, n);
            case IRON_DOOR -> add(r, Signal.IRON_DOOR_PART, n);
            case OBSIDIAN -> add(r, Signal.OBSIDIAN, n);
            case CANDLE -> { if (is(state, BlockStateProperties.LIT)) add(r, Signal.CANDLE_LIT, n); }
            case COPPER_0, COPPER_1, COPPER_2, COPPER_3 -> {
                add(r, switch (k) {
                    case COPPER_1 -> Signal.COPPER_EXPOSED;
                    case COPPER_2 -> Signal.COPPER_WEATHERED;
                    case COPPER_3 -> Signal.COPPER_OXIDIZED;
                    default -> Signal.COPPER_FRESH;
                }, n);
                if (WAXED.contains(state.getBlock())) add(r, Signal.COPPER_WAXED, n);
            }
            case PORTAL_FRAME -> {
                add(r, Signal.PORTAL_FRAME, n);
                if (is(state, BlockStateProperties.EYE)) add(r, Signal.PORTAL_FRAME_EYE, n);
            }
            case END_PORTAL -> add(r, Signal.END_PORTAL, n);
            case NETHER_PORTAL -> add(r, Signal.NETHER_PORTAL, n);
            case SPAWNER -> add(r, Signal.SPAWNER, n);
            case VALUABLE -> add(r, Signal.VALUABLE, n);
            case BEACON -> { add(r, Signal.BEACON, n); add(r, Signal.VALUABLE, n); }
            case BUDDING -> add(r, Signal.BUDDING_AMETHYST, n);
        }
    }

    private static boolean is(BlockState state, net.minecraft.world.level.block.state.properties.BooleanProperty p) {
        return state.hasProperty(p) && state.getValue(p);
    }

    private static void add(ChunkReport r, Signal s, int n) { r.signals[s.ordinal()] += n; }

    /**
     * Amethyst clock. Generation gives ~35% of budding blocks one crystal of a random stage; after that a
     * face only grows (about 1.76 stages per hour) while a player is within simulation distance. So the
     * maturity of the faces measures how long players have spent near this chunk.
     */
    private static final Set<String> RAW_GROUND = Set.of("dirt", "coarse_dirt", "rooted_dirt", "mud");
    private static final Set<Block> RAW_GROUND_BLOCKS = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Explosion craters. Crystals and TNT blow bowls into the ground and strip the grass, leaving raw
     * dirt at the bottom. Stone is left out: mountains are full of natural stone hollows. A column counts when it is bare dirt and at least 3 blocks below 7 of
     * the 8 points on a 4-block ring around it: ravines (open along their axis), ponds (water is the
     * surface) and grassy courtyards (grass is not raw ground) do not match.
     */
    private static void craters(Snapshot snap, ChunkReport r) {
        if (RAW_GROUND_BLOCKS.isEmpty())
            synchronized (RAW_GROUND_BLOCKS) {
                if (RAW_GROUND_BLOCKS.isEmpty())
                    for (Block b : BuiltInRegistries.BLOCK) if (RAW_GROUND.contains(BuiltInRegistries.BLOCK.getKey(b).getPath())) RAW_GROUND_BLOCKS.add(b);
            }
        int topY = -1;
        LevelChunkSection[] sections = snap.sections();
        for (int i = sections.length - 1; i >= 0; i--)
            if (sections[i] != null) { topY = ((snap.minSectionY() + i) << 4) + 15; break; }
        if (topY < 0) return;
        int[] h = new int[256];
        Block[] top = new Block[256];
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                int idx = z * 16 + x;
                h[idx] = Integer.MIN_VALUE;
                for (int y = topY; y >= 0; y--) {
                    BlockState st = snap.state(x, y, z);
                    if (st == null) { y &= ~15; continue; }          // empty section: skip it whole
                    if (st.isAir()) continue;
                    if (st.getFluidState().isEmpty() && (!st.blocksMotion() || st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.LOGS)
                            || st.getBlock() instanceof net.minecraft.world.level.block.HugeMushroomBlock)) continue;   // trees are not terrain
                    h[idx] = y;
                    top[idx] = st.getBlock();
                    break;
                }
            }
        int count = 0;
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                int c = h[z * 16 + x];
                if (c == Integer.MIN_VALUE || !RAW_GROUND_BLOCKS.contains(top[z * 16 + x])) continue;
                // small blasts are caught by the inner rings, big crystal/TNT bowls by the 7-block one
                if (rimAbove(h, x, z, c, 3) || rimAbove(h, x, z, c, 5) || rimAbove(h, x, z, c, 7)) count++;
            }
        if (count > 0) add(r, Signal.CRATER, count);
    }

    /** Ring directions in opposite pairs: (0,1) (2,3) are the x axis... */
    private static final int[][] RING = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    /**
     * True when the ground rises at least 3 blocks on the ring of radius {@code r} around a column, in
     * a bowl: at least two opposite pairs of ring points must be known and higher, and every known point
     * must be higher (one miss allowed when all 8 are known). Ring points outside the chunk are unknown.
     * Cliff edges and valleys rise on one side or one axis only, so they never pass.
     */
    private static boolean rimAbove(int[] h, int x, int z, int c, int r) {
        int known = 0, higher = 0, pairs = 0;
        boolean[] up = new boolean[8], seen = new boolean[8];
        for (int i = 0; i < 8; i++) {
            int[] o = RING[i];
            int d = o[0] != 0 && o[1] != 0 ? Math.round(r * 0.72f) : r;
            int nx = x + o[0] * d, nz = z + o[1] * d;
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || h[nz * 16 + nx] == Integer.MIN_VALUE) continue;
            seen[i] = true;
            known++;
            if (h[nz * 16 + nx] - c >= 3) { higher++; up[i] = true; }
        }
        for (int i = 0; i < 8; i += 2) if (seen[i] && seen[i + 1] && up[i] && up[i + 1]) pairs++;
        return pairs >= 2 && higher >= known - (known == 8 ? 1 : 0);
    }

    private static void amethyst(Snapshot snap, ChunkReport r, LevelChunkSection section, int baseY) {
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    if (SIGNALS.get(section.getBlockState(x, y, z).getBlock()) != SigKind.BUDDING) continue;
                    r.amethystBudding++;
                    for (Direction d : Direction.values()) {
                        BlockState n = snap.state(x + d.getStepX(), baseY + y + d.getStepY(), z + d.getStepZ());
                        if (n == null) continue;
                        Integer stage = BUD_STAGE.get(n.getBlock());
                        if (stage != null) {
                            if (n.hasProperty(BlockStateProperties.FACING) && n.getValue(BlockStateProperties.FACING) == d) {
                                r.amethystFaces++;
                                r.amethystStageSum += stage;
                            }
                        } else if (n.isAir() || (n.is(net.minecraft.world.level.block.Blocks.WATER) && n.getFluidState().isSourceOfType(Fluids.WATER))) {
                            r.amethystFaces++;
                        }
                    }
                }
    }

    private static String signText(net.minecraft.world.level.block.entity.SignText text) {
        StringBuilder sb = new StringBuilder();
        for (var line : text.getMessages(false)) {
            String s = line.getString().trim();
            if (!s.isEmpty()) sb.append(sb.length() > 0 ? " / " : "").append(s);
        }
        return sb.toString();
    }

    private static boolean hasKey(ChunkReport report, Feature f) {
        for (Key key : report.keys) if (key.feature() == f) return true;
        return false;
    }

    private static void detectStructures(ChunkReport r, int[] m) {
        if (m[Marker.BELL.ordinal()] > 0) r.structures.add(Structure.VILLAGE);
        if (m[Marker.COBWEB.ordinal()] >= 3 && m[Marker.RAIL.ordinal()] >= 3) r.structures.add(Structure.MINESHAFT);
        if (m[Marker.END_PORTAL_FRAME.ordinal()] > 0 || m[Marker.INFESTED_BRICKS.ordinal()] > 0) r.structures.add(Structure.STRONGHOLD);
        if (m[Marker.SPAWNER.ordinal()] > 0) r.structures.add(Structure.DUNGEON);
        // Shriekers also appear in ordinary sculk patches, so only city-only blocks count.
        if (m[Marker.REINFORCED_DEEPSLATE.ordinal()] > 0 || m[Marker.DEEP_BRICKS.ordinal()] >= 40) r.structures.add(Structure.ANCIENT_CITY);
        if (m[Marker.TRIAL_SPAWNER.ordinal()] > 0 || m[Marker.VAULT.ordinal()] > 0) r.structures.add(Structure.TRIAL_CHAMBER);
        if (m[Marker.GILDED_BLACKSTONE.ordinal()] > 0) r.structures.add(Structure.BASTION);
        if (m[Marker.NETHER_BRICK_FENCE.ordinal()] >= 3 && m[Marker.NETHER_BRICKS.ordinal()] >= 80) r.structures.add(Structure.FORTRESS);
        if (m[Marker.PURPUR.ordinal()] >= 40) r.structures.add(Structure.END_CITY);
        if (m[Marker.PRISMARINE.ordinal()] >= 30) r.structures.add(Structure.MONUMENT);
        if (m[Marker.TNT.ordinal()] >= 4 || m[Marker.CHISELED_SANDSTONE.ordinal()] >= 4
                || (m[Marker.MOSSY_COBBLE.ordinal()] >= 30 && m[Marker.STICKY_PISTON.ordinal()] > 0)) r.structures.add(Structure.TEMPLE);
        if (m[Marker.CRYING_OBSIDIAN.ordinal()] > 0) r.structures.add(Structure.RUINED_PORTAL);
        if (m[Marker.SUSPICIOUS.ordinal()] > 0) r.structures.add(Structure.TRAIL_RUINS);
        // Igloos have no unique block; their few items (bed, furnace, brewing stand) score below any threshold.
    }

    /**
     * Score of one chunk given the structures detected in it and its neighbours.
     * {@code perCategory} receives the contribution of each category.
     */
    static float score(ChunkReport r, EnumSet<Structure> nearby, Dim dim, boolean ignoreNatural,
                       EnumSet<Category> enabled, float[] perCategory, float[] perFeature) {
        float total = 0;
        for (Feature f : FEATURES) {
            int count = r.count(f);
            if (count == 0 || !enabled.contains(f.category) || f.natural.contains(dim)) continue;
            if (ignoreNatural && !java.util.Collections.disjoint(f.explainedBy, nearby)) continue;
            float s = f.weight * Math.min(count, f.cap);
            total += s;
            if (perCategory != null) perCategory[f.category.ordinal()] += s;
            if (perFeature != null) perFeature[f.id] += s;
        }
        return total;
    }
}
