package unblonded.anthrax.gui;

import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unblonded.anthrax.modules.client.ClientSettingsModule;
import unblonded.anthrax.modules.visual.FontSizeModule;

import java.util.ArrayList;
import java.util.List;

import static unblonded.anthrax.gui.Draw.*;

/**
 * Title screen in the menu's liquid-glass style: the vanilla panorama, blurred, behind a glass panel
 * on the left with Singleplayer, Multiplayer, Realms, Options and Quit. Swapped in for the vanilla
 * title screen (see {@link #replace}); "Vanilla menu" opens the original one.
 */
public final class AnthraxTitleScreen extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("Anthrax");
    /** The one vanilla title screen the player asked for; it and its sub-screens' "back" stay vanilla. */
    private static Screen vanilla;

    /** Called for every screen change: hands back this screen in place of a fresh vanilla title screen. */
    public static Screen replace(Screen screen) {
        if (screen == null || screen.getClass() != TitleScreen.class || screen == vanilla) return screen;
        if (!ClientSettingsModule.INSTANCE.customTitle()) return screen;
        return new AnthraxTitleScreen();
    }

    private record Item(String label, String glyph, Runnable action) {}
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Item> items = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final long openNs = System.nanoTime();
    private int selected = -1;
    private int mx, my;
    private float gs = 1;
    private boolean failed;

    public AnthraxTitleScreen() { super(Component.translatable("narrator.screen.title")); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        minecraft.gameRenderer.panorama().startSpin();
        items.clear();
        Minecraft mc = minecraft;
        items.add(new Item("Singleplayer", Icons.CUBE, () -> mc.gui.setScreen(new SelectWorldScreen(this))));
        items.add(new Item("Multiplayer", Icons.SERVER, () -> {
            if (!mc.allowsMultiplayer()) { Notifications.warn("Multiplayer", "Disabled for this account"); return; }
            mc.gui.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
        }));
        items.add(new Item("Realms", Icons.CLOUD, () -> mc.gui.setScreen(new RealmsMainScreen(this))));
        items.add(new Item("Options", Icons.GEAR, () -> mc.gui.setScreen(new OptionsScreen(this, mc.options, false))));
        items.add(new Item("Quit game", Icons.POWER_OFF, mc::stop));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractPanorama(g, delta);
        if (Draw.FX > 0) extractBlurredBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (failed) return;
        Draw.measureFont = font;
        gs = (float) minecraft.getWindow().getGuiScale();
        int pw = Math.round(width * gs), ph = Math.round(height * gs);
        mx = Math.round(mouseX * gs);
        my = Math.round(mouseY * gs);
        Theme.tick(System.currentTimeMillis());
        int savedS = Draw.S;
        Draw.S = FontSizeModule.apply(Math.min(Math.round(ph / 720f), Math.min(pw / 600, ph / 400)));
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            hits.clear();
            draw(g, pw, ph);
            Hud.toasts(g, pw, ph, Theme.accent(), u(SP_L));
        } catch (Throwable t) {
            // never strand the player on a broken title screen: fall back to vanilla
            failed = true;
            LOG.error("Anthrax title screen failed, opening the vanilla one", t);
            minecraft.execute(this::openVanilla);
        } finally {
            g.pose().popMatrix();
            Draw.S = savedS;
        }
    }

    private void draw(GuiGraphicsExtractor g, int pw, int ph) {
        int accent = Theme.accent(), accent2 = Theme.accent2();
        float t = (System.nanoTime() - openNs) / 1e9f;
        float open = easeOut(t / 0.35f);

        // veil: darkens the panorama so the glass and white text read, tinted with the accent
        g.fill(0, 0, pw, ph, alpha(0x05060B, (int) ((FX > 0 ? 0x60 : 0x90) * open)));
        g.fillGradient(0, 0, pw, ph, alpha(accent, (int) (0x0E * open)), alpha(accent2, (int) (0x16 * open)));

        // ---- the panel: left third of the screen, vertically centred
        int pad = u(SP_XL), btnH = u(38), btnGap = u(SP_S);
        int headH = u(64);
        int ww = Math.min(pw - 2 * u(SP_L), u(320));
        int wh = pad + headH + u(SP_L) + items.size() * (btnH + btnGap) - btnGap + u(SP_L) + u(28) + pad;
        int wx = Math.max(u(SP_L), Math.min(Math.round(pw * 0.08f), pw - ww - u(SP_L)));
        int wy = (ph - wh) / 2;
        wx -= Math.round((1 - open) * u(24));

        shadow(g, wx, wy, ww, wh, u(R_WINDOW), u(22), (int) (0x70 * open));
        canvas(g, wx, wy, ww, wh, u(R_WINDOW));

        // header: logo, name, version
        int lx = wx + pad, logo = u(44), hy = wy + pad;
        if (FANCY) glow(g, lx + logo / 2, hy + logo / 2, u(36), accent, 3, 12);
        logo(g, lx, hy, logo, accent, accent2);
        int tx = lx + logo + u(SP_M);
        int savedS = Draw.S;
        Draw.S = Math.min(4, savedS + 1);
        if (Theme.idx == 0) textRainbow(g, tx, hy + logo / 2 - u(4), "Anthrax", Theme.hue, 22f, true);
        else text(g, tx, hy + logo / 2 - u(4), "Anthrax", Theme.TEXT, true);
        Draw.S = savedS;
        text(g, tx, hy + logo / 2 + u(14), "Minecraft " + SharedConstants.getCurrentVersion().name(), Theme.MUTED, false);
        int divY = wy + pad + headH;
        fill(g, wx + pad, divY, ww - 2 * pad, Math.max(1, S / 2), 0x16FFFFFF);

        // menu items, staggered in
        int by = divY + u(SP_L), bx = wx + pad, bw = ww - 2 * pad;
        boolean anyHover = false;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float ap = easeOut((t - 0.06f - 0.035f * i) / 0.28f);
            int y = by + i * (btnH + btnGap) + Math.round((1 - ap) * u(10));
            boolean hov = inside(mx, my, bx, y, bw, btnH);
            if (hov) { selected = i; anyHover = true; }
            boolean focus = i == selected;
            float k = hover("title:" + it.label, focus);
            boolean primary = i == 0, quit = i == items.size() - 1;
            int tint = quit ? Theme.DANGER : primary ? accent : 0xFFFFFF;
            liquidButton(g, bx, y, bw, btnH, primary ? Math.max(0.35f, k) : k, false, tint, mx);
            int cy = y + btnH / 2;
            int iconCol = lerp(primary ? accent : Theme.SUBTEXT, 0xFFFFFFFF, k);
            icon(g, bx + u(SP_L + 6), cy, it.glyph, iconCol);
            text(g, bx + u(SP_L + 22) + Math.round(k * u(2)), cy, it.label, lerp(Theme.SUBTEXT, 0xFFFFFFFF, Math.max(k, primary ? 0.6f : 0f)), primary);
            if (k > 0.02f) icon(g, bx + bw - u(SP_L) - Math.round((1 - k) * u(6)), cy, Icons.ANGLE_RIGHT, alpha(0xFFFFFF, Math.round(0xFF * k)));
            hits.add(new Hit(bx, y, bw, btnH, it.action));
        }
        if (!anyHover && selected >= 0 && mouseMovedSinceKey) selected = -1;

        // small row: language, accessibility, and the vanilla menu
        int rowY = by + items.size() * (btnH + btnGap) - btnGap + u(SP_L);
        int sq = u(28);
        smallIcon(g, bx, rowY, sq, Icons.LANGUAGE, "lang",
                () -> minecraft.gui.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())));
        smallIcon(g, bx + sq + u(SP_S), rowY, sq, Icons.UNIVERSAL_ACCESS, "access",
                () -> minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, minecraft.options)));
        String vanillaLabel = "Vanilla menu";
        int vw = textWidth(vanillaLabel, false) + u(SP_L + 12);
        int vx = bx + bw - vw;
        boolean vhov = inside(mx, my, vx, rowY, vw, sq);
        float vk = hover("title:vanilla", vhov);
        liquidButton(g, vx, rowY, vw, sq, vk, false, 0xFFFFFF, mx);
        textC(g, vx + vw / 2, rowY + sq / 2, vanillaLabel, lerp(Theme.MUTED, 0xFFFFFFFF, vk), false);
        hits.add(new Hit(vx, rowY, vw, sq, this::openVanilla));

        // footer: who is playing (left), attribution (right, opens the credits like vanilla)
        int fy = ph - u(SP_L);
        String user = minecraft.getUser().getName();
        icon(g, u(SP_L + 6), fy, Icons.CIRCLE_USER, Theme.MUTED);
        text(g, u(SP_L + 18), fy, user, Theme.SUBTEXT, false);
        String copy = "Copyright Mojang AB. Do not distribute!";
        int cw = textWidth(copy, false);
        boolean chov = inside(mx, my, pw - u(SP_L) - cw, fy - u(8), cw, u(16));
        textR(g, pw - u(SP_L), fy, copy, chov ? 0xFFFFFFFF : Theme.MUTED, false);
        if (chov) fill(g, pw - u(SP_L) - cw, fy + u(7), cw, Math.max(1, S / 2), 0x80FFFFFF);
        hits.add(new Hit(pw - u(SP_L) - cw, fy - u(8), cw, u(16), () -> minecraft.gui.setScreen(new CreditsAndAttributionScreen(this))));
    }

    private void smallIcon(GuiGraphicsExtractor g, int x, int y, int size, String glyph, String id, Runnable action) {
        boolean hov = inside(mx, my, x, y, size, size);
        float k = hover("title:" + id, hov);
        liquidButton(g, x, y, size, size, k, false, 0xFFFFFF, mx);
        icon(g, x + size / 2, y + size / 2, glyph, lerp(Theme.SUBTEXT, 0xFFFFFFFF, k));
        hits.add(new Hit(x, y, size, size, action));
    }

    private void openVanilla() {
        vanilla = new TitleScreen();
        minecraft.gui.setScreen(vanilla);
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float i = 1f - t;
        return 1f - i * i * i;
    }

    /** Keyboard selection sticks until the mouse moves again. */
    private boolean mouseMovedSinceKey = true;
    private int lastMx = Integer.MIN_VALUE, lastMy;

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        int x = Math.round((float) mouseX * gs), y = Math.round((float) mouseY * gs);
        if (x != lastMx || y != lastMy) mouseMovedSinceKey = true;
        lastMx = x;
        lastMy = y;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int px = Math.round((float) event.x() * gs), py = Math.round((float) event.y() * gs);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (inside(px, py, h.x(), h.y(), h.w(), h.h())) { h.action().run(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) {
            mouseMovedSinceKey = false;
            selected = selected < 0 ? 0 : (selected + 1) % items.size();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            mouseMovedSinceKey = false;
            selected = selected <= 0 ? items.size() - 1 : selected - 1;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) && selected >= 0) {
            items.get(selected).action().run();
            return true;
        }
        return super.keyPressed(event);
    }
}
