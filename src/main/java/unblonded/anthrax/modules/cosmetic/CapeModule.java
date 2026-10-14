package unblonded.anthrax.modules.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import unblonded.anthrax.gui.ThemeColors;
import unblonded.anthrax.modules.Setting;

/**
 * A cape drawn by the client (CapeMixin swaps it into your skin). Only you see it; it replaces any cape you own
 * while it is on. The texture is painted here and animated designs are repainted a few times a second.
 */
public final class CapeModule extends CosmeticModule {
    public static final CapeModule INSTANCE = new CapeModule();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("anthrax", "cosmetic/cape");
    private final Setting.Mode design = setting(Setting.Mode.standalone("Design", "Anthrax", "Anthrax", "Galaxy", "Flame", "Ocean", "Rainbow", "Aurora", "Carbon", "Solid"),
            "Anthrax uses your menu colours with the client mark; Rainbow, Flame, Galaxy and Aurora are animated.");
    private final Setting.Num hue = setting(Setting.Num.standalone("Solid hue", 285, 0, 360, 1), "Colour of the Solid design.");
    private final Setting.Bool elytra = setting(Setting.Bool.standalone("On elytra too", true), "Paint your elytra with the same design.");
    private final Setting.Bool animate = setting(Setting.Bool.standalone("Animate", true), "Let the animated designs move.");

    private NativeImage image;
    private DynamicTexture texture;
    private ClientAsset.Texture asset;
    private long lastPaint;
    private String painted = "";

    private CapeModule() { super("Cape", "Wear a client-side cape: animated galaxy, flame, rainbow, aurora or your theme colours. Only you see it"); }

    @Override public void tick(Minecraft mc) {
        if (texture == null) {
            image = new NativeImage(64, 32, true);
            texture = new DynamicTexture(() -> "anthrax cape", image);
            mc.getTextureManager().register(ID, texture);
            asset = new ClientAsset.ResourceTexture(ID, ID);
        }
        long now = System.currentTimeMillis();
        boolean moving = animate.get() && !design.get().equals("Solid") && !design.get().equals("Carbon");
        String key = design.get() + hue.get();
        if (!key.equals(painted) || (moving && now - lastPaint > 66) || (design.get().equals("Anthrax") && now - lastPaint > 500)) {
            paint(now / 1000.0);
            texture.upload();
            painted = key;
            lastPaint = now;
        }
    }

    /** Swapped into your skin by CapeMixin. */
    public PlayerSkin apply(PlayerSkin skin) {
        if (!isEnabled() || asset == null) return skin;
        return new PlayerSkin(skin.body(), asset, elytra.get() ? asset : skin.elytra(), skin.model(), skin.secure());
    }

    private void paint(double t) {
        String d = design.get();
        int a1 = ThemeColors.accent() & 0xFFFFFF, a2 = ThemeColors.accent2() & 0xFFFFFF;
        for (int x = 0; x < 64; x++)
            for (int y = 0; y < 32; y++) {
                // cape front sits at (1..11, 1..17); u/v are 0..1 across it so every face gets a sensible slice
                double u = ((x % 22) - 1) / 10.0, v = (y - 1) / 16.0;
                int rgb = switch (d) {
                    case "Galaxy" -> galaxy(x, y, u, v, t);
                    case "Flame" -> flame(u, v, t);
                    case "Ocean" -> mix(0x0A2A6B, 0x19D3E6, (float) (v * 0.8 + 0.2 * Math.sin(u * 8 + v * 5 + t * 2)));
                    case "Rainbow" -> hsl((float) (v * 220 + u * 40 - t * 90), 0.9f, 0.58f);
                    case "Aurora" -> mix(0x07101F, mix(0x3DFF8A, 0xA25CFF, (float) v), (float) Math.max(0, Math.sin(u * 5 + Math.sin(v * 4 + t) * 2 + t * 1.5)) * 0.9f);
                    case "Carbon" -> ((x + y) % 2 == 0) ? 0x1A1A1E : 0x101013;
                    case "Solid" -> mix(hsl((float) (double) hue.get(), 0.85f, 0.5f), hsl((float) (double) hue.get(), 0.85f, 0.3f), (float) v);
                    default -> anthrax(u, v, a1, a2);
                };
                image.setPixel(x, y, 0xFF000000 | rgb);
            }
    }

    private static int anthrax(double u, double v, int a1, int a2) {
        int base = mix(0x121216, 0x1D1D24, (float) v);
        int c = mix(a1, a2, (float) v);
        double cx = u - 0.5, cy = v - 0.42;
        // a hollow diamond mark in the middle with a stripe down each edge
        double dia = Math.abs(cx) * 1.5 + Math.abs(cy);
        if (dia < 0.3 && dia > 0.18) return c;
        if (u < 0.1 || u > 0.9) return mix(base, c, 0.65f);
        if (v > 0.88) return mix(base, c, 0.4f);
        return base;
    }

    private static int galaxy(int x, int y, double u, double v, double t) {
        int sky = mix(0x0B0420, 0x2A0F55, (float) (0.5 + 0.5 * Math.sin(u * 3 + v * 4 + t * 0.4)));
        sky = mix(sky, 0x7A2BD6, (float) Math.max(0, Math.sin(u * 6 - v * 3 + t * 0.6)) * 0.35f);
        int h = (x * 73856093) ^ (y * 19349663);
        if ((h & 63) == 0) {
            double tw = 0.5 + 0.5 * Math.sin(t * 4 + (h >> 6));
            return mix(sky, 0xFFFFFF, (float) tw);
        }
        return sky;
    }

    private static int flame(double u, double v, double t) {
        double f = 1 - v + 0.25 * Math.sin(u * 9 + t * 6) * Math.sin(v * 7 - t * 4);
        f = Math.max(0, Math.min(1, f));
        return f > 0.66 ? mix(0xFF8C1A, 0xFFE45C, (float) ((f - 0.66) / 0.34)) : mix(0x2A0602, 0xFF3B0F, (float) (f / 0.66));
    }
}
