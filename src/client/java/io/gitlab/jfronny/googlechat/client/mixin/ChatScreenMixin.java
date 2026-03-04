package io.gitlab.jfronny.googlechat.client.mixin;

import io.gitlab.jfronny.googlechat.GoogleChat;
import io.gitlab.jfronny.googlechat.GoogleChatConfig;
import io.gitlab.jfronny.googlechat.TranslationDirection;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    private static final Set<String> NO_TRANSLATE_COMMANDS = Set.of(
        "tellraw", "title", "data", "scoreboard", "team", "trigger", "execute", "recipe", "function"
    );
    
    private static final Map<String, String> COMMAND_ALIASES;
    static {
        COMMAND_ALIASES = new HashMap<>();
        COMMAND_ALIASES.put("w", "tell");
        COMMAND_ALIASES.put("msg", "tell");
        COMMAND_ALIASES.put("whisper", "tell");
        COMMAND_ALIASES.put("reply", "tell");
        COMMAND_ALIASES.put("t", "tell");
        COMMAND_ALIASES.put("r", "msg");
        COMMAND_ALIASES.put("me", "cemetery");
        COMMAND_ALIASES.put("bc", "broadcast");
        COMMAND_ALIASES.put("say", "broadcast");
        COMMAND_ALIASES.put("g", "global");
        COMMAND_ALIASES.put("l", "local");
        COMMAND_ALIASES.put("loc", "locate");
    }
    
    private static final Pattern SELECTOR_PATTERN = Pattern.compile("@[a-zA-Z][a-zA-Z0-9]*(\\[.*?])?");
    private static final Pattern QUOTED_ARG_PATTERN = Pattern.compile("(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')");
    private static final Pattern JSON_START_PATTERN = Pattern.compile("^\\s*\\{");
    private static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile(" {2,}");

    @ModifyVariable(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At(value = "HEAD"), argsOnly = true, ordinal = 0)
    String googlechat$translateChatText(String chatText) {
        if (!GoogleChatConfig.General.enabled) return chatText;
        if (chatText == null || chatText.isEmpty()) return chatText;
        
        if (chatText.startsWith("/")) {
            int firstSpace = chatText.indexOf(' ');
            int commandEnd = firstSpace > 0 ? firstSpace : chatText.length();
            
            String commandName = chatText.substring(1, commandEnd).toLowerCase();
            String baseCommand = COMMAND_ALIASES.getOrDefault(commandName, commandName);
            
            if (NO_TRANSLATE_COMMANDS.contains(baseCommand)) {
                return chatText;
            }
            
            if (firstSpace > 0) {
                String rawArgs = chatText.substring(firstSpace + 1);
                String translatedArgs = translateArgsWithProtection(rawArgs);
                
                return chatText.substring(0, 1) + commandName + " " + translatedArgs;
            }
            return chatText;
        }
        
        return GoogleChat.translateIfNeeded(chatText, TranslationDirection.C2S, true);
    }

    private String translateArgsWithProtection(String args) {
        if (args == null || args.isEmpty()) return args;
        
        if (JSON_START_PATTERN.matcher(args).find()) {
            return args;
        }
        
        if (args.contains("#")) {
            StringBuilder result = new StringBuilder();
            int lastEnd = 0;
            Pattern hashtagPattern = Pattern.compile("#\\S+");
            Matcher hashtagMatcher = hashtagPattern.matcher(args);
            
            while (hashtagMatcher.find()) {
                result.append(args, lastEnd, hashtagMatcher.start());
                result.append(hashtagMatcher.group());
                lastEnd = hashtagMatcher.end();
            }
            result.append(args.substring(lastEnd));
            args = result.toString();
        }
        
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        Matcher selectorMatcher = SELECTOR_PATTERN.matcher(args);
        
        while (selectorMatcher.find()) {
            result.append(args, lastEnd, selectorMatcher.start());
            result.append(selectorMatcher.group());
            lastEnd = selectorMatcher.end();
        }
        
        if (lastEnd == 0) {
            String translated = GoogleChat.translateIfNeeded(args, TranslationDirection.C2S, true);
            return preserveMultipleSpaces(args, translated);
        }
        
        result.append(args.substring(lastEnd));
        
        String remaining = result.toString();
        
        Matcher quotedMatcher = QUOTED_ARG_PATTERN.matcher(remaining);
        StringBuilder finalResult = new StringBuilder();
        lastEnd = 0;
        
        while (quotedMatcher.find()) {
            finalResult.append(remaining, lastEnd, quotedMatcher.start());
            
            String quoted = quotedMatcher.group();
            char quoteChar = quoted.charAt(0);
            String inner = quoted.substring(1, quoted.length() - 1);
            String translated = GoogleChat.translateIfNeeded(inner, TranslationDirection.C2S, true);
            finalResult.append(quoteChar).append(translated).append(quoteChar);
            lastEnd = quotedMatcher.end();
        }
        
        if (lastEnd == 0) {
            String translated = GoogleChat.translateIfNeeded(remaining, TranslationDirection.C2S, true);
            return preserveMultipleSpaces(args, translated);
        }
        
        finalResult.append(remaining.substring(lastEnd));
        
        return preserveMultipleSpaces(args, finalResult.toString());
    }

    private String preserveMultipleSpaces(String original, String translated) {
        if (original == null || translated == null) return translated;
        
        Matcher spacesMatcher = MULTIPLE_SPACES_PATTERN.matcher(original);
        if (!spacesMatcher.find()) return translated;
        
        spacesMatcher.reset();
        StringBuilder result = new StringBuilder(translated);
        int offset = 0;
        
        while (spacesMatcher.find()) {
            int pos = spacesMatcher.start() + offset;
            String spaces = spacesMatcher.group();
            int endPos = spacesMatcher.end() + offset;
            
            if (pos < result.length() && endPos <= result.length()) {
                String existing = result.substring(pos, endPos);
                if (!existing.equals(spaces)) {
                    result.replace(pos, endPos, spaces);
                }
            }
            offset += spaces.length() - (endPos - pos);
        }
        
        return result.toString();
    }
}
