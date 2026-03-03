package io.gitlab.jfronny.googlechat.server;

import io.gitlab.jfronny.googlechat.*;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class GoogleChatServer implements DedicatedServerModInitializer, ChatDecorator {
    @Override
    public void onInitializeServer() {
        // Default phase is executed between CONTENT and STYLING
        // Perform translation there instead of during CONTENT to better support other mods (such as chat-transform)
        // If this causes an incompatibility, I'll add my own phase
        ServerMessageDecoratorEvent.EVENT.register(Event.DEFAULT_PHASE, this);
    }

    @Override
    public Component decorate(@Nullable ServerPlayer sender, Component original) {
        if (!GoogleChatConfig.General.enabled) return original;
        if (!GoogleChatConfig.Advanced.async) {
            return decorate(sender, new TranslatableContainer.Sync(original)).text();
        }
        try {
            return decorate(sender, new TranslatableContainer.Async(CompletableFuture.completedFuture(original)))
                        .text()
                        .exceptionally(e -> {
                            GoogleChat.LOGGER.error("Could not compute translation", e);
                            return original;
                        })
                        .get();
        } catch (InterruptedException | ExecutionException e) {
            GoogleChat.LOGGER.error("Could not synchronize async translation for synchronous decorator", e);
            return original;
        }
    }

    private <K, T extends TranslatableContainer<K, T>> T decorate(@Nullable ServerPlayer sender, T original) {
        T message = original;
        if (sender != null) {  // Client messages should first be translated to the server language
            if (TranslationDirection.C2S.hasTarget()) {
                if (TranslationDirection.S2C.hasTarget()) {
                    // Do not translate back and forth
                    return message;
                }
            }
            message = message.translate(TranslationDirection.C2S);
        }
        // All messages should be translated to the client language before sending
        message = message.translate(TranslationDirection.S2C);
        return message;
    }
}
