package io.gitlab.jfronny.googlechat;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.util.function.Supplier;
import java.util.stream.Stream;

public enum TranslationDirection {
    C2S,
    S2C;

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
        return !hasTarget();
    }

    public boolean failsRegex(String text) {
        boolean isSender = (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) == (this == TranslationDirection.C2S);
        if (isSender) return text.matches(GoogleChatConfig.Processing.sendingRegex) == GoogleChatConfig.Processing.sendingRegexIsBlacklist;
        else return text.matches(GoogleChatConfig.Processing.receivingRegex) == GoogleChatConfig.Processing.receivingRegexIsBlacklist;
    }

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
