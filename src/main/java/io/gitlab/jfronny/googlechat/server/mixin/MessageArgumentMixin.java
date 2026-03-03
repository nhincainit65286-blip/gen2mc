package io.gitlab.jfronny.googlechat.server.mixin;

import com.mojang.brigadier.context.CommandContext;
import io.gitlab.jfronny.googlechat.GoogleChatConfig;
import io.gitlab.jfronny.googlechat.TranslationDirection;
import io.gitlab.jfronny.googlechat.server.TranslatableContainer;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MessageArgument.class)
public class MessageArgumentMixin {
    @Inject(method = "getMessage(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;)Lnet/minecraft/network/chat/Component;", at = @At("TAIL"), cancellable = true)
    private static void modifyMessage(CommandContext<CommandSourceStack> context, String name, CallbackInfoReturnable<Component> cir) {
        if (!GoogleChatConfig.General.enabled || !GoogleChatConfig.Advanced.translateMessageArguments) return;
        Component message = cir.getReturnValue();
        if (context.getSource().getPlayer() != null) { // Client messages should first be translated to the server language
            if (TranslationDirection.C2S.hasTarget()) {
                if (TranslationDirection.S2C.hasTarget()) {
                    // Do not translate back and forth
                    return;
                }
            }
            message = TranslatableContainer.translateAndLog(message, TranslationDirection.C2S);
        }
        // All messages should be translated to the client language before sending
        message = TranslatableContainer.translateAndLog(message, TranslationDirection.S2C);
        cir.setReturnValue(message);
    }
}
