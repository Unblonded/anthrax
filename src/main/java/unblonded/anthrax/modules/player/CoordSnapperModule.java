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

/** Copies your block coordinates and dimension to the clipboard, then turns itself off. Bind it to a key. */
public final class CoordSnapperModule extends FeatureModule {
    public static final CoordSnapperModule INSTANCE = new CoordSnapperModule();

    private CoordSnapperModule() { super("Coord Snapper", "Copies your block coordinates and dimension to the clipboard. Bind it to a key", "Player", false); }

    @Override protected void onToggle(boolean enabled) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            String dim = mc.level.dimension().identifier().getPath();
            String text = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ() + " (" + dim + ")";
            mc.keyboardHandler.setClipboard(text);
            Notifications.success("Coord Snapper", "Copied " + text);
        }
        mc.execute(() -> setEnabled(false));
    }

    @Override protected boolean hiddenFromList() { return true; }
}
