package io.gitlab.jfronny.googlechat.client.mixin;

import io.gitlab.jfronny.googlechat.GoogleChat;
import io.gitlab.jfronny.googlechat.GoogleChatConfig;
import io.gitlab.jfronny.googlechat.TranslationDirection;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    private static final Set<String> NO_TRANSLATE_COMMANDS = Set.of(
        "tellraw", "title", "data", "scoreboard", "team", "trigger"
    );
    private static final Pattern SELECTOR_PATTERN = Pattern.compile("@[a-zA-Z][a-zA-Z0-9]*(\\[.*?])?");
    private static final Pattern QUOTED_ARG_PATTERN = Pattern.compile("(\"[^\"]*\"|'[^']*')");

    @ModifyVariable(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At(value = "HEAD"), argsOnly = true, ordinal = 0)
    String googlechat$translateChatText(String chatText) {
        if (!GoogleChatConfig.General.enabled) return chatText;
        
        if (chatText.startsWith("/")) {
            int spaceIndex = chatText.indexOf(' ');
            if (spaceIndex > 0) {
                String command = chatText.substring(1, spaceIndex).toLowerCase();
                
                if (NO_TRANSLATE_COMMANDS.contains(command)) {
                    return chatText;
                }
                
                String rawArgs = chatText.substring(spaceIndex + 1);
                String translatedArgs = translateArgsWithSelectors(rawArgs);
                return command + " " + translatedArgs;
            }
            return chatText;
        }
        
        return GoogleChat.translateIfNeeded(chatText, TranslationDirection.C2S, true);
    }

    private String translateArgsWithSelectors(String args) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        Matcher selectorMatcher = SELECTOR_PATTERN.matcher(args);
        
        while (selectorMatcher.find()) {
            result.append(args, lastEnd, selectorMatcher.start());
            result.append(selectorMatcher.group());
            lastEnd = selectorMatcher.end();
        }
        
        if (lastEnd == 0) {
            return GoogleChat.translateIfNeeded(args, TranslationDirection.C2S, true);
        }
        
        result.append(args.substring(lastEnd));
        
        String remaining = result.toString();
        
        Matcher quotedMatcher = QUOTED_ARG_PATTERN.matcher(remaining);
        StringBuilder finalResult = new StringBuilder();
        lastEnd = 0;
        
        while (quotedMatcher.find()) {
            finalResult.append(remaining, lastEnd, quotedMatcher.start());
            
            String quoted = quotedMatcher.group();
            if (quoted.startsWith("\"") || quoted.startsWith("'")) {
                String inner = quoted.substring(1, quoted.length() - 1);
                String translated = GoogleChat.translateIfNeeded(inner, TranslationDirection.C2S, true);
                finalResult.append(quoted.charAt(0)).append(translated).append(quoted.charAt(0));
            } else {
                finalResult.append(quoted);
            }
            lastEnd = quotedMatcher.end();
        }
        
        if (lastEnd == 0) {
            return GoogleChat.translateIfNeeded(remaining, TranslationDirection.C2S, true);
        }
        
        finalResult.append(remaining.substring(lastEnd));
        
        return finalResult.toString();
    }
}
