package unblonded.anthrax.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.Setting;

/**
 * A pet that follows you around. It exists only on your client (the server and other players never see it), so it
 * can't be hit, pushed or targeted; the crosshair and combat modules skip it.
 */
public final class PetsModule extends CosmeticModule {
    public static final PetsModule INSTANCE = new PetsModule();
    private final Setting.Mode type = setting(Setting.Mode.standalone("Pet", "Allay", "Allay", "Parrot", "Bee", "Vex", "Bat", "Cat", "Fox", "Wolf", "Axolotl", "Frog", "Chicken", "Rabbit"),
            "Allay, Parrot, Bee, Vex and Bat fly beside you; the rest walk at your heels.");
    private final Setting.Text name = setting(Setting.Text.standalone("Name", "", 24), "A name tag over your pet. Leave empty for none.");
    private final Setting.Bool baby = setting(Setting.Bool.standalone("Baby", false), "A baby version where there is one.");
    private final Setting.Num distance = setting(Setting.Num.standalone("Follow distance", 1.6, 0.8, 4, 0.1), "How far behind you it stays.");
    private final Setting.Bool hideFirstPerson = setting(Setting.Bool.standalone("Hide in first person", false), "Only show the pet in third person.");

    private Mob pet;
    private String spawnedType = "";
    private boolean spawnedBaby;
    private Vec3 smooth;

    private PetsModule() { super("Pets", "A client-side pet that follows you around: allay, parrot, cat, fox and more. Only you see it"); }

    /** True for the client-only pet, so picking and combat can ignore it. */
    public static boolean isPet(Entity e) { return e != null && INSTANCE.pet == e; }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { despawn(); return; }
        if (pet != null && (pet.level() != mc.level || pet.isRemoved() || !type.get().equals(spawnedType) || baby.get() != spawnedBaby)) despawn();
        if (pet == null && !spawn(mc)) return;
        boolean flier = flier(type.get());
        double yaw = Math.toRadians(p.getYRot());
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));        // behind the player
        Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        double t = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 target = p.position().add(back.scale(distance.get())).add(side.scale(flier ? 0.7 : 0.45))
                .add(0, flier ? 1.5 + 0.15 * Math.sin(t / 8.0) : 0, 0);
        if (!flier) target = new Vec3(target.x, groundY(mc, target), target.z);
        if (smooth == null || smooth.distanceToSqr(target) > 400) smooth = target;
        smooth = smooth.lerp(target, 0.25);
        Vec3 look = p.position().subtract(smooth);
        float face = p.getDeltaMovement().horizontalDistanceSqr() > 0.002 ? p.getYRot() : (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        pet.moveOrInterpolateTo(smooth, face, 0);
        pet.setYHeadRot(face);
        pet.setInvisible(hideFirstPerson.get() && mc.options.getCameraType().isFirstPerson());
        String n = name.get();
        if (!n.equals(pet.getCustomName() == null ? "" : pet.getCustomName().getString())) {
            pet.setCustomName(n.isBlank() ? null : Component.literal(n));
            pet.setCustomNameVisible(!n.isBlank());
        }
    }

    private boolean spawn(Minecraft mc) {
        EntityType<? extends Mob> et = switch (type.get()) {
            case "Parrot" -> EntityTypes.PARROT;
            case "Bee" -> EntityTypes.BEE;
            case "Vex" -> EntityTypes.VEX;
            case "Bat" -> EntityTypes.BAT;
            case "Cat" -> EntityTypes.CAT;
            case "Fox" -> EntityTypes.FOX;
            case "Wolf" -> EntityTypes.WOLF;
            case "Axolotl" -> EntityTypes.AXOLOTL;
            case "Frog" -> EntityTypes.FROG;
            case "Chicken" -> EntityTypes.CHICKEN;
            case "Rabbit" -> EntityTypes.RABBIT;
            default -> EntityTypes.ALLAY;
        };
        Mob m = et.create(mc.level, EntitySpawnReason.COMMAND);
        if (m == null) return false;
        m.setNoAi(true);
        m.setSilent(true);
        m.setInvulnerable(true);
        m.setNoGravity(true);
        if (baby.get() && m instanceof AgeableMob a) a.setBaby(true);
        smooth = mc.player.position();
        m.snapTo(smooth.x, smooth.y, smooth.z, mc.player.getYRot(), 0);
        Fx.addClientEntity(m);
        pet = m;
        spawnedType = type.get();
        spawnedBaby = baby.get();
        return true;
    }

    /** Top of the ground under a spot, searching a few blocks up and down from it. */
    private static double groundY(Minecraft mc, Vec3 at) {
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        int y0 = (int) Math.floor(at.y + 0.5);
        for (int dy = 1; dy >= -4; dy--) {
            m.set(Math.floor(at.x), y0 + dy - 1, Math.floor(at.z));
            var shape = mc.level.getBlockState(m).getCollisionShape(mc.level, m);
            if (!shape.isEmpty()) return m.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
        }
        return at.y;
    }

    private static boolean flier(String t) { return t.equals("Allay") || t.equals("Parrot") || t.equals("Bee") || t.equals("Vex") || t.equals("Bat"); }

    private void despawn() {
        if (pet != null) Fx.removeClientEntity(pet);
        pet = null;
        smooth = null;
    }

    @Override protected void onToggle(boolean enabled) { if (!enabled) despawn(); }
}
