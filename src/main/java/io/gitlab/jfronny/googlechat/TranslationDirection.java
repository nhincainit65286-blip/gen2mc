package io.gitlab.jfronny.googlechat;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public enum TranslationDirection {
    C2S,
    S2C;

    private static final Object REGEX_LOCK = new Object();
    private static volatile CachedRegex sendingRegex = new CachedRegex("", Pattern.compile(""));
    private static volatile CachedRegex receivingRegex = new CachedRegex("", Pattern.compile(""));

    public String source() {
        return switch (this) {
            case C2S -> GoogleChatConfig.General.clientLanguage;
            case S2C -> GoogleChatConfig.General.serverLanguage;
        };
    }

    public String target() {
        return switch (this) {
            case C2S -> GoogleChatConfig.General.serverLanguage;
            case S2C -> GoogleChatConfig.General.clientLanguage;
        };
    }

    public boolean hasTarget() {
        return !target().equals("auto");
    }

    public boolean shouldSkipOutright() {
        if (!GoogleChatConfig.General.enabled) return true;
        if (!hasTarget()) return true;
        return source().equalsIgnoreCase(target());
    }

    public boolean regexCanFilterNonBlankText() {
        boolean isSender = (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) == (this == TranslationDirection.C2S);
        String regex = isSender ? GoogleChatConfig.Processing.sendingRegex : GoogleChatConfig.Processing.receivingRegex;
        boolean isBlacklist = isSender ? GoogleChatConfig.Processing.sendingRegexIsBlacklist : GoogleChatConfig.Processing.receivingRegexIsBlacklist;
        return !regex.isEmpty() || !isBlacklist;
    }

    public boolean failsRegex(String text) {
        boolean isSender = (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) == (this == TranslationDirection.C2S);
        boolean isBlacklist = isSender ? GoogleChatConfig.Processing.sendingRegexIsBlacklist : GoogleChatConfig.Processing.receivingRegexIsBlacklist;
        Pattern pattern;
        try {
            pattern = regexPattern(isSender);
        } catch (PatternSyntaxException ignored) {
            return false;
        }
        return pattern.matcher(text).matches() == isBlacklist;
    }

    public static void onConfigChange() {
        synchronized (REGEX_LOCK) {
            sendingRegex = new CachedRegex("", Pattern.compile(""));
            receivingRegex = new CachedRegex("", Pattern.compile(""));
        }
    }

    private static Pattern regexPattern(boolean isSender) {
        String regex = isSender ? GoogleChatConfig.Processing.sendingRegex : GoogleChatConfig.Processing.receivingRegex;
        CachedRegex cached = isSender ? sendingRegex : receivingRegex;
        if (regex.equals(cached.source())) return cached.pattern();
        synchronized (REGEX_LOCK) {
            cached = isSender ? sendingRegex : receivingRegex;
            if (regex.equals(cached.source())) return cached.pattern();
            Pattern compiled = Pattern.compile(regex);
            CachedRegex updated = new CachedRegex(regex, compiled);
            if (isSender) sendingRegex = updated;
            else receivingRegex = updated;
            return compiled;
        }
    }

    private record CachedRegex(String source, Pattern pattern) {}

    record Split<T>(T c2s, T s2c) {
        public static <T> Split<T> of(Supplier<T> supply) {
            return new Split<>(supply.get(), supply.get());
        }

        public T get(TranslationDirection direction) {
            return direction == C2S ? c2s : s2c;
        }

        public Stream<T> stream() {
            return Stream.of(c2s, s2c);
        }
    }
}
