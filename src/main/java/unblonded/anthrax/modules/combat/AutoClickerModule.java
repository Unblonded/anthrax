package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Moves;
import unblonded.anthrax.modules.Setting;

/** Clicks for you while you hold attack, with randomised timing. */
public final class AutoClickerModule extends FeatureModule {
    public static final AutoClickerModule INSTANCE = new AutoClickerModule();
    private final Setting.Num minCps = setting(Setting.Num.standalone("Min CPS", 9, 1, 20, 1), "Slowest clicks per second.");
    private final Setting.Num maxCps = setting(Setting.Num.standalone("Max CPS", 13, 1, 20, 1), "Fastest clicks per second.");
    private final Setting.Bool cooldown = setting(Setting.Bool.standalone("Wait for full charge", false), "Only clicks once your attack is fully charged (1.9+ combat).");
    private final java.util.Random rnd = new java.util.Random();
    private long next;

    private AutoClickerModule() { super("Auto Clicker", "Clicks for you while you hold the mouse button, with randomised timing", "Combat", false); }

    @Override public void tick(Minecraft mc) { }

    @Override public void frame(Minecraft mc, float dt) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.gui.screen() != null || !mc.options.keyAttack.isDown()) return;
        long now = System.nanoTime();
        if (now < next) return;
        double lo = Math.min(minCps.get(), maxCps.get()), hi = Math.max(minCps.get(), maxCps.get());
        next = now + (long) (1e9 / (lo + rnd.nextDouble() * (hi - lo)));
        if (cooldown.get() && p.getAttackStrengthScale(0.5f) < 1f) return;
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) {
            mc.gameMode.attack(p, hit.getEntity());
            p.swing(InteractionHand.MAIN_HAND);
        } else if (mc.hitResult == null || mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            p.swing(InteractionHand.MAIN_HAND);
        }
    }
}
