package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Chams: tells armour, glint and item render types apart by name. */
@Mixin(RenderType.class)
public interface RenderTypeNameAccessor {
    @Accessor("name") String anthrax$name();
}
