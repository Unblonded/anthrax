package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.modules.client.FriendsModule;

import java.util.function.Predicate;

/** Shared combat helpers: targeting, rotations, weapons, and the current target for the target HUD. */
public final class CombatUtil {
    private CombatUtil() {}

    // ------------------------------------------------------------------ current target
    private static LivingEntity target;
    private static long targetAt;

    /** Remember who we are fighting (aura target or whoever we last hit); the target HUD shows them. */
    public static void markTarget(Entity e) {
        if (e instanceof LivingEntity living) { target = living; targetAt = System.currentTimeMillis(); }
    }

    /** The current fight target, or null once they are gone or untouched for 6 s. */
    public static LivingEntity target() {
        LivingEntity t = target;
        if (t == null || t.isRemoved() || !t.isAlive() || System.currentTimeMillis() - targetAt > 6000) return null;
        return t;
    }

    // ------------------------------------------------------------------ targeting
    public static boolean attackable(LocalPlayer self, Entity e, boolean players, boolean hostiles, boolean animals, boolean invisible) {
        if (unblonded.anthrax.modules.cosmetic.PetsModule.isPet(e)) return false;
        if (e == self || e.isRemoved() || !(e instanceof LivingEntity living) || !living.isAlive() || living.getHealth() <= 0) return false;
        if (!invisible && e.isInvisible()) return false;
        if (e instanceof Player p) return players && !p.isSpectator() && !p.isCreative() && !FriendsModule.protects(p);
        if (e instanceof Enemy) return hostiles;
        if (e instanceof Animal) return animals;
        return false;
    }

    /** Closest point of the target's hitbox to our eyes: how far an attack actually reaches. */
    public static double reachSq(LocalPlayer self, Entity e) {
        Vec3 eye = self.getEyePosition();
        var box = e.getBoundingBox();
        double x = Mth.clamp(eye.x, box.minX, box.maxX), y = Mth.clamp(eye.y, box.minY, box.maxY), z = Mth.clamp(eye.z, box.minZ, box.maxZ);
        return eye.distanceToSqr(x, y, z);
    }

    public static float[] rotationTo(LocalPlayer self, Vec3 point, float partial) {
        Vec3 eye = self.getEyePosition(partial);
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        return new float[]{(float) (Math.toDegrees(Math.atan2(dz, dx)) - 90),
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    /** Angle in degrees between where we look and the point. */
    public static float angleTo(LocalPlayer self, Vec3 point) {
        float[] r = rotationTo(self, point, 1f);
        float dy = Mth.wrapDegrees(r[0] - self.getYRot()), dp = r[1] - self.getXRot();
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    /** Aim point on a target: the part of its body closest to our eye height, so aim stays natural. */
    public static Vec3 aimPoint(LocalPlayer self, Entity e, float partial) {
        Vec3 base = e.getPosition(partial);
        double eye = self.getEyePosition(partial).y;
        double y = Mth.clamp(eye, base.y + 0.2, base.y + e.getBbHeight() * 0.9);
        return new Vec3(base.x, y, base.z);
    }

    /** Turn the view toward a point at most {@code degPerSec} (smooth) or instantly when {@code degPerSec <= 0}. */
    public static void turnToward(LocalPlayer self, Vec3 point, float partial, float degPerSec, float dt) {
        float[] r = rotationTo(self, point, partial);
        float yaw = Mth.wrapDegrees(r[0] - self.getYRot()), pitch = r[1] - self.getXRot();
        float angular = (float) Math.sqrt(yaw * yaw + pitch * pitch);
        if (angular < 0.05f) return;
        float f = degPerSec <= 0 ? 1f : Math.min(1f, degPerSec * dt / angular);
        // turn() moves current and previous rotation together, so the camera never jitters
        self.turn(yaw * f / 0.15f, pitch * f / 0.15f);
    }

    // ------------------------------------------------------------------ attacking
    /** Attack cooldown (0..1); 1 means a full-strength hit. */
    public static float cooldown(LocalPlayer self) { return self.getAttackStrengthScale(0.5f); }

    /** True while a jump attack would land as a critical hit. */
    public static boolean critReady(LocalPlayer self) {
        return !self.onGround() && self.fallDistance > 0 && !self.isInWater() && !self.onClimbable()
                && !self.isPassenger() && !self.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
    }

    /** True while we are in the air but not falling yet: waiting a few ticks turns the hit into a crit. */
    public static boolean critPending(LocalPlayer self) {
        return !self.onGround() && !self.isInWater() && !self.onClimbable() && self.getDeltaMovement().y > -0.08 && self.fallDistance <= 0;
    }

    public static void attack(Minecraft mc, Entity e) {
        mc.gameMode.attack(mc.player, e);
        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        markTarget(e);
    }

    // ------------------------------------------------------------------ items
    public static double attackDamage(ItemStack stack) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double damage = mods.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
        return damage + (stack.isEnchanted() ? 0.5 : 0);
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(net.minecraft.world.item.Items.MACE)
                || stack.is(net.minecraft.world.item.Items.TRIDENT);
    }

    public static int findHotbar(LocalPlayer self, Predicate<ItemStack> test) {
        for (int i = 0; i < 9; i++) if (test.test(self.getInventory().getItem(i))) return i;
        return -1;
    }

    /** Hotbar slot of the hardest-hitting weapon, or -1. */
    public static int bestWeapon(LocalPlayer self) {
        int best = -1;
        double bestDamage = 1.0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = self.getInventory().getItem(i);
            if (s.isEmpty() || !isWeapon(s)) continue;
            double d = attackDamage(s);
            if (d > bestDamage) { bestDamage = d; best = i; }
        }
        return best;
    }
}
