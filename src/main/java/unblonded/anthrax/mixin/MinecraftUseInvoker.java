package unblonded.anthrax.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Auto Clicker: a vanilla right-click (blocks, entities and items alike) and its repeat delay. */
@Mixin(Minecraft.class)
public interface MinecraftUseInvoker {
    @Invoker("startUseItem") void anthrax$startUseItem();
    @Accessor("rightClickDelay") void anthrax$setRightClickDelay(int value);
}
