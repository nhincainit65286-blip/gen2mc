package io.gitlab.jfronny.googlechat.client.mixin;

import io.gitlab.jfronny.googlechat.GoogleChat;
import io.gitlab.jfronny.googlechat.TranslationDirection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @ModifyVariable(method = "displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    Component googlechat$translateMessage(Component source) {
        return GoogleChat.translateIfNeeded(source, TranslationDirection.C2S, true);
    }
}
