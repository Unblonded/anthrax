package unblonded.anthrax.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Chams: the package-private RenderType factory, for the see-through flat-colour type. */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Invoker("create")
    static RenderType anthrax$create(String name, RenderSetup setup) { throw new AssertionError(); }
}
