package unblonded.anthrax.mixin;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Crisp UI text. Vanilla measures TrueType glyphs with hinting disabled (fractional advances) but then
 * rasterises them with hinting on, so letters sit on half pixels and get snapped unevenly by the
 * nearest-filtered font atlas, which reads as blur. For the Anthrax UI font we measure with hinting
 * too: metrics and bitmaps then agree and every advance is a whole pixel.
 */
@Mixin(TrueTypeGlyphProvider.class)
public abstract class FontHintingMixin {
    private static final int FT_LOAD_BITMAP_METRICS_ONLY = 0x400000;
    private static final int FT_LOAD_NO_HINTING = 0x8;

    @Redirect(method = "loadGlyph", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
    private int anthrax$hintedMetrics(FT_Face face, int glyphIndex, int flags) {
        if (isAnthraxUiFont(face)) flags = (flags & ~FT_LOAD_NO_HINTING) | FT_LOAD_BITMAP_METRICS_ONLY;
        return FreeType.FT_Load_Glyph(face, glyphIndex, flags);
    }

    private static boolean isAnthraxUiFont(FT_Face face) {
        try {
            String family = face.family_nameString();
            return family != null && family.startsWith("Segoe UI");
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
