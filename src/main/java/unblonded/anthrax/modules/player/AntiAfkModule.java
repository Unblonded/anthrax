package unblonded.anthrax.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import unblonded.anthrax.gui.Notifications;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

/** Prevents AFK kicks with small randomised actions. */
public final class AntiAfkModule extends FeatureModule {
    public static final AntiAfkModule INSTANCE = new AntiAfkModule();
    private final Setting.Num interval = setting(Setting.Num.standalone("Interval (s)", 20, 5, 240, 5), "Average time between actions; each wait is randomised.");
    private final Setting.Bool jump = setting(Setting.Bool.standalone("Jump", true), "Hop now and then.");
    private final Setting.Bool look = setting(Setting.Bool.standalone("Look around", true), "Turn your head a little.");
    private final Setting.Bool swing = setting(Setting.Bool.standalone("Swing", true), "Swing your hand.");
    private final java.util.Random rnd = new java.util.Random();
    private long next;

    private AntiAfkModule() { super("Anti AFK", "Prevents AFK kicks with randomised actions", "Player", false); }

    @Override public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        long now = System.currentTimeMillis();
        if (next == 0) next = now + delay();
        if (now < next) return;
        next = now + delay();
        int pick = rnd.nextInt(3);
        if (pick == 0 && jump.get() && p.onGround()) p.jumpFromGround();
        else if (pick == 1 && look.get()) { p.setYRot(p.getYRot() + (rnd.nextFloat() - 0.5f) * 60f); p.setXRot(Math.max(-60, Math.min(60, p.getXRot() + (rnd.nextFloat() - 0.5f) * 20f))); }
        else if (swing.get()) p.swing(InteractionHand.MAIN_HAND);
    }

    private long delay() { return (long) (interval.get() * 1000 * (0.6 + rnd.nextDouble() * 0.8)); }
}
