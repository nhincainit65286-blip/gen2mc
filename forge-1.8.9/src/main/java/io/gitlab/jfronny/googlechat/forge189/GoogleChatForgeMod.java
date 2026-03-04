package io.gitlab.jfronny.googlechat.forge189;

import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientChatSentEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = GoogleChatForgeMod.MODID, name = GoogleChatForgeMod.NAME, version = GoogleChatForgeMod.VERSION, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public final class GoogleChatForgeMod {
    public static final String MODID = "googlechatforge";
    public static final String NAME = "GoogleChat Forge";
    public static final String VERSION = "1.0.0";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GoogleChatForgeConfig.init(event.getSuggestedConfigurationFile());
        GoogleChatForgeTranslator.reconfigure();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event) {
        if (!GoogleChatForgeConfig.enabled) return;
        if (event.type != 0) return;

        String original = event.message.getUnformattedText();
        String translated = GoogleChatForgeTranslator.translateIncoming(original);
        if (!translated.equals(original)) {
            event.message = new net.minecraft.util.ChatComponentText(translated);
        }
    }

    @SubscribeEvent
    public void onClientChatSent(ClientChatSentEvent event) {
        if (!GoogleChatForgeConfig.enabled) return;
        if (event.message == null || event.message.isEmpty()) return;
        if (event.message.charAt(0) == '/') return;

        String translated = GoogleChatForgeTranslator.translateOutgoing(event.message);
        if (!translated.equals(event.message)) {
            event.message = translated;
        }
    }
}
