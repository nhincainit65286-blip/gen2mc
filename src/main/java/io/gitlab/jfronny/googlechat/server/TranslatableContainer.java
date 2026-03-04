package io.gitlab.jfronny.googlechat.server;

import io.gitlab.jfronny.googlechat.*;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public sealed interface TranslatableContainer<T, S extends TranslatableContainer<T, S>> {
    S translate(TranslationDirection direction);

    ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    record Sync(Component text) implements TranslatableContainer<Component, Sync> {
        @Override
        public Sync translate(TranslationDirection direction) {
            return new Sync(translateAndLog(text, direction));
        }
    }

    record Async(CompletableFuture<Component> text) implements TranslatableContainer<CompletableFuture<Component>, Async> {
        @Override
        public Async translate(TranslationDirection direction) {
            return new Async(text.thenApplyAsync(msg -> translateAndLog(msg, direction), EXECUTOR));
        }
    }

    static Component translateAndLog(final Component source, final TranslationDirection direction) {
        var translated = GoogleChat.translateIfNeeded(source, direction, true);
        if (GoogleChatConfig.Advanced.debugLogs) GoogleChat.LOGGER.info("Applied C2S translation from {0} to {1}", source, translated);
        return translated;
    }
}
