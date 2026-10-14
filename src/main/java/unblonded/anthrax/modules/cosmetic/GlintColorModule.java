package unblonded.anthrax.modules.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unblonded.anthrax.modules.Setting;

import java.io.InputStream;

/**
 * Recolours the enchantment glint on items and armour. The vanilla glint textures are read once, turned to
 * brightness and re-tinted into textures registered under the same names, so every glint picks up the colour.
 */
public final class GlintColorModule extends CosmeticModule {
    public static final GlintColorModule INSTANCE = new GlintColorModule();
    private static final Logger LOG = LoggerFactory.getLogger("Anthrax/Glint");
    private static final Identifier[] IDS = {ItemFeatureRenderer.ENCHANTED_GLINT_ITEM, ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR};
    private final Palette palette = palette("Theme");
    private final Setting.Num intensity = setting(Setting.Num.standalone("Intensity", 1.3, 0.3, 3, 0.1), "Brightness of the glint.");

    private final NativeImage[] source = new NativeImage[2];
    private final NativeImage[] tinted = new NativeImage[2];
    private final DynamicTexture[] textures = new DynamicTexture[2];
    private int lastRgb = -1;
    private double lastIntensity;
    private long lastCheck;

    private GlintColorModule() { super("Glint Color", "Changes the colour of the enchantment glint on items and armour"); }

    @Override public void tick(Minecraft mc) {
        if (source[0] == null && !load(mc)) { setEnabled(false); return; }
        long now = System.currentTimeMillis();
        // a resource reload replaces (and closes) our textures; build them again from the new pack
        if (now - lastCheck > 1000) {
            lastCheck = now;
            boolean lost = false;
            for (int i = 0; i < 2; i++) lost |= mc.getTextureManager().getTexture(IDS[i]) != textures[i];
            if (lost) {
                for (int i = 0; i < 2; i++) { if (source[i] != null) source[i].close(); source[i] = null; tinted[i] = null; textures[i] = null; }
                if (!load(mc)) { setEnabled(false); return; }
            }
        }
        int rgb = palette.rgb();
        if (rgb != lastRgb || intensity.get() != lastIntensity) {
            lastRgb = rgb;
            lastIntensity = intensity.get();
            for (int i = 0; i < 2; i++) { tint(source[i], tinted[i], rgb, (float) lastIntensity); textures[i].upload(); }
        }
    }

    private boolean load(Minecraft mc) {
        try {
            for (int i = 0; i < 2; i++) {
                var res = mc.getResourceManager().getResource(IDS[i]);
                if (res.isEmpty()) return false;
                try (InputStream in = res.get().open()) { source[i] = NativeImage.read(in); }
                tinted[i] = new NativeImage(source[i].getWidth(), source[i].getHeight(), true);
                int idx = i;
                textures[i] = new DynamicTexture(() -> "anthrax glint " + idx, tinted[i]);
                mc.getTextureManager().register(IDS[i], textures[i]);
            }
            lastRgb = -1;
            return true;
        } catch (Exception e) {
            LOG.warn("Could not load the glint textures", e);
            return false;
        }
    }

    private static void tint(NativeImage src, NativeImage dst, int rgb, float k) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        // the glint adds light, so stretch its brightness to the full range or dim colours barely show
        float peak = 0.01f;
        for (int x = 0; x < src.getWidth(); x++)
            for (int y = 0; y < src.getHeight(); y++) {
                int p = src.getPixel(x, y);
                peak = Math.max(peak, (0.3f * ((p >> 16) & 255) + 0.59f * ((p >> 8) & 255) + 0.11f * (p & 255)) / 255f);
            }
        k /= peak;
        for (int x = 0; x < src.getWidth(); x++)
            for (int y = 0; y < src.getHeight(); y++) {
                int p = src.getPixel(x, y);
                int a = (p >>> 24) & 255;
                float lum = (0.3f * ((p >> 16) & 255) + 0.59f * ((p >> 8) & 255) + 0.11f * (p & 255)) / 255f * k;
                int nr = Math.min(255, Math.round(lum * r * 255)), ng = Math.min(255, Math.round(lum * g * 255)), nb = Math.min(255, Math.round(lum * b * 255));
                dst.setPixel(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
    }

    @Override protected void onToggle(boolean enabled) {
        if (enabled) return;
        Minecraft mc = Minecraft.getInstance();
        // give the names back to the vanilla textures; they load again on next use
        for (int i = 0; i < 2; i++) {
            if (textures[i] == null) continue;
            AbstractTexture current = mc.getTextureManager().getTexture(IDS[i]);
            if (current == textures[i]) mc.getTextureManager().release(IDS[i]);
            textures[i] = null;
            if (source[i] != null) source[i].close();
            source[i] = null;
            tinted[i] = null;
        }
    }
}
