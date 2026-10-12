package unblonded.anthrax.modules.visual;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;
import unblonded.anthrax.modules.client.FriendsModule;
import unblonded.anthrax.modules.combat.TotemPops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Nearby players sorted closest first, drawn by the HUD as collapsible intel cards: vitals, telemetry,
 * live status flags, every equipped item with durability and enchantments, effects and a threat rating.
 * Open chat and click a row to expand or collapse it.
 */
public final class PlayerListModule extends FeatureModule {
    public static final PlayerListModule INSTANCE = new PlayerListModule();

    /** One equipped item: what it is, how worn, and its enchantments in short form ("Prot IV"). */
    public record Gear(EquipmentSlot slot, ItemStack stack, String name, int damage, int maxDamage,
                       List<String> enchants, int enchantLevels) {
        public float durability() { return maxDamage <= 0 ? -1 : 1f - damage / (float) maxDamage; }
    }

    public record Entry(UUID id, String name, AbstractClientPlayer player, double distance, float health, float absorption,
                        float maxHealth, int armor, float toughness, int pops, int ping, String gameMode,
                        int x, int y, int z, double dy, double speed, float bearing, boolean lookingAtMe,
                        boolean visible, boolean friend, List<String> flags, List<Gear> gear, List<String> effects,
                        int threat, ItemStack mainHand, ItemStack offHand) {
        public float healthFrac() { return maxHealth <= 0 ? 0 : Math.min(1f, health / maxHealth); }
        public String threatLabel() { return threat >= 75 ? "EXTREME" : threat >= 50 ? "HIGH" : threat >= 25 ? "MEDIUM" : "LOW"; }
    }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private final Setting.Mode expand = setting(Setting.Mode.standalone("Expand", "Closest", "Click", "Closest", "All", "None"));
    private final Setting.Bool showFriends = setting(Setting.Bool.standalone("Show friends", true));
    private final Setting.Bool showEmpty = setting(Setting.Bool.standalone("Show when empty", true));
    private final Setting.Bool showLoadout = setting(Setting.Bool.standalone("Loadout and enchants", true));
    private final Setting.Bool showTelemetry = setting(Setting.Bool.standalone("Telemetry", true));
    private final Setting.Bool freeze = setting(Setting.Bool.standalone("Freeze list", false));
    private final Setting.Num maxEntries = setting(Setting.Num.standalone("Max players", 8, 1, 20, 1));
    private final Setting.Num range = setting(Setting.Num.standalone("Range", 128, 8, 512, 8));
    private List<Entry> current = List.of();
    private List<Entry> frozen = List.of();
    private boolean wasFrozen;
    /** Rows the user opened or closed by clicking; these override the Expand mode. */
    private final Set<UUID> opened = new HashSet<>(), closed = new HashSet<>();

    private PlayerListModule() {
        super("Show Player List", "Closest-first player intel: health, armor, enchants, items, telemetry", "Visuals", false);
    }

    @Override public void tick(Minecraft client) {
        if (client.player == null || client.level == null) { current = List.of(); frozen = List.of(); wasFrozen = false; return; }
        List<Entry> entries = new ArrayList<>();
        double rangeSq = range.get() * range.get();
        for (Player other : client.level.players()) {
            if (other == client.player || other.isRemoved() || other.isSpectator()) continue;
            if (!(other instanceof AbstractClientPlayer p)) continue;
            if (!showFriends.get() && FriendsModule.isFriend(other)) continue;
            double distSq = client.player.distanceToSqr(other);
            if (distSq > rangeSq) continue;
            entries.add(read(client, p, Math.sqrt(distSq)));
        }
        entries.sort(Comparator.comparingDouble(Entry::distance));
        if (entries.size() > (int) maxEntries.get()) entries = entries.subList(0, (int) maxEntries.get());
        current = List.copyOf(entries);
        boolean freezeNow = freeze.get();
        if (freezeNow && !wasFrozen) frozen = current;
        else if (!freezeNow) frozen = List.of();
        wasFrozen = freezeNow;
    }

    private static Entry read(Minecraft mc, AbstractClientPlayer p, double distance) {
        var me = mc.player;
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(p.getUUID());
        int ping = info == null ? -1 : info.getLatency();
        String mode = info == null || info.getGameMode() == null ? "?" : info.getGameMode().getName().toUpperCase(Locale.ROOT);

        double dx = p.getX() - me.getX(), dz = p.getZ() - me.getZ();
        float yawTo = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float bearing = Mth.wrapDegrees(yawTo - me.getYRot());
        double speed = Math.sqrt((p.getX() - p.xo) * (p.getX() - p.xo) + (p.getZ() - p.zo) * (p.getZ() - p.zo)) * 20;
        Vec3 toMe = me.getEyePosition().subtract(p.getEyePosition()).normalize();
        boolean looking = p.getViewVector(1f).dot(toMe) > 0.985;   // within ~10 degrees

        List<String> flags = new ArrayList<>();
        if (p.isFallFlying()) flags.add("ELYTRA");
        if (p.isSprinting()) flags.add("SPRINT");
        if (p.isCrouching()) flags.add("SNEAK");
        if (p.isBlocking()) flags.add("BLOCK");
        else if (p.isUsingItem()) flags.add("USING");
        if (p.isInWater()) flags.add("WATER");
        if (p.isOnFire()) flags.add("FIRE");
        if (p.isInvisible()) flags.add("INVIS");
        if (p.isPassenger()) flags.add("RIDING");
        if (p.hurtTime > 0) flags.add("HIT");
        if (!p.onGround() && !p.isFallFlying() && !p.isInWater()) flags.add("AIR");
        if (looking) flags.add("WATCHING");

        List<Gear> gear = new ArrayList<>();
        int enchantTotal = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            List<String> enchants = new ArrayList<>();
            int levels = 0;
            for (Object2IntMap.Entry<Holder<Enchantment>> e : stack.getEnchantments().entrySet()) {
                enchants.add(shortName(e.getKey()) + (e.getIntValue() > 1 || e.getKey().value().getMaxLevel() > 1 ? " " + roman(e.getIntValue()) : ""));
                levels += e.getIntValue();
            }
            enchantTotal += levels;
            gear.add(new Gear(slot, stack.copy(), stack.getHoverName().getString(),
                    stack.isDamageableItem() ? stack.getDamageValue() : 0, stack.isDamageableItem() ? stack.getMaxDamage() : 0,
                    List.copyOf(enchants), levels));
        }

        List<String> effects = new ArrayList<>();
        for (MobEffectInstance effect : p.getActiveEffects()) {
            int secs = effect.getDuration() / 20;
            effects.add(effect.getEffect().value().getDisplayName().getString()
                    + (effect.getAmplifier() > 0 ? " " + roman(effect.getAmplifier() + 1) : "")
                    + (effect.isInfiniteDuration() ? "" : " " + secs / 60 + ":" + String.format("%02d", secs % 60)));
        }

        float toughness = (float) p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        int threat = p.getArmorValue() * 2 + Math.round(toughness * 2) + Math.min(25, enchantTotal);
        if (off.is(Items.TOTEM_OF_UNDYING) || main.is(Items.TOTEM_OF_UNDYING)) threat += 8;
        if (main.is(Items.END_CRYSTAL) || off.is(Items.END_CRYSTAL) || main.is(Items.RESPAWN_ANCHOR)) threat += 15;
        if (main.is(Items.MACE)) threat += 12;
        threat = Math.min(100, threat);

        return new Entry(p.getUUID(), p.getName().getString(), p, distance,
                p.getHealth(), p.getAbsorptionAmount(), p.getMaxHealth(), p.getArmorValue(), toughness,
                TotemPops.pops(p), ping, mode, p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.getY() - me.getY(), speed,
                bearing, looking, me.hasLineOfSight(p), FriendsModule.isFriend(p), List.copyOf(flags), List.copyOf(gear),
                List.copyOf(effects), threat, main.copy(), off.copy());
    }

    /** "protection" -> "Prot", "fire_aspect" -> "FireAsp", unknown ids title-cased. */
    private static String shortName(Holder<Enchantment> holder) {
        String id = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
        return switch (id) {
            case "protection" -> "Prot";
            case "fire_protection" -> "FireProt";
            case "blast_protection" -> "BlastProt";
            case "projectile_protection" -> "ProjProt";
            case "feather_falling" -> "FF";
            case "unbreaking" -> "Unb";
            case "mending" -> "Mend";
            case "sharpness" -> "Sharp";
            case "smite" -> "Smite";
            case "bane_of_arthropods" -> "Bane";
            case "fire_aspect" -> "FireAsp";
            case "knockback" -> "KB";
            case "looting" -> "Loot";
            case "sweeping_edge" -> "Sweep";
            case "efficiency" -> "Eff";
            case "fortune" -> "Fort";
            case "silk_touch" -> "Silk";
            case "power" -> "Pow";
            case "punch" -> "Punch";
            case "infinity" -> "Inf";
            case "flame" -> "Flame";
            case "thorns" -> "Thorns";
            case "respiration" -> "Resp";
            case "aqua_affinity" -> "Aqua";
            case "depth_strider" -> "Depth";
            case "frost_walker" -> "Frost";
            case "soul_speed" -> "Soul";
            case "swift_sneak" -> "Swift";
            case "density" -> "Dens";
            case "breach" -> "Breach";
            case "wind_burst" -> "Wind";
            case "binding_curse" -> "Bind";
            case "vanishing_curse" -> "Vanish";
            default -> {
                StringBuilder sb = new StringBuilder();
                for (String part : id.split("_")) if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
                yield sb.toString();
            }
        };
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> Integer.toString(n);
        };
    }

    public List<Entry> entries() { return freeze.get() ? frozen : current; }

    public boolean showWhenEmpty() { return showEmpty.get(); }
    public boolean showLoadout() { return showLoadout.get(); }
    public boolean showTelemetry() { return showTelemetry.get(); }
    public int range() { return (int) range.get(); }
    public String expandMode() { return expand.get(); }

    /** Whether the card at this rank (0 = closest) is open: a click wins, otherwise the Expand mode decides. */
    public boolean expanded(Entry e, int rank) {
        if (opened.contains(e.id())) return true;
        if (closed.contains(e.id())) return false;
        return switch (expand.get()) {
            case "All" -> true;
            case "Closest" -> rank == 0;
            default -> false;
        };
    }

    public void toggle(Entry e, int rank) {
        boolean open = !expanded(e, rank);
        (open ? opened : closed).add(e.id());
        (open ? closed : opened).remove(e.id());
    }

    @Override public String hudInfo() { return Integer.toString(current.size()); }
}
