package unblonded.anthrax.modules.world;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.world.BaseScanner.Category;
import unblonded.anthrax.modules.world.BaseScanner.ChunkReport;
import unblonded.anthrax.modules.world.BaseScanner.Dim;
import unblonded.anthrax.modules.world.BaseScanner.Feature;
import unblonded.anthrax.modules.world.BaseScanner.Key;
import unblonded.anthrax.modules.world.BaseScanner.Structure;
import unblonded.anthrax.modules.world.BaseScanner.Signal;
import unblonded.anthrax.modules.world.BaseScanner.EntityIntel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base Finder: scans every chunk the server sends for evidence of players (storage, valuables,
 * redstone, portals, builds, farms, torches...), discounts anything a natural structure explains,
 * groups nearby active chunks into bases, classifies them (stash, base, farm, redstone, build,
 * portal), and shows them as beams, outlines, highlighted blocks, labels and a HUD list. Finds are
 * announced and logged per server so revisits are recognised.
 */
public final class BaseFinderModule extends FeatureModule {
    public static final BaseFinderModule INSTANCE = new BaseFinderModule();

    /** A detected base, exposed read-only to the HUD. */
    public static final class Base {
        /** Stable across re-clustering, so UI animations and selections follow the same base. */
        public long id;
        /** {@link BaseScanner#DEEP} or {@link BaseScanner#SURFACE}. */
        public int layer;
        public String type;
        public int color;
        public float score;
        public int confidence;
        public int x, y, z;
        public String summary;
        public boolean known;
        public int chunks;
        int minChunkX, minChunkZ, maxChunkX, maxChunkZ, minY, maxY;
        final List<Key> keys = new ArrayList<>();
        long firstSeen;
        boolean announced;

        /** Highest detected block of the base (where its stats card is pinned). */
        public int topY() { return maxY; }

        // ---- storage inventory
        public int singleChests, doubleChests, trappedChests, barrels, shulkers, enderChests, hoppers, dispensers, crafters;
        public int furnaces, storageSlots;
        // ---- valuables and fixtures
        public int beacons, valuableBlocks, beds, respawnAnchors, lecternBooks, jukeboxDiscs, brewingBottles, filledComposters;
        // ---- live state
        public int litFurnaces, poweredRedstone, litCandles, crops, cropsMature, drops, xpOrbs;
        public final List<String> playersHere = new ArrayList<>();
        // ---- ownership
        public final List<String> owners = new ArrayList<>(), named = new ArrayList<>(), heads = new ArrayList<>(), signs = new ArrayList<>();
        public int pets, framesFull, framesEmpty, standsDressed, standsBare, villagers;
        // ---- defense and raid evidence
        public int obsidian, ironDoors, traps, tnt, fire;
        // ---- age
        public int copperFresh, copperAged, copperWaxed;
        /** Estimated hours of player presence from the amethyst clock, -1 if no geode nearby. */
        public float amethystHours = -1;
        public int amethystFaces;
        // ---- raid aftermath
        public int crystals, craters, lavaFlows, lootDrops, debrisDrops, tntEntities, withers, workstations, portals;
        public final List<String> raiderSigns = new ArrayList<>();
        /** Weight of the raid evidence (0 = none, 30+ = clearly raided); drives {@link #raidLabel}. */
        public int raidDamage;
        public String raidLabel = "";
        // ---- assessments (0..100) with the evidence behind them
        public int unraided, activity;
        public String activityLabel = "";
        public final List<String> unraidedWhy = new ArrayList<>(), activityWhy = new ArrayList<>(), notes = new ArrayList<>();
        public final List<String> nearbyStructures = new ArrayList<>();
        public float[] categories = new float[Category.values().length];
    }

    /** A natural structure, with what can be read about whether players got to it first. */
    public static final class Poi {
        public String type;
        public int x, y, z, color;
        public final List<String> notes = new ArrayList<>();
        public boolean touched;
        public int layer;
    }

    // ---- detection
    private final Setting.Mode sensitivity = setting(Setting.Mode.standalone("Sensitivity", "Balanced", "Low", "Balanced", "High", "Paranoid"));
    private final Setting.Num minBase = setting(Setting.Num.standalone("Min base score", 120, 30, 1500, 10));
    private final Setting.Bool ignoreNatural = setting(Setting.Bool.standalone("Ignore natural structures", true));
    private final Setting.Num scanSpeed = setting(Setting.Num.standalone("Scan speed (chunks/tick)", 4, 1, 24, 1));
    private final Setting.Bool multithreaded = setting(Setting.Bool.standalone("Multithreaded scanning", true));
    private final Setting.Bool rescanOnTeleport = setting(Setting.Bool.standalone("Clear and rescan on teleport", true));
    // ---- what counts
    private final Setting.Bool useStorage = setting(Setting.Bool.standalone("Detect storage", true));
    private final Setting.Bool useValuables = setting(Setting.Bool.standalone("Detect valuables", true));
    private final Setting.Bool useRedstone = setting(Setting.Bool.standalone("Detect redstone", true));
    private final Setting.Bool usePortals = setting(Setting.Bool.standalone("Detect portals", true));
    private final Setting.Bool useBuilds = setting(Setting.Bool.standalone("Detect builds", true));
    private final Setting.Bool useFarms = setting(Setting.Bool.standalone("Detect farms", true));
    private final Setting.Bool useActivity = setting(Setting.Bool.standalone("Detect torches and paths", true));
    private final Setting.Bool useAmethyst = setting(Setting.Bool.standalone("Amethyst growth evidence", true));
    // ---- display
    private final Setting.Bool beams = setting(Setting.Bool.standalone("Beacon beams", true));
    private final Setting.Bool outlines = setting(Setting.Bool.standalone("Area outlines", true));
    private final Setting.Bool highlight = setting(Setting.Bool.standalone("Highlight key blocks", true));
    private final Setting.Bool labels = setting(Setting.Bool.standalone("Labels and stat cards", true));
    private final Setting.Bool tracers = setting(Setting.Bool.standalone("Tracers", false));
    private final Setting.Bool throughWalls = setting(Setting.Bool.standalone("Render through walls", true));
    private final Setting.Num maxShown = setting(Setting.Num.standalone("Max bases shown", 8, 1, 30, 1));
    private final Setting.Bool hudList = setting(Setting.Bool.standalone("HUD list", true));
    private final Setting.Bool hudIntel = setting(Setting.Bool.standalone("Stat card over base", true));
    private final Setting.Mode cardSize = setting(Setting.Mode.standalone("Stat card size", "Small", "Small", "Medium", "Large"));
    private final Setting.Bool structures = setting(Setting.Bool.standalone("Show natural structures", true));
    // ---- alerts and log
    private final Setting.Bool notify = setting(Setting.Bool.standalone("Notify on find", true));
    private final Setting.Bool notifyKnown = setting(Setting.Bool.standalone("Notify known bases", false));
    private final Setting.Bool log = setting(Setting.Bool.standalone("Log finds to file", true));
    private final Setting.Action openIntel = setting(new Setting.Action("Open Base Intel", () ->
            Minecraft.getInstance().gui.setScreen(new unblonded.anthrax.gui.IntelScreen())));
    private final Setting.Action rescan = setting(new Setting.Action("Rescan loaded chunks", this::rescanAll));
    private final Setting.Action copyBest = setting(new Setting.Action("Copy top base coordinates", this::copyBest));
    private final Setting.Action clear = setting(new Setting.Action("Clear results", this::reset));

    private static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Anthrax Base Finder");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });
    private record Result(long generation, long key, ChunkReport[] reports) {}

    /** Deepslate-layer reports live in their own key space, far beyond any real chunk coordinate. */
    private static final int DEEP_OFFSET = 1 << 24;

    static long key(int cx, int cz, int layer) {
        return ChunkPos.pack(cx, layer == BaseScanner.DEEP ? cz + DEEP_OFFSET : cz);
    }

    private static int layerOfKey(long key) { return ChunkPos.getZ(key) > DEEP_OFFSET / 2 ? BaseScanner.DEEP : BaseScanner.SURFACE; }
    private static int realZ(long key) { int z = ChunkPos.getZ(key); return z > DEEP_OFFSET / 2 ? z - DEEP_OFFSET : z; }

    private static int shownLayer = BaseScanner.SURFACE;

    /**
     * The layer the player is in; only that layer is shown. A one-block buffer around y=0 stops the
     * display flipping back and forth while you stand on the boundary.
     */
    private static int playerLayer() {
        var p = Minecraft.getInstance().player;
        if (p == null) return shownLayer;
        if (shownLayer == BaseScanner.SURFACE && p.getY() < -1) shownLayer = BaseScanner.DEEP;
        else if (shownLayer == BaseScanner.DEEP && p.getY() >= 1) shownLayer = BaseScanner.SURFACE;
        return shownLayer;
    }
    private final java.util.concurrent.ConcurrentLinkedQueue<Result> results = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
    /** Bumped on reset so results from a previous world are dropped. */
    private volatile long generation;
    private final Map<Long, ChunkReport> reports = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private volatile List<Base> bases = List.of();
    private final java.util.concurrent.atomic.AtomicReference<Object[]> layerView = new java.util.concurrent.atomic.AtomicReference<>();
    private volatile List<Poi> pois = List.of();
    private int trackedX = Integer.MIN_VALUE, trackedZ;
    private ClientLevel level;
    private Dim dim = Dim.OVERWORLD;
    private long lastEntityPass, lastRequeue, lastCluster;
    private boolean dirty;
    private int scannedTotal;
    private String settingsKey = "";
    private Vec3 lastPos;

    private BaseFinderModule() {
        super("Base Finder", "Detect, classify and track player bases, stashes and farms in loaded chunks", "World", false);
        ClientChunkEvents.CHUNK_LOAD.register((lvl, chunk) -> {
            if (isEnabled() && lvl == level) enqueue(chunk.getPos().x(), chunk.getPos().z());
        });
    }

    // ------------------------------------------------------------------ lifecycle
    @Override protected void onToggle(boolean enabled) {
        if (enabled) {
            reset();
            level = Minecraft.getInstance().level;
            if (level != null) { dim = dimOf(level); BaseLog.load(level); rescanAll(); }
        } else {
            reset();
        }
    }

    private void reset() {
        generation++;
        results.clear();
        reports.clear();
        queue.clear();
        queued.clear();
        bases = List.of();
        pois = List.of();
        scannedTotal = 0;
        lastPos = null;
        dirty = false;
    }

    private void enqueue(int cx, int cz) {
        long key = ChunkPos.pack(cx, cz);
        if (queued.add(key)) queue.add(key);
    }

    private void rescanAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int view = mc.options.getEffectiveRenderDistance() + 1;
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        // nearest rings first, so the area around the player is ready almost immediately
        for (int r = 0; r <= view; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && mc.level.hasChunk(pcx + dx, pcz + dz))
                        enqueue(pcx + dx, pcz + dz);
    }

    @Override public void tick(Minecraft client) {
        if (client.level == null || client.player == null) return;
        if (client.level != level) {
            reset();
            level = client.level;
            dim = dimOf(level);
            BaseLog.load(level);
            rescanAll();
        }
        long now = System.currentTimeMillis();
        // A jump of more than 64 blocks in one tick is a teleport (/tp, /home, warps, pearl stasis):
        // the old area's results are stale, so start over around the new position.
        Vec3 pos = client.player.position();
        if (lastPos != null && rescanOnTeleport.get() && pos.distanceToSqr(lastPos) > 64 * 64) {
            reset();
            rescanAll();
            if (notify.get()) Notifications.info("Base Finder", "Teleport detected, rescanning");
        }
        lastPos = pos;
        String key = sensitivity.get() + ignoreNatural.get() + minBase.get() + useStorage.get() + useValuables.get()
                + useRedstone.get() + usePortals.get() + useBuilds.get() + useFarms.get() + useActivity.get() + useAmethyst.get();
        if (!key.equals(settingsKey)) { settingsKey = key; dirty = true; }

        // Snapshot on this thread (cheap copies), analyse on the worker; at most a few batches in flight.
        int budget = (int) scanSpeed.get() * (multithreaded.get() ? 4 : 1);
        while (budget-- > 0 && !queue.isEmpty() && inFlight.get() < 64) {
            long chunkKey = queue.poll();
            queued.remove(chunkKey);
            LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey), ChunkStatus.FULL, false);
            if (chunk == null) continue;
            BaseScanner.Snapshot snap = BaseScanner.snapshot(chunk);
            if (multithreaded.get()) {
                long gen = generation;
                inFlight.incrementAndGet();
                WORKER.execute(() -> {
                    try { results.add(new Result(gen, chunkKey, BaseScanner.scan(snap))); }
                    catch (RuntimeException e) { System.err.println("Anthrax Base Finder: scan failed: " + e); }
                    finally { inFlight.decrementAndGet(); }
                });
            } else {
                apply(chunkKey, BaseScanner.scan(snap));
            }
        }
        for (Result r; (r = results.poll()) != null; ) if (r.generation() == generation) apply(r.key(), r.reports());
        if (now - lastEntityPass > 2000) { lastEntityPass = now; entityPass(); }
        if (now - lastRequeue > 90_000) { lastRequeue = now; rescanAll(); }
        // Re-cluster at most every 1.5 s; results are smoothed in stabilize().
        if (dirty && now - lastCluster > 1500) { lastCluster = now; dirty = false; cluster(client); }
    }

    private void apply(long chunkKey, ChunkReport[] layers) {
        for (ChunkReport report : layers) {
            long layerKey = key(report.chunkX, report.chunkZ, report.layer);
            ChunkReport previous = reports.get(layerKey);
            if (previous != null) {
                System.arraycopy(previous.entityCounts, 0, report.entityCounts, 0, report.entityCounts.length);
                report.entities = previous.entities;
            }
            if (report.empty()) reports.remove(layerKey);
            else reports.put(layerKey, report);
            dirty = true;
        }
        scannedTotal++;
    }

    /** Entities that only players create or gather, plus who owns or named them and who is here now. */
    private void entityPass() {
        BaseScanner.build();
        for (ChunkReport r : reports.values()) { java.util.Arrays.fill(r.entityCounts, 0); r.entities = EntityIntel.EMPTY; }
        Map<Long, int[]> animals = new HashMap<>();
        Set<Long> chestCarts = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        for (Entity e : level.entitiesForRendering()) {
            if (e == mc.player) continue;
            long key = key(e.blockPosition().getX() >> 4, e.blockPosition().getZ() >> 4, BaseScanner.layerOf(e.getY()));
            Feature f = null;
            EntityIntel intel;
            if (e instanceof ItemFrame frame) {
                f = BaseScanner.ITEM_FRAME;
                intel = intel(key);
                if (frame.getItem().isEmpty()) intel.framesEmpty++;
                else {
                    intel.framesFull++;
                    if (frame.getItem().is(Items.ELYTRA)) intel.elytraFrames++;
                }
            } else if (e instanceof ArmorStand stand) {
                f = BaseScanner.ARMOR_STAND;
                boolean dressed = !stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || !stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                        || !stand.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || !stand.getItemBySlot(EquipmentSlot.FEET).isEmpty();
                if (dressed) intel(key).standsDressed++; else intel(key).standsBare++;
            } else if (e instanceof Painting) f = BaseScanner.PAINTING;
            else if (e instanceof EndCrystal) f = BaseScanner.END_CRYSTAL;
            else if (e instanceof AbstractVillager) intel(key).villagers++;
            else if (e instanceof MinecartChest) { chestCarts.add(key); continue; }
            else if (e instanceof ItemEntity item) {
                EntityIntel in = intel(key);
                in.drops++;
                var stack = item.getItem();
                if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock
                        || stack.isEnchanted() || stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.ELYTRA) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                        || stack.is(Items.DIAMOND) || stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHERITE_SCRAP) || stack.is(Items.ENDER_CHEST))
                    in.lootDrops++;
                else if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) in.debrisDrops++;
            }
            else if (typeIs(e, "tnt", "tnt_minecart"))
                intel(key).tntEntities++;
            else if (typeIs(e, "wither", "wither_skull"))
                intel(key).withers++;
            else if (e instanceof ExperienceOrb) intel(key).xpOrbs++;
            else if (e instanceof AbstractBoat) intel(key).vehicles++;
            else if (e instanceof Player p) intel(key).players.add(p.getName().getString());
            if (e instanceof Animal) animals.computeIfAbsent(key, k -> new int[1])[0]++;
            if (e instanceof OwnableEntity owned && owned.getOwnerReference() != null) {
                java.util.UUID owner = owned.getOwnerReference().getUUID();
                var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(owner);
                intel(key).owners.add(info != null ? info.getProfile().name() : owner.toString().substring(0, 8));
            }
            if (e.hasCustomName() && !(e instanceof Player) && e.getCustomName() != null) intel(key).named.add(e.getCustomName().getString());
            if (f != null) report(key).entityCounts[f.id]++;
        }
        // A handful of villagers or animals is normal; dense crowds in one chunk are pens, halls and farms.
        for (ChunkReport r : reports.values()) {
            if (r.entities.villagers > 5) r.entityCounts[BaseScanner.VILLAGER_HALL.id] = r.entities.villagers - 5;
        }
        animals.forEach((key, n) -> { if (n[0] > 12) report(key).entityCounts[BaseScanner.ANIMAL_PEN.id] = n[0] - 12; });
        for (long key : chestCarts) { ChunkReport r = reports.get(key); if (r != null) r.structures.add(Structure.MINESHAFT); }
        reports.values().removeIf(ChunkReport::empty);
        dirty = true;
    }

    private static boolean typeIs(Entity e, String a, String b) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        return id.equals(a) || id.equals(b);
    }

    private EntityIntel intel(long key) {
        ChunkReport r = report(key);
        if (r.entities == EntityIntel.EMPTY) r.entities = new EntityIntel();
        return r.entities;
    }

    private ChunkReport report(long key) {
        return reports.computeIfAbsent(key, k -> new ChunkReport(ChunkPos.getX(k), realZ(k), layerOfKey(k)));
    }

    // ------------------------------------------------------------------ clustering
    private float chunkThreshold() {
        return switch (sensitivity.get()) {
            case "Low" -> 60f;
            case "High" -> 16f;
            case "Paranoid" -> 8f;
            default -> 30f;
        };
    }

    private EnumSet<Category> enabledCategories() {
        EnumSet<Category> set = EnumSet.noneOf(Category.class);
        if (useStorage.get()) set.add(Category.STORAGE);
        if (useValuables.get()) set.add(Category.VALUABLE);
        if (useRedstone.get()) set.add(Category.REDSTONE);
        if (usePortals.get()) set.add(Category.PORTAL);
        if (useBuilds.get()) set.add(Category.BUILD);
        if (useFarms.get()) set.add(Category.FARM);
        if (useActivity.get()) set.add(Category.ACTIVITY);
        return set;
    }

    /** Amethyst clock over a chunk and its neighbours (geodes often straddle chunk borders). */
    private float regionAmethystHours(ChunkReport r) {
        int faces = 0, stages = 0, budding = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                ChunkReport n = reports.get(key(r.chunkX + dx, r.chunkZ + dz, r.layer));
                if (n != null) { faces += n.amethystFaces; stages += n.amethystStageSum; budding += n.amethystBudding; }
            }
        return faces >= 8 ? AmethystClock.hours(faces, stages, budding) : -1f;
    }

    private record Scored(ChunkReport report, float score, float[] cats, float[] feats) {}

    private void cluster(Minecraft client) {
        EnumSet<Category> enabled = enabledCategories();
        boolean ignore = ignoreNatural.get();
        float threshold = chunkThreshold();
        Map<Long, Scored> active = new HashMap<>();
        for (Map.Entry<Long, ChunkReport> entry : reports.entrySet()) {
            ChunkReport r = entry.getValue();
            // Structures sprawl across chunks, so markers within two chunks explain this chunk too.
            EnumSet<Structure> nearby = EnumSet.noneOf(Structure.class);
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    ChunkReport n = reports.get(key(r.chunkX + dx, r.chunkZ + dz, r.layer));
                    if (n != null) nearby.addAll(n.structures);
                }
            float[] cats = new float[Category.values().length];
            float[] feats = new float[BaseScanner.FEATURES.size()];
            float score = BaseScanner.score(r, nearby, dim, ignore, enabled, cats, feats);
            // Amethyst only grows while players are nearby: a heavily grown geode means people spent hours here.
            if (useAmethyst.get() && r.amethystFaces > 0) {
                float hours = regionAmethystHours(r);
                if (hours > 0.5f) {
                    float a = Math.min(100f, (hours - 0.5f) * 25f);
                    score += a;
                    cats[Category.ACTIVITY.ordinal()] += a;
                    feats[BaseScanner.AMETHYST_GROWTH.id] += a;
                }
            }
            // Raid aftermath is evidence too: a base bombed down to the dirt still marks where players lived.
            // (Craters only lower the unraided score of a base found by other evidence; terrain can mimic them.)
            float raid = Math.min(40f, r.entities.lootDrops * 8f) + (r.entities.debrisDrops >= 12 ? 15f : 0);
            for (String sign : r.signs) if (RAIDER_SIGN.matcher(sign).find()) { raid += 40; break; }
            if (raid > 0) { score += raid; cats[Category.ACTIVITY.ordinal()] += raid; }
            if (score >= threshold) active.put(entry.getKey(), new Scored(r, score, cats, feats));
        }

        List<Base> previous = bases;
        List<Base> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long start : active.keySet()) {
            if (!seen.add(start)) continue;
            List<Scored> members = new ArrayList<>();
            ArrayDeque<Long> frontier = new ArrayDeque<>();
            frontier.add(start);
            while (!frontier.isEmpty()) {
                long k = frontier.poll();
                Scored sc = active.get(k);
                members.add(sc);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        long nk = key(sc.report.chunkX + dx, sc.report.chunkZ + dz, sc.report.layer);
                        if (active.containsKey(nk) && seen.add(nk)) frontier.add(nk);
                    }
            }
            Base base = build(members);
            if (base.score >= minBase.get()) found.add(base);
        }
        found.sort((a, b) -> Float.compare(b.score, a.score));

        long now = System.currentTimeMillis();
        Map<Base, Base> matches = new java.util.IdentityHashMap<>();
        Set<Base> claimed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Base base : found) {
            // nearest previous base within 64 blocks that no other new base has taken
            Base match = null;
            long bestD = 64L * 64;
            for (Base old : previous) {
                if (claimed.contains(old) || old.layer != base.layer) continue;
                long dx = old.x - base.x, dz = old.z - base.z, d = dx * dx + dz * dz;
                if (d < bestD) { bestD = d; match = old; }
            }
            if (match != null) {
                claimed.add(match);
                matches.put(base, match);
                base.id = match.id;
                base.firstSeen = match.firstSeen;
                base.announced = match.announced;
                base.known = match.known;
            } else {
                base.id = ++nextId;
                base.firstSeen = now;
                base.known = BaseLog.isKnown(base.x, base.z);
            }
            if (!base.announced && base.layer == playerLayer()) {
                base.announced = true;
                if (log.get() && !base.known) BaseLog.append(level, base);
                if (notify.get() && (!base.known || notifyKnown.get()) && client.player != null) {
                    double dist = Math.sqrt(client.player.distanceToSqr(base.x + .5, client.player.getY(), base.z + .5));
                    Notifications.success((base.known ? "Known " : "") + base.type + " found",
                            base.summary + "  |  " + Math.round(dist) + "m " + compass(client, base));
                }
            }
        }
        for (Base base : found) {
            assess(base, client);
            Base old = matches.get(base);
            if (old != null) stabilize(base, old);
        }
        bases = List.copyOf(found);
        pois = findPois(client);
    }

    private long nextId;

    /**
     * Re-clustering runs every couple of seconds and raw evidence jitters (items despawn, furnaces go
     * out, chunks rescan). Ease the headline numbers toward their new value and ignore tiny wobbles,
     * so the gauges move deliberately instead of flickering.
     */
    private static void stabilize(Base now, Base before) {
        now.confidence = ease(before.confidence, now.confidence);
        now.unraided = ease(before.unraided, now.unraided);
        now.activity = ease(before.activity, now.activity);
        now.activityLabel = activityLabel(now);
        if (before.amethystHours >= 0 && now.amethystHours >= 0)
            now.amethystHours = before.amethystHours + (now.amethystHours - before.amethystHours) * 0.4f;
    }

    private static int ease(int before, int target) {
        int diff = target - before;
        if (Math.abs(diff) <= 2) return before;                  // deadband
        return before + Math.round(diff * 0.4f);
    }

    /** A raided base is not "active": recent signals there are the raid itself. */
    static String activityLabel(Base b) {
        if (b.raidDamage >= 30) return b.activity >= 55 ? "Raided recently" : "Raided, abandoned";
        return activityLabel(b.activity);
    }

    static String activityLabel(int activity) {
        return activity >= 85 ? "Active now" : activity >= 55 ? "Recently active" : activity >= 28 ? "Dormant" : "Abandoned";
    }

    private Base build(List<Scored> members) {
        Base b = new Base();
        float total = 0, wx = 0, wz = 0;
        float[] cats = new float[Category.values().length];
        float[] feats = new float[BaseScanner.FEATURES.size()];
        int[] featCounts = new int[BaseScanner.FEATURES.size()];
        b.minChunkX = b.minChunkZ = Integer.MAX_VALUE;
        b.maxChunkX = b.maxChunkZ = Integer.MIN_VALUE;
        b.minY = Integer.MAX_VALUE;
        b.maxY = Integer.MIN_VALUE;
        int ySum = 0, yCount = 0;
        for (Scored s : members) {
            ChunkReport r = s.report;
            total += s.score;
            wx += (r.chunkX * 16 + 8) * s.score;
            wz += (r.chunkZ * 16 + 8) * s.score;
            for (int i = 0; i < cats.length; i++) cats[i] += s.cats[i];
            for (int i = 0; i < feats.length; i++) {
                feats[i] += s.feats[i];
                if (s.feats[i] > 0) featCounts[i] += r.count(BaseScanner.FEATURES.get(i));
            }
            b.minChunkX = Math.min(b.minChunkX, r.chunkX); b.maxChunkX = Math.max(b.maxChunkX, r.chunkX);
            b.minChunkZ = Math.min(b.minChunkZ, r.chunkZ); b.maxChunkZ = Math.max(b.maxChunkZ, r.chunkZ);
            for (Key key : r.keys) {
                if (s.feats[key.feature().id] <= 0) continue;   // suppressed by a natural structure
                b.keys.add(key);
                b.minY = Math.min(b.minY, key.pos().getY());
                b.maxY = Math.max(b.maxY, key.pos().getY());
            }
            ySum += r.keyYSum;
            yCount += r.keyYCount;
        }
        b.score = total;
        b.layer = members.getFirst().report.layer;
        b.chunks = members.size();
        b.x = Math.round(wx / total);
        b.z = Math.round(wz / total);
        b.y = yCount > 0 ? ySum / yCount : (b.layer == BaseScanner.DEEP ? -32 : 64);
        if (b.minY == Integer.MAX_VALUE) { b.minY = b.y - 4; b.maxY = b.y + 4; }
        b.confidence = (int) Math.round(100 * (1 - Math.exp(-total / 250.0)));
        b.type = classify(cats, total, featCounts, feats[BaseScanner.AMETHYST_GROWTH.id]);
        b.color = typeColor(b.type);
        b.summary = summarize(feats, featCounts);
        b.categories = cats;
        return b;
    }

    // ------------------------------------------------------------------ intel
    /** Fill a base's inventory, ownership, live-state and age facts, then score raid and activity likelihood. */
    private void assess(Base b, Minecraft mc) {
        // Facts come from every chunk the base covers plus a one-chunk margin (doors, farms and frames often sit at the edge).
        EnumSet<Structure> nearby = EnumSet.noneOf(Structure.class);
        int faces = 0, stageSum = 0, budding = 0;
        for (int cx = b.minChunkX - 1; cx <= b.maxChunkX + 1; cx++)
            for (int cz = b.minChunkZ - 1; cz <= b.maxChunkZ + 1; cz++) {
                ChunkReport r = reports.get(key(cx, cz, b.layer));
                if (r == null) continue;
                nearby.addAll(r.structures);
                b.singleChests += r.sig(Signal.CHEST_SINGLE);
                b.doubleChests += r.sig(Signal.CHEST_HALF);
                b.trappedChests += r.sig(Signal.TRAPPED);
                b.barrels += r.sig(Signal.BARREL);
                b.shulkers += r.sig(Signal.SHULKER);
                b.enderChests += r.sig(Signal.ENDER_CHEST);
                b.hoppers += r.sig(Signal.HOPPER);
                b.dispensers += r.sig(Signal.DISPENSER);
                b.crafters += r.sig(Signal.CRAFTER);
                b.furnaces += r.sig(Signal.FURNACE);
                b.litFurnaces += r.sig(Signal.LIT_FURNACE);
                b.poweredRedstone += r.sig(Signal.POWERED);
                b.litCandles += r.sig(Signal.CANDLE_LIT);
                b.jukeboxDiscs += r.sig(Signal.JUKEBOX_DISC);
                b.lecternBooks += r.sig(Signal.LECTERN_BOOK);
                b.brewingBottles += r.sig(Signal.BREWING_BOTTLE);
                b.respawnAnchors += r.sig(Signal.ANCHOR_CHARGED);
                b.filledComposters += r.sig(Signal.COMPOSTER_FILLED);
                b.crops += r.sig(Signal.CROPS);
                b.cropsMature += r.sig(Signal.CROPS_MATURE);
                b.beds += r.sig(Signal.BED_PART);
                b.tnt += r.sig(Signal.TNT);
                b.fire += r.sig(Signal.FIRE);
                b.traps += r.sig(Signal.TRAP);
                b.ironDoors += r.sig(Signal.IRON_DOOR_PART);
                b.obsidian += r.sig(Signal.OBSIDIAN);
                b.beacons += r.sig(Signal.BEACON);
                b.valuableBlocks += r.sig(Signal.VALUABLE);
                b.copperFresh += r.sig(Signal.COPPER_FRESH) + r.sig(Signal.COPPER_EXPOSED);
                b.copperAged += r.sig(Signal.COPPER_WEATHERED) + r.sig(Signal.COPPER_OXIDIZED);
                b.copperWaxed += r.sig(Signal.COPPER_WAXED);
                for (String sign : r.signs) if (b.signs.size() < 12 && !b.signs.contains(sign)) b.signs.add(sign);
                for (String head : r.heads) if (!b.heads.contains(head)) b.heads.add(head);
                EntityIntel e = r.entities;
                b.framesFull += e.framesFull; b.framesEmpty += e.framesEmpty;
                b.standsDressed += e.standsDressed; b.standsBare += e.standsBare;
                b.drops += e.drops; b.xpOrbs += e.xpOrbs; b.villagers += e.villagers;
                for (String o : e.owners) { b.pets++; if (!b.owners.contains(o)) b.owners.add(o); }
                for (String n : e.named) if (b.named.size() < 10 && !b.named.contains(n)) b.named.add(n);
                for (String p : e.players) if (!b.playersHere.contains(p)) b.playersHere.add(p);
                b.lootDrops += e.lootDrops; b.debrisDrops += e.debrisDrops; b.tntEntities += e.tntEntities; b.withers += e.withers;
                b.crystals += r.entityCounts[BaseScanner.END_CRYSTAL.id];
                if (cx >= b.minChunkX && cx <= b.maxChunkX && cz >= b.minChunkZ && cz <= b.maxChunkZ) b.craters += r.sig(Signal.CRATER);
                b.lavaFlows += r.sig(Signal.LAVA_FLOW);
                b.portals += r.sig(Signal.NETHER_PORTAL) + r.sig(Signal.PORTAL_FRAME);
                for (Feature f : BaseScanner.FEATURES)
                    if (WORKSTATIONS.contains(f.name)) b.workstations += r.blockCounts[f.id];
            }
        // The amethyst clock looks a little further out: geodes are rarely right under a base.
        for (int cx = b.minChunkX - 4; cx <= b.maxChunkX + 4; cx++)
            for (int cz = b.minChunkZ - 4; cz <= b.maxChunkZ + 4; cz++) {
                ChunkReport r = reports.get(key(cx, cz, b.layer));
                if (r != null) { faces += r.amethystFaces; stageSum += r.amethystStageSum; budding += r.amethystBudding; }
            }
        b.doubleChests /= 2;
        b.beds /= 2;
        b.ironDoors /= 2;
        b.storageSlots = b.singleChests * 27 + b.doubleChests * 54 + b.trappedChests * 27 + b.barrels * 27
                + b.shulkers * 27 + b.hoppers * 5 + b.dispensers * 9 + b.crafters * 9;
        for (Structure st : nearby) b.nearbyStructures.add(pretty(st));
        if (faces >= 6) { b.amethystFaces = faces; b.amethystHours = AmethystClock.hours(faces, stageSum, budding); }
        if (!nearby.isEmpty()) b.notes.add("Overlaps a natural " + String.join(", ", b.nearbyStructures).toLowerCase()
                + "; its own blocks are ignored");

        for (String sign : b.signs) if (RAIDER_SIGN.matcher(sign).find() && b.raiderSigns.size() < 3) b.raiderSigns.add(sign);
        scoreUnraided(b, dim);
        scoreActivity(b);
    }

    private static final Set<String> WORKSTATIONS = Set.of("crafting table", "furnace", "enchanting table", "anvil", "brewing stand");
    /** What raiders write on signs they leave behind. */
    private static final java.util.regex.Pattern RAIDER_SIGN = java.util.regex.Pattern.compile(
            "\\b(raided|raid(?:ed)? by|griefed|grief(?:ed)? by|rekt|e+z+|gg+|was here|were here|destroyed|bombed|nuked|looted|"
                    + "cope|seethe|stay mad|l+o+l+|rip|owned|cleaned)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Unraided likelihood. Raid damage is weighed first and dominates: a bombed base still has chests
     * and builds standing, so whatever is still "intact" can never outvote craters, crystals, spilled
     * loot or raider signs. Without damage it falls back to what is still worth stealing.
     */
    private static void scoreUnraided(Base b, Dim dim) {
        List<String> why = b.unraidedWhy;
        float damage = 0, intact = 0;
        // ---- damage evidence
        if (!b.raiderSigns.isEmpty()) { damage += 40; why.add("- Raider sign: \"" + clip(b.raiderSigns.getFirst(), 40) + "\""); }
        if (b.craters >= 8) { damage += Math.min(20, 6 + b.craters * 0.4f); why.add("- Possible explosion craters in the ground (" + b.craters + " columns)"); }
        if (b.crystals > 0 && dim != Dim.END) { damage += Math.min(30, 12 + b.crystals * 6f); why.add("- " + b.crystals + " end crystal" + (b.crystals == 1 ? "" : "s") + " placed (crystal bombing)"); }
        if (b.tnt > 0 || b.tntEntities > 0) { damage += Math.min(30, 10 + (b.tnt + b.tntEntities * 3) * 2f); why.add("- TNT here (" + (b.tnt + b.tntEntities) + ")"); }
        if (b.withers > 0) { damage += 25; why.add("- Wither or wither skulls in the area"); }
        if (b.lootDrops > 0) { damage += Math.min(30, 15 + b.lootDrops * 3f); why.add("- " + b.lootDrops + " valuable" + (b.lootDrops == 1 ? "" : "s") + " lying on the ground (spilled loot)"); }
        if (b.debrisDrops >= 12) { damage += Math.min(20, b.debrisDrops * 0.5f); why.add("- " + b.debrisDrops + " block items scattered (explosion debris)"); }
        if (b.lavaFlows >= 6) { damage += Math.min(20, b.lavaFlows * 0.4f); why.add("- Lava poured over the area (lava cast)"); }
        if (b.fire > 0) { damage += Math.min(12, 4 + b.fire * 2f); why.add("- Fire burning"); }
        if (b.obsidian >= 16 && b.portals == 0) { damage += Math.min(12, b.obsidian * 0.2f); why.add("- " + b.obsidian + " loose obsidian (crystal platforms)"); }
        if (b.workstations >= 2 && b.storageSlots < 27 && b.shulkers == 0) { damage += 20; why.add("- Workstations left but the storage is gone (stripped)"); }
        if (b.framesEmpty > 0) { damage += Math.min(15, b.framesEmpty * 3f); why.add("- " + b.framesEmpty + " empty item frame" + (b.framesEmpty == 1 ? "" : "s")); }
        if (b.standsBare > 0 && b.standsDressed == 0) { damage += Math.min(8, b.standsBare * 2f); why.add("- Armor stands stripped"); }
        // ---- still worth stealing
        if (b.shulkers > 0) { intact += Math.min(25, b.shulkers * 3f); why.add("+ " + b.shulkers + " shulker box" + (b.shulkers == 1 ? "" : "es") + " still placed"); }
        if (b.valuableBlocks > 0) { intact += Math.min(20, b.valuableBlocks * 4f); why.add("+ " + b.valuableBlocks + " beacon/valuable block" + (b.valuableBlocks == 1 ? "" : "s") + " intact"); }
        if (b.framesFull > 0) { intact += Math.min(10, b.framesFull * 1.5f); why.add("+ " + b.framesFull + " item frame" + (b.framesFull == 1 ? "" : "s") + " still holding items"); }
        if (b.standsDressed > 0) { intact += Math.min(8, b.standsDressed * 2f); why.add("+ " + b.standsDressed + " armor stand" + (b.standsDressed == 1 ? "" : "s") + " still wearing armor"); }
        int keepsakes = b.lecternBooks + b.jukeboxDiscs + b.brewingBottles;
        if (keepsakes > 0) { intact += Math.min(6, keepsakes * 1.5f); why.add("+ Books, discs or potions left in place"); }
        if (b.storageSlots >= 270 && damage < 15) { intact += 8; why.add("+ " + b.storageSlots + " storage slots still standing"); }
        if (Math.abs(b.x) > 50_000 || Math.abs(b.z) > 50_000) { intact += 5; why.add("+ Far from spawn"); }
        if (b.y < 20) { intact += 3; why.add("+ Built underground"); }

        float score = 50 + intact - damage * 1.3f;
        if (damage >= 30) score = Math.min(score, 20);         // clear raid evidence outweighs anything still standing
        else if (damage >= 15) score = Math.min(score, 45);
        b.unraided = Math.round(Math.max(3, Math.min(97, score)));
        b.raidDamage = Math.round(damage);
        b.raidLabel = damage >= 50 ? "Bombed" : damage >= 30 ? "Raided" : damage >= 15 ? "Possibly raided"
                : b.unraided >= 70 ? "Looks intact" : "No raid signs";
    }

    private static String clip(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "\u2026"; }

    private static void scoreActivity(Base b) {
        float score = 30;
        List<String> why = b.activityWhy;
        if (!b.playersHere.isEmpty()) {
            score += 70;
            why.add((b.raidDamage >= 30 ? "Players here now (raiders?): " : "Players here now: ") + String.join(", ", b.playersHere));
        }
        if (b.litFurnaces > 0) { score += 35; why.add(b.litFurnaces + " furnace" + (b.litFurnaces == 1 ? " is" : "s are") + " burning (used within minutes)"); }
        boolean debris = b.lootDrops > 0 || b.debrisDrops >= 12;
        if (debris) why.add(b.drops + " items on the ground look like raid debris, not someone living here");
        else if (b.drops > 0) { score += 20; why.add(b.drops + " dropped item" + (b.drops == 1 ? "" : "s") + " (they vanish after 5 loaded minutes)"); }
        if (b.xpOrbs > 0 && !debris) { score += 10; why.add("XP orbs on the ground"); }
        if (b.poweredRedstone > 4) { score += 10; why.add("Redstone machines running"); }
        if (b.litCandles > 0) { score += 5; why.add("Candles lit"); }
        if (b.crops >= 16) {
            float ripe = b.cropsMature / (float) b.crops;
            if (ripe >= 0.95f) { score -= 15; why.add("All " + b.crops + " crops ripe and unharvested"); }
            else if (ripe < 0.5f) { score += 15; why.add("Crops mostly growing (recently harvested or replanted)"); }
        }
        if (b.amethystHours >= 0) {
            // Grown crystals = players stayed here for hours: strong sign of a lived-in base.
            if (b.amethystHours >= 5) { score += 15; why.add("Amethyst heavily grown: 5h+ of player presence (lived-in spot)"); }
            else if (b.amethystHours >= 1) { score += Math.round(b.amethystHours * 3); why.add(String.format("Amethyst grown: about %.1fh of player presence", b.amethystHours)); }
            else why.add(String.format("Amethyst barely grown: about %.1fh of player presence", b.amethystHours));
            if (b.amethystHours < 0.4f) score -= 5;
        }
        if (b.copperAged > b.copperFresh && b.copperAged >= 8) { score -= 10; why.add("Copper has weathered (base is old)"); }
        else if (b.copperFresh >= 8 && b.copperWaxed < b.copperFresh) { score += 5; why.add("Fresh unwaxed copper (recent build)"); }
        if (b.respawnAnchors > 0 || b.beds > 0) why.add((b.beds > 0 ? b.beds + " bed" + (b.beds == 1 ? "" : "s") : "")
                + (b.beds > 0 && b.respawnAnchors > 0 ? ", " : "") + (b.respawnAnchors > 0 ? "charged respawn anchor" : "") + " (spawn points)");
        b.activity = Math.round(Math.max(0, Math.min(100, score)));
        b.activityLabel = activityLabel(b);
        if (why.isEmpty()) why.add("No live signals; status is a guess");
    }

    static String pretty(Structure s) {
        String n = s.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Natural structures, plus whatever shows whether players already found them. */
    private List<Poi> findPois(Minecraft mc) {
        if (!structures.get()) return List.of();
        Map<Structure, List<ChunkReport>> byType = new java.util.EnumMap<>(Structure.class);
        for (ChunkReport r : reports.values()) for (Structure st : r.structures) byType.computeIfAbsent(st, k -> new ArrayList<>()).add(r);
        List<Poi> out = new ArrayList<>();
        for (Map.Entry<Structure, List<ChunkReport>> entry : byType.entrySet()) for (int layer = 0; layer < 2; layer++) {
            final int l = layer;
            List<ChunkReport> pool = new ArrayList<>(entry.getValue());
            pool.removeIf(r -> r.layer != l);
            while (!pool.isEmpty()) {
                // group chunks of the same structure type that touch (within 3 chunks)
                List<ChunkReport> group = new ArrayList<>();
                group.add(pool.removeLast());
                for (int i = 0; i < group.size(); i++) {
                    ChunkReport g = group.get(i);
                    pool.removeIf(o -> {
                        boolean near = Math.abs(o.chunkX - g.chunkX) <= 3 && Math.abs(o.chunkZ - g.chunkZ) <= 3;
                        if (near) group.add(o);
                        return near;
                    });
                }
                out.add(poi(entry.getKey(), group));
            }
        }
        out.sort(java.util.Comparator.comparingDouble(p -> mc.player == null ? 0 : mc.player.distanceToSqr(p.x, mc.player.getY(), p.z)));
        return List.copyOf(out);
    }

    private Poi poi(Structure st, List<ChunkReport> group) {
        Poi p = new Poi();
        p.layer = group.getFirst().layer;
        p.type = pretty(st);
        long sx = 0, sz = 0;
        int eyes = 0, frames = 0, endPortal = 0, elytra = 0, villagers = 0, spawners = 0, chestsHere = 0, playerScore = 0;
        for (ChunkReport r : group) {
            sx += r.chunkX * 16 + 8; sz += r.chunkZ * 16 + 8;
            eyes += r.sig(Signal.PORTAL_FRAME_EYE); frames += r.sig(Signal.PORTAL_FRAME); endPortal += r.sig(Signal.END_PORTAL);
            elytra += r.entities.elytraFrames; villagers += r.entities.villagers; spawners += r.sig(Signal.SPAWNER);
            chestsHere += r.sig(Signal.CHEST_SINGLE) + r.sig(Signal.CHEST_HALF);
            // Player blocks the structure cannot explain (scored without suppression for this structure).
            playerScore += r.sig(Signal.SHULKER) * 10 + r.sig(Signal.HOPPER) * 2 + r.sig(Signal.LIT_FURNACE) * 5 + r.sig(Signal.NETHER_PORTAL);
        }
        p.x = (int) (sx / group.size()); p.z = (int) (sz / group.size());
        p.y = p.layer == BaseScanner.DEEP ? -40
                : level == null ? 64 : level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, p.x, p.z);
        p.color = 0xFFB0B8D0;
        switch (st) {
            case STRONGHOLD -> {
                if (frames > 0) p.notes.add("End portal room: " + eyes + "/" + frames + " eyes placed");
                if (endPortal > 0) { p.notes.add("Portal is ACTIVE: players have been here"); p.touched = true; }
                else if (eyes > 0 && frames > 0) { p.notes.add("Some eyes placed: possibly visited"); p.touched = true; }
            }
            case END_CITY -> {
                if (elytra > 0) p.notes.add("Elytra still in its frame: ship NOT looted");
                else p.notes.add("No elytra frame seen (looted, or ship not loaded)");
                p.touched = elytra == 0;
            }
            case VILLAGE -> {
                p.notes.add(villagers + " villager" + (villagers == 1 ? "" : "s") + " seen");
                if (villagers == 0) p.notes.add("No villagers: zombie raid or players moved them");
            }
            case DUNGEON -> p.notes.add(spawners + " spawner" + (spawners == 1 ? "" : "s") + ", " + chestsHere + " chest" + (chestsHere == 1 ? "" : "s")
                    + (chestsHere == 0 ? " (chest gone: looted)" : ""));
            default -> {}
        }
        if (st == Structure.DUNGEON && chestsHere == 0) p.touched = true;
        if (playerScore >= 10) { p.notes.add("Player-made blocks inside: taken over or used by players"); p.touched = true; }
        if (p.touched) p.color = 0xFFFFC94A;
        return p;
    }

    // ------------------------------------------------------------------ focus and tracking
    /** Pinned base if still present, otherwise the base closest to the crosshair (within 25 degrees), otherwise the nearest. */
    public Base focused(Minecraft mc) {
        List<Base> list = bases();
        if (list.isEmpty() || mc.player == null) return null;
        if (trackedX != Integer.MIN_VALUE) {
            for (Base b : list) {
                long dx = b.x - trackedX, dz = b.z - trackedZ;
                if (dx * dx + dz * dz < 64 * 64) return b;
            }
        }
        Base best = null, nearest = null;
        double bestAngle = 25, nearestDist = Double.MAX_VALUE;
        for (Base b : list) {
            double angle = Math.abs(relativeYaw(mc, b));
            if (angle < bestAngle) { bestAngle = angle; best = b; }
            double d = mc.player.distanceToSqr(b.x, mc.player.getY(), b.z);
            if (d < nearestDist) { nearestDist = d; nearest = b; }
        }
        return best != null ? best : nearest;
    }

    public boolean isTracked(Base b) {
        if (trackedX == Integer.MIN_VALUE) return false;
        long dx = b.x - trackedX, dz = b.z - trackedZ;
        return dx * dx + dz * dz < 64 * 64;
    }

    public void track(Base b) {
        if (b == null || isTracked(b)) { trackedX = Integer.MIN_VALUE; return; }
        trackedX = b.x;
        trackedZ = b.z;
    }

    public List<Poi> pois() {
        int layer = playerLayer();
        List<Poi> view = new ArrayList<>();
        for (Poi p : pois) if (p.layer == layer) view.add(p);
        return view;
    }
    public boolean showIntelCard() { return isEnabled() && hudIntel.get(); }
    public boolean showPinnedCards() { return isEnabled() && labels.get(); }
    public String cardSize() { return cardSize.get(); }

    private static String classify(float[] c, float total, int[] counts, float amethyst) {
        float storage = c[Category.STORAGE.ordinal()] / total, valuable = c[Category.VALUABLE.ordinal()] / total;
        float redstone = c[Category.REDSTONE.ordinal()] / total, portal = c[Category.PORTAL.ordinal()] / total;
        float build = c[Category.BUILD.ordinal()] / total, farm = c[Category.FARM.ordinal()] / total;
        int shulkers = counts[0];
        if (shulkers >= 8 || (storage > 0.6f && total >= 200)) return "Stash";
        if (amethyst / total > 0.5f) return "Activity hotspot";
        int strong = 0;
        for (float v : c) if (v / total > 0.12f) strong++;
        if (strong >= 3 || valuable > 0.4f) return "Base";
        if (redstone > 0.5f) return "Redstone build";
        if (farm > 0.5f) return "Farm";
        if (portal > 0.6f) return "Portal";
        if (build > 0.5f) return "Build";
        if (storage > 0.4f) return "Storage";
        return "Player activity";
    }

    private static int typeColor(String type) {
        return switch (type) {
            case "Stash" -> 0xFFFFB347;
            case "Base" -> 0xFF4AF5FF;
            case "Redstone build" -> 0xFFFF4D4D;
            case "Farm" -> 0xFF7CFF7C;
            case "Portal" -> 0xFFB76BFF;
            case "Build" -> 0xFF7CC8FF;
            case "Storage" -> 0xFFFFD27A;
            case "Activity hotspot" -> 0xFFC58BFF;
            default -> 0xFFFFE08A;
        };
    }

    /** "23 shulker boxes, 4 beacons, 120 obsidian" from the three largest contributors. */
    private static String summarize(float[] feats, int[] counts) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < feats.length; i++) if (feats[i] > 0) order.add(i);
        order.sort((a, b) -> Float.compare(feats[b], feats[a]));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, order.size()); i++) {
            Feature f = BaseScanner.FEATURES.get(order.get(i));
            int n = counts[f.id];
            if (sb.length() > 0) sb.append(", ");
            if (f == BaseScanner.AMETHYST_GROWTH) { sb.append("player-grown amethyst"); continue; }
            sb.append(n).append(' ').append(n == 1 ? f.name : f.plural);
        }
        return sb.toString();
    }

    private static String compass(Minecraft mc, Base b) {
        double dx = b.x + .5 - mc.player.getX(), dz = b.z + .5 - mc.player.getZ();
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return names[(int) Math.floorMod(Math.round(angle / 45.0), 8L)];
    }

    private static Dim dimOf(ClientLevel level) {
        if (level.dimension() == Level.NETHER) return Dim.NETHER;
        if (level.dimension() == Level.END) return Dim.END;
        return Dim.OVERWORLD;
    }

    private void copyBest() {
        List<Base> list = bases();
        if (list.isEmpty()) { Notifications.warn("Base Finder", "Nothing found in this layer yet"); return; }
        Base b = list.getFirst();
        Minecraft.getInstance().keyboardHandler.setClipboard(b.x + " " + b.y + " " + b.z);
        Notifications.success("Base Finder", "Copied " + b.type + " at " + b.x + " " + b.y + " " + b.z);
    }

    // ------------------------------------------------------------------ public views
    /** Bases in the layer the player is in (deepslate below y=0, surface above). */
    public List<Base> bases() {
        List<Base> all = bases;
        int layer = playerLayer();
        Object[] cached = layerView.get();
        if (cached != null && cached[0] == all && (int) cached[1] == layer) {
            @SuppressWarnings("unchecked") List<Base> v = (List<Base>) cached[2];
            return v;
        }
        List<Base> view = new ArrayList<>();
        for (Base b : all) if (b.layer == layer) view.add(b);
        view = List.copyOf(view);
        layerView.set(new Object[]{all, layer, view});
        return view;
    }

    /** Human-readable name of the layer currently shown. */
    public String layerName() { return playerLayer() == BaseScanner.DEEP ? "Deepslate (below y 0)" : "Surface (y 0 and up)"; }
    public boolean showHud() { return isEnabled() && hudList.get(); }
    public int queued() { return queue.size(); }
    public int scanned() { return scannedTotal; }

    @Override public String hudInfo() {
        List<Base> list = bases();
        return list.isEmpty() ? (queue.isEmpty() ? "0" : "Scanning") : Integer.toString(list.size());
    }

    // ------------------------------------------------------------------ world rendering
    public static void renderGizmos(LevelRenderer renderer) {
        BaseFinderModule m = INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        List<Base> list = m.bases();
        if (!m.isEnabled() || mc.player == null || mc.level == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 eye = mc.player.getEyePosition(partial);
        Vec3 look = mc.player.getViewVector(partial);
        boolean onTop = m.throughWalls.get();
        double t = System.currentTimeMillis() / 1000.0;
        int minY = mc.level.getMinY(), maxY = mc.level.getMaxY();
        int shown = 0, keyBudget = 220;
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            for (Base b : list) {
                if (shown++ >= (int) m.maxShown.get()) break;
                int color = b.color;
                float pulse = (float) (0.65 + 0.35 * Math.sin(t * 2.4 + b.x * 0.01));
                Vec3 center = new Vec3(b.x + .5, b.y + .5, b.z + .5);
                if (m.beams.get()) {
                    int glow = ((int) (0x50 * pulse) << 24) | (color & 0xFFFFFF);
                    var outer = Gizmos.line(new Vec3(center.x, minY, center.z), new Vec3(center.x, maxY, center.z), glow, 9f);
                    var core = Gizmos.line(new Vec3(center.x, minY, center.z), new Vec3(center.x, maxY, center.z),
                            0xF0000000 | (lighten(color) & 0xFFFFFF), 2.5f);
                    if (onTop) { outer.setAlwaysOnTop(); core.setAlwaysOnTop(); }
                }
                if (m.outlines.get()) {
                    AABB area = new AABB(b.minChunkX * 16, b.minY - 1, b.minChunkZ * 16,
                            b.maxChunkX * 16 + 16, b.maxY + 2, b.maxChunkZ * 16 + 16);
                    var box = Gizmos.cuboid(area, GizmoStyle.stroke(0xB0000000 | (color & 0xFFFFFF), 1.5f));
                    if (onTop) box.setAlwaysOnTop();
                }
                if (m.highlight.get()) {
                    for (Key key : b.keys) {
                        if (keyBudget <= 0) break;
                        if (eye.distanceToSqr(key.pos().getX(), key.pos().getY(), key.pos().getZ()) > 128 * 128) continue;
                        keyBudget--;
                        int c = key.feature().category.color;
                        var box = Gizmos.cuboid(key.pos(), GizmoStyle.strokeAndFill(0xE0000000 | (c & 0xFFFFFF), 1.5f,
                                0x28000000 | (c & 0xFFFFFF)));
                        if (onTop) box.setAlwaysOnTop();
                    }
                }
                if (m.tracers.get() || m.isTracked(b)) {
                    Gizmos.line(eye.add(look.scale(2)), center, 0xD0000000 | (color & 0xFFFFFF), 1.5f).setAlwaysOnTop();
                }
            }
        }
        if (m.structures.get()) {
            try (var ignored = Gizmos.withCollector(collector)) {
                int labelled = 0;
                for (Poi p : m.pois()) {   // sorted nearest first
                    Vec3 base = new Vec3(p.x + .5, p.y, p.z + .5);
                    if (eye.distanceToSqr(base) > 400 * 400) continue;
                    Gizmos.line(base, base.add(0, 48, 0), 0x90000000 | (p.color & 0xFFFFFF), 2f).setAlwaysOnTop();
                    // Only the few nearest structures get a (small) label, so they never drown out bases.
                    if (m.labels.get() && labelled++ < 5 && eye.distanceToSqr(base) < 200 * 200) {
                        Vec3 to = base.add(0, 50, 0).subtract(eye);
                        double dist = to.length(), shownDist = Math.min(dist, 48);
                        Vec3 pos = eye.add(to.normalize().scale(shownDist));
                        float scale = (float) (TextGizmo.Style.DEFAULT_SCALE * Math.max(1.5, shownDist / 5.0));
                        Gizmos.billboardText(p.type + "  " + Math.round(dist) + "m", pos,
                                TextGizmo.Style.forColorAndCentered(p.color).withScale(scale)).setAlwaysOnTop();
                    }
                }
            }
        }
        renderer.addMainThreadGizmos(collector.drainGizmos());
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 90), g = Math.min(255, ((c >> 8) & 255) + 90), b = Math.min(255, (c & 255) + 90);
        return (r << 16) | (g << 8) | b;
    }

    /** Horizontal direction to a base relative to where the player faces, in degrees (0 = straight ahead). */
    public static float relativeYaw(Minecraft mc, Base b) {
        double dx = b.x + .5 - mc.player.getX(), dz = b.z + .5 - mc.player.getZ();
        float target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        return net.minecraft.util.Mth.wrapDegrees(target - mc.player.getYRot());
    }

    static BlockPos posOf(Base b) { return new BlockPos(b.x, b.y, b.z); }
}
