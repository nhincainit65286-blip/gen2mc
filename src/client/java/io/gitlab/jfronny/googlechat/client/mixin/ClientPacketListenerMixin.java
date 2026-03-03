package io.gitlab.jfronny.googlechat.client.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedArgument;
import io.gitlab.jfronny.googlechat.GoogleChat;
import io.gitlab.jfronny.googlechat.GoogleChatConfig;
import io.gitlab.jfronny.googlechat.TranslationDirection;
import io.gitlab.jfronny.googlechat.client.GoogleChatClient;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.arguments.MessageArgument;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Shadow private CommandDispatcher<ClientSuggestionProvider> commands;
    @Shadow @Final private ClientSuggestionProvider suggestionsProvider;
    @Unique private final AtomicReference<ParseResults<ClientSuggestionProvider>> googlechat$lastResults = new AtomicReference<>(null);

    @ModifyVariable(method = "sendCommand(Ljava/lang/String;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    public String translateCommand(String command) {
        ParseResults<ClientSuggestionProvider> results = this.commands.parse(command, this.suggestionsProvider);
        if (!GoogleChatConfig.General.enabled || !GoogleChatConfig.Advanced.translateMessageArguments || !results.getExceptions().isEmpty()) return command;
        final boolean[] modified = {false};
        results.getContext().getArguments().replaceAll((key, value) -> {
            if (value.getResult() instanceof MessageArgument.Message fmt) {
                if (fmt.parts().length != 0) return value; // Selectors contain position information, which this would break
                fmt = new MessageArgument.Message(GoogleChat.translateIfNeeded(fmt.text(), TranslationDirection.C2S, true), new MessageArgument.Part[0]);
                modified[0] = true;
                return new ParsedArgument<>(value.getRange().getStart(), value.getRange().getEnd(), fmt);
            }
            return value;
        });
        if (!modified[0]) return command;
        return GoogleChatClient.reconstitute(command, results.getContext()).map(s -> {
            googlechat$lastResults.set(results);
            return s;
        }).orElse(command);
    }

    @Redirect(method = "sendCommand(Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;parse(Ljava/lang/String;Ljava/lang/Object;)Lcom/mojang/brigadier/ParseResults;", remap = false))
    public <S> ParseResults<S> googlechat$modifyArguments(CommandDispatcher<S> instance, String command, S source) {
        @SuppressWarnings("rawtypes") ParseResults results = googlechat$lastResults.getAndSet(null);
        if (results == null) return instance.parse(command, source);
        //noinspection unchecked
        return results;
    }
}
