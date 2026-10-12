package unblonded.anthrax.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unblonded.anthrax.modules.client.Commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Chat suggestions for Anthrax commands. When the chat box starts with the command prefix, the input is
 * parsed with our dispatcher instead of the server's, so vanilla draws the suggestion list, the usage hint
 * ("<module> <setting> <value>"), red error text and argument colors exactly as it does for /commands.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private EditBox input;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private boolean currentParseIsCommand;
    @Shadow private boolean currentParseIsMessage;
    @Shadow private boolean keepSuggestions;

    @Shadow public abstract void hide();
    @Shadow public abstract boolean isVisible();
    @Shadow private void updateUsageInfo(ParseResults<ClientSuggestionProvider> parse, Suggestions suggestions) {}

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void anthrax$updateCommandInfo(CallbackInfo ci) {
        String prefix = Commands.prefix();
        String value = input.getValue();
        if (prefix.isEmpty() || !value.startsWith(prefix) || minecraft.player == null) return;
        ci.cancel();

        if (currentParse != null && !currentParse.getReader().getString().equals(value)) currentParse = null;
        // Tab-cycling sets keepSuggestions so the open list survives the text change it causes.
        if (!keepSuggestions) {
            input.setSuggestion(null);
            hide();
        }
        commandUsage.clear();

        if (currentParse == null) {
            StringReader reader = new StringReader(value);
            reader.setCursor(prefix.length());
            currentParse = Commands.DISPATCHER.parse(reader, minecraft.player.connection.getSuggestionsProvider());
            currentParseIsCommand = true;
            currentParseIsMessage = false;
        }

        int cursor = input.getCursorPosition();
        if (cursor >= prefix.length() && (!isVisible() || !keepSuggestions)) {
            ParseResults<ClientSuggestionProvider> parse = currentParse;
            CompletableFuture<Suggestions> future = Commands.DISPATCHER.getCompletionSuggestions(parse, cursor);
            pendingSuggestions = future;
            future.thenAccept(suggestions -> {
                if (pendingSuggestions == future && future.isDone()) updateUsageInfo(parse, suggestions);
            });
        }
    }
}
