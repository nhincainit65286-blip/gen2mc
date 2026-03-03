package io.gitlab.jfronny.googlechat.client;

import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.gitlab.jfronny.googlechat.GoogleChat;
import io.gitlab.jfronny.googlechat.GoogleChatConfig;
import io.gitlab.jfronny.libjf.config.api.v2.ConfigHolder;
import io.gitlab.jfronny.libjf.config.api.v2.ConfigInstance;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.MessageArgument;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class GoogleChatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ConfigInstance ci = ConfigHolder.getInstance().get(GoogleChat.MOD_ID);
        if (ci != null
                && FabricLoader.getInstance().isModLoaded("fabric-key-binding-api-v1")
                && FabricLoader.getInstance().isModLoaded("fabric-lifecycle-events-v1")) {
            setupKeybind(ci);
        }
    }

    private static void setupKeybind(@NotNull ConfigInstance ci) {
        // Factored out to prevent loading classes if mods are not present
        KeyMapping keyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + GoogleChat.MOD_ID + ".toggle",
                InputConstants.Type.KEYSYM,
                -1,
                KeyMapping.Category.MULTIPLAYER
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyBinding.consumeClick()) {
                GoogleChatConfig.General.enabled = !GoogleChatConfig.General.enabled;
                ci.write();
            }
        });
    }

    public static <S> Optional<String> reconstitute(String source, CommandContextBuilder<S> results) {
        StringBuilder builder = new StringBuilder();
        for (ParsedCommandNode<S> node : results.getNodes()) {
            switch (node.getNode()) {
                case ArgumentCommandNode<?, ?> arg -> {
                    ParsedArgument<S, ?> pa = results.getArguments().get(arg.getName());
                    String datum = pa.getRange().get(source);
                    if (pa.getResult() instanceof MessageArgument.Message fmt) builder.append(fmt.text());
                    else builder.append(datum);
                    builder.append(' ');
                }
                case LiteralCommandNode<?> lit -> {
                    builder.append(lit.getLiteral());
                    builder.append(' ');
                }
                case RootCommandNode<?> root -> {}
                default -> {
                    return Optional.empty();
                }
            }
        }
        if (GoogleChatConfig.Advanced.debugLogs) {
            GoogleChat.LOGGER.info("Reconstituted command: {0} from {1}", builder.substring(0, builder.length() - 1), source);
        }
        return Optional.of(builder.substring(0, builder.length() - 1));
    }
}
