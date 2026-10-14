package unblonded.anthrax.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import unblonded.anthrax.mixin.MinecraftUseInvoker;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Clicks for you while you hold a mouse button, with randomised timing: left, right or both. */
public final class AutoClickerModule extends FeatureModule {
    public static final AutoClickerModule INSTANCE = new AutoClickerModule();
    private final Setting.Mode button = setting(Setting.Mode.standalone("Button", "Left", "Left", "Right", "Both"),
            "Which held button gets auto-clicked. Right clicks use whatever you hold: place blocks, throw, eat, interact.");
    private final Setting.Num minCps = setting(Setting.Num.standalone("Min CPS", 9, 1, 20, 1), "Slowest left clicks per second.");
    private final Setting.Num maxCps = setting(Setting.Num.standalone("Max CPS", 13, 1, 20, 1), "Fastest left clicks per second.");
    private final Setting.Num rightMin = setting(Setting.Num.standalone("Right min CPS", 8, 1, 20, 1), "Slowest right clicks per second.");
    private final Setting.Num rightMax = setting(Setting.Num.standalone("Right max CPS", 12, 1, 20, 1), "Fastest right clicks per second.");
    private final Setting.Bool cooldown = setting(Setting.Bool.standalone("Wait for full charge", false), "Only left-clicks once your attack is fully charged (1.9+ combat).");
    private final Setting.Mode blocks = setting(Setting.Mode.standalone("Left on blocks", "Mine normally", "Mine normally", "Click", "Off"),
            "Mine normally leaves holding the button to break blocks as usual; Click hits the block on every click (instant-break blocks, creative, buttons); Off ignores blocks.");
    private final java.util.Random rnd = new java.util.Random();
    private long nextLeft, nextRight;

    private AutoClickerModule() { super("Auto Clicker", "Clicks for you while you hold the left or right mouse button, with randomised timing", "Combat", false); }

    @Override public void frame(Minecraft mc, float dt) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.gui.screen() != null) return;
        long now = System.nanoTime();
        String b = button.get();
        if (!b.equals("Right") && mc.options.keyAttack.isDown() && now >= nextLeft) {
            nextLeft = now + interval(minCps.get(), maxCps.get());
            left(mc, p);
        }
        if (!b.equals("Left") && mc.options.keyUse.isDown() && now >= nextRight && !p.isUsingItem()) {
            nextRight = now + interval(rightMin.get(), rightMax.get());
            MinecraftUseInvoker inv = (MinecraftUseInvoker) mc;
            inv.anthrax$startUseItem();
            inv.anthrax$setRightClickDelay(4);   // keep vanilla's own hold-to-repeat from adding extra clicks
        }
    }

    private long interval(double a, double b) {
        double lo = Math.min(a, b), hi = Math.max(a, b);
        return (long) (1e9 / (lo + rnd.nextDouble() * (hi - lo)));
    }

    private void left(Minecraft mc, LocalPlayer p) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult e) {
            if (cooldown.get() && p.getAttackStrengthScale(0.5f) < 1f) return;
            mc.gameMode.attack(p, e.getEntity());
            p.swing(InteractionHand.MAIN_HAND);
        } else if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            if (!blocks.get().equals("Click")) return;   // vanilla handles held mining
            mc.gameMode.startDestroyBlock(bh.getBlockPos(), bh.getDirection());
            p.swing(InteractionHand.MAIN_HAND);
        } else {
            if (cooldown.get() && p.getAttackStrengthScale(0.5f) < 1f) return;
            p.swing(InteractionHand.MAIN_HAND);
            p.resetAttackStrengthTicker();
        }
    }
}
