package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Small helpers shared by the cosmetics: client-only particles, sounds and entities. */
public final class Fx {
    private Fx() {}
    static final Random RND = new Random();
    /** Ids for client-only entities (pets, lightning): far below anything a server hands out. */
    private static int nextId = -0x3A000000;

    static void particle(ParticleOptions p, Vec3 at, double vx, double vy, double vz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.addParticle(p, true, true, at.x, at.y, at.z, vx, vy, vz);
    }

    static void particle(ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.addParticle(p, true, true, x, y, z, vx, vy, vz);
    }

    static DustParticleOptions dust(int rgb, float scale) { return new DustParticleOptions(rgb & 0xFFFFFF, Math.max(0.05f, Math.min(4f, scale))); }

    /** Bursts {@code count} particles from a point with a random spread and speed. */
    static void burst(ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        for (int i = 0; i < count; i++) {
            double vx = (RND.nextDouble() - 0.5) * 2 * speed, vy = (RND.nextDouble() - 0.3) * 2 * speed, vz = (RND.nextDouble() - 0.5) * 2 * speed;
            particle(p, at.add((RND.nextDouble() - 0.5) * spread, (RND.nextDouble() - 0.5) * spread, (RND.nextDouble() - 0.5) * spread), vx, vy, vz);
        }
    }

    /** A named particle choice used by several modules' style settings; colour is used by the dust styles. */
    static ParticleOptions named(String name, int rgb) {
        return switch (name) {
            case "Crit" -> ParticleTypes.CRIT;
            case "Magic" -> ParticleTypes.ENCHANTED_HIT;
            case "Hearts" -> ParticleTypes.HEART;
            case "Sparks" -> ParticleTypes.ELECTRIC_SPARK;
            case "Flames" -> ParticleTypes.FLAME;
            case "Soul flames" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "Souls" -> ParticleTypes.SOUL;
            case "Totem" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "End rod" -> ParticleTypes.END_ROD;
            case "Clouds" -> ParticleTypes.CLOUD;
            case "Notes" -> ParticleTypes.NOTE;
            case "Witch" -> ParticleTypes.WITCH;
            case "Portal" -> ParticleTypes.PORTAL;
            case "Snow" -> ParticleTypes.SNOWFLAKE;
            case "Cherry" -> ParticleTypes.CHERRY_LEAVES;
            case "Glow" -> ParticleTypes.GLOW;
            case "Wax" -> ParticleTypes.WAX_ON;
            case "Ash" -> ParticleTypes.WHITE_ASH;
            case "Bubbles" -> ParticleTypes.BUBBLE_POP;
            case "Blood" -> dust(0xB0101A, 1.3f);
            default -> dust(rgb, 1.0f);   // "Colour dust"
        };
    }

    static final String[] PARTICLES = {"Colour dust", "Crit", "Magic", "Hearts", "Sparks", "Flames", "Soul flames", "Souls", "Totem",
            "End rod", "Clouds", "Notes", "Witch", "Portal", "Snow", "Cherry", "Glow", "Wax", "Ash", "Bubbles", "Blood"};

    /** Plays a sound only you hear, not positioned in the world. */
    static void ui(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** A sound only you hear, at a spot in the world. */
    static void at(Vec3 pos, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.playLocalSound(pos.x, pos.y, pos.z, sound, net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch, false);
    }

    static SoundEvent namedSound(String name) {
        return switch (name) {
            case "Bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "Pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "Chime" -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case "Xylophone" -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
            case "Hat" -> SoundEvents.NOTE_BLOCK_HAT.value();
            case "Bass drum" -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            case "Click" -> SoundEvents.UI_BUTTON_CLICK.value();
            case "Amethyst" -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case "Arrow hit" -> SoundEvents.ARROW_HIT_PLAYER;
            case "Anvil" -> SoundEvents.ANVIL_LAND;
            case "Level up" -> SoundEvents.PLAYER_LEVELUP;
            case "Teleport" -> SoundEvents.ENDERMAN_TELEPORT;
            default -> SoundEvents.EXPERIENCE_ORB_PICKUP;   // "Ding"
        };
    }

    static final String[] SOUNDS = {"Ding", "Bell", "Pling", "Chime", "Xylophone", "Hat", "Bass drum", "Click", "Amethyst", "Arrow hit", "Anvil", "Level up", "Teleport"};

    /** Adds an entity that exists only on this client. */
    static void addClientEntity(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        e.setId(nextId--);
        mc.level.addEntity(e);
    }

    static void removeClientEntity(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e == null) return;
        if (mc.level != null && mc.level.getEntity(e.getId()) == e) mc.level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
        else e.discard();
    }

    /** Client-only lightning: the bolt, the flash and the thunder, with no fire or damage. */
    static void lightning(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.snapTo(at.x, at.y, at.z);
        bolt.setVisualOnly(true);
        addClientEntity(bolt);
        mc.level.setSkyFlashTime(2);
    }
}
