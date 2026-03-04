package io.gitlab.jfronny.googlechat.client.mixin;

import io.gitlab.jfronny.googlechat.GoogleChat;
import io.gitlab.jfronny.googlechat.GoogleChatConfig;
import io.gitlab.jfronny.googlechat.TranslationDirection;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @ModifyVariable(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At(value = "HEAD"), argsOnly = true, ordinal = 0)
    String googlechat$translateChatText(String chatText) {
        if (!GoogleChatConfig.General.enabled) return chatText;
        if (chatText == null || chatText.isEmpty()) return chatText;
        if (chatText.startsWith("/")) {
            // Command argument translation is handled in ClientPacketListenerMixin after Brigadier parsing.
            return chatText;
        }
        return GoogleChat.translateIfNeeded(chatText, TranslationDirection.C2S, true);
    }
}
