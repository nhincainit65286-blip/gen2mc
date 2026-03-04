package io.gitlab.jfronny.googlechat.client.mixin;

import com.mojang.authlib.GameProfile;
import io.gitlab.jfronny.googlechat.*;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.GameNarrator;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow protected abstract void narrateChatMessage(ChatType.Bound params, Component message);

    //TODO somehow modify applyChatDecoration, since that is the only method that knows the real text

    @Inject(method = "<init>", at = @At("TAIL"))
    void initialize(Minecraft minecraft, CallbackInfo ci) {
        delayedMessageQueue = new ConcurrentLinkedDeque<>(delayedMessageQueue);
    }

    @Mutable @Shadow @Final private Deque<ChatListener.Message> delayedMessageQueue;
    @Unique private static final ExecutorService googlechat$executor = Executors.newVirtualThreadPerTaskExecutor();
    @Unique CompletableFuture<Void> googlechat$currentFuture = CompletableFuture.completedFuture(null);
    @Unique ThreadLocal<GameProfile> sender = new ThreadLocal<>();

    @Redirect(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void googlechat$handleSystemMessage$setOverlayMessage(Gui instance, Component message, boolean tinted) {
        TranslationDirection direction = googlechat$displayDirection();
        if (direction != null) googlechat$scheduleTranslation(message, direction, msg -> instance.setOverlayMessage(msg, tinted));
        else instance.setOverlayMessage(message, tinted);
    }

    @Redirect(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;)V"))
    private void googlechat$handleSystemMessage$addMessage(ChatComponent instance, Component text) {
        TranslationDirection direction = googlechat$displayDirection();
        if (direction != null) googlechat$scheduleTranslation(text, direction, instance::addMessage);
        else instance.addMessage(text);
    }

    @Redirect(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GameNarrator;saySystemQueued(Lnet/minecraft/network/chat/Component;)V"))
    private void googlechat$handleSystemMessage$narrateSystemMessage(GameNarrator instance, Component text) {
        TranslationDirection direction = googlechat$displayDirection();
        if (direction != null) googlechat$scheduleTranslation(text, direction, instance::saySystemQueued);
        else instance.saySystemQueued(text);
    }

    @Redirect(method = "method_45745", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;)V"))
    private void googlechat$handleDisguisedChatMessage$addMessage(ChatComponent instance, Component text) {
        TranslationDirection direction = googlechat$displayDirection();
        if (direction != null) googlechat$scheduleTranslation(text, direction, instance::addMessage);
        else instance.addMessage(text);
    }

    @Redirect(method = "method_45745", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;narrateChatMessage(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/Component;)V"))
    private void googlechat$handleDisguisedChatMessage$narrate(ChatListener instance, ChatType.Bound params, Component message) {
        if (instance != (Object) this) GoogleChat.LOGGER.warn("Mismatched message handler in handleSystemMessage");
        TranslationDirection direction = googlechat$displayDirection();
        if (direction != null) googlechat$scheduleTranslation(message, direction, msg -> narrateChatMessage(params, msg));
        else narrateChatMessage(params, message);
    }

    @Inject(method = "handlePlayerChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/network/chat/ChatType$Bound;)V", at = @At("HEAD"))
    private void googlechat$handlePlayerChatMessage$extractSender(PlayerChatMessage message, GameProfile sender, ChatType.Bound params, CallbackInfo ci) {
        this.sender.set(sender);
    }

    @Redirect(method = "showMessageToPlayer(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/network/chat/Component;Lcom/mojang/authlib/GameProfile;ZLjava/time/Instant;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V"))
    private void googlechat$showMessageToPlayer$addMessage(ChatComponent instance, Component message, MessageSignature signature, GuiMessageTag indicator) {
        TranslationDirection direction = googlechat$displayDirection();
        if (direction != null) googlechat$scheduleTranslation(message, direction, msg -> instance.addMessage(msg, signature, indicator));
        else instance.addMessage(message, signature, indicator);
    }

    @Redirect(method = "showMessageToPlayer(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/network/chat/Component;Lcom/mojang/authlib/GameProfile;ZLjava/time/Instant;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;narrateChatMessage(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/Component;)V"))
    private void googlechat$showMessageToPlayer$narrate(ChatListener instance, ChatType.Bound params, Component message) {
        if (instance != (Object) this) GoogleChat.LOGGER.warn("Mismatched message handler in handleSystemMessage");
        TranslationDirection direction = googlechat$displayDirection();
        if (direction != null) googlechat$scheduleTranslation(message, direction, msg -> narrateChatMessage(params, msg));
        else narrateChatMessage(params, message);
    }

    @Inject(method = "showMessageToPlayer(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/network/chat/Component;Lcom/mojang/authlib/GameProfile;ZLjava/time/Instant;)Z", at = @At("RETURN"))
    private void googlechat$showMessageToPlayer$clearSender(ChatType.Bound params, PlayerChatMessage message, Component decorated, GameProfile sender, boolean onlyShowSecureChat, Instant receptionTimestamp, CallbackInfoReturnable<Boolean> cir) {
        this.sender.remove();
    }

    @Unique
    private Component googlechat$translate(Component message, TranslationDirection direction) {
        return GoogleChat.translateIfNeeded(message, direction, true);
    }

    @Unique
    private void googlechat$scheduleTranslation(Component message, TranslationDirection direction, Consumer<Component> runnable) {
        if (!GoogleChatConfig.Advanced.async) runnable.accept(googlechat$translate(message, direction));
        else {
            CompletableFuture<Component> translated = CompletableFuture.supplyAsync(() -> googlechat$translate(message, direction), googlechat$executor);
            googlechat$currentFuture = googlechat$currentFuture
                .handle((_1, _2) -> (Void) null)
                .thenCompose(_1 -> translated)
                .handle((msg, throwable) -> {
                    if (throwable != null) {
                        GoogleChat.LOGGER.error("Something went wrong while processing a message", throwable);
                        return message;
                    }
                    return msg;
                })
                .thenAccept(msg -> delayedMessageQueue.add(new ChatListener.Message(null, () -> {
                    runnable.accept(msg);
                    return false;
                })))
                .exceptionally(throwable -> {
                    GoogleChat.LOGGER.error("Something went wrong while queuing a translated message", throwable);
                    return null;
                });
        }
    }

    @Unique
    private TranslationDirection googlechat$displayDirection() {
        if (!GoogleChatConfig.General.enabled) return null;
        if (minecraft == null || minecraft.player == null) return null;
        var sender = this.sender.get();
        if (sender == null || !sender.id().equals(minecraft.player.getUUID())) {
            return TranslationDirection.S2C;
        }
        return TranslationDirection.C2S;
    }
}
