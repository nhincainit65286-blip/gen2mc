package io.gitlab.jfronny.googlechat.forge189;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

@Mod(modid = GoogleChatForgeMod.MODID, name = GoogleChatForgeMod.NAME, version = GoogleChatForgeMod.VERSION, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public final class GoogleChatForgeMod {
    public static final String MODID = "googlechatforge";
    public static final String NAME = "GoogleChat Forge";
    public static final String VERSION = "1.0.0";
    private static final Field CHAT_INPUT_FIELD = ReflectionHelper.findField(GuiChat.class, "inputField", "field_146415_a");

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
            event.message = new ChatComponentText(translated);
        }
    }

    @SubscribeEvent
    public void onGuiKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!GoogleChatForgeConfig.enabled) return;
        if (!(event.gui instanceof GuiChat)) return;
        if (!Keyboard.getEventKeyState()) return;

        int key = Keyboard.getEventKey();
        if (key != Keyboard.KEY_RETURN && key != Keyboard.KEY_NUMPADENTER) return;

        String message = readChatInput((GuiChat) event.gui);
        if (message == null || message.isEmpty()) return;
        if (message.charAt(0) == '/') return;

        String translated = GoogleChatForgeTranslator.translateOutgoing(message);
        if (translated.equals(message)) return;

        event.setCanceled(true);

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        String trimmed = translated.trim();
        if (trimmed.isEmpty()) {
            mc.displayGuiScreen(null);
            return;
        }

        mc.ingameGUI.getChatGUI().addToSentMessages(trimmed);
        mc.thePlayer.sendChatMessage(trimmed);
        mc.displayGuiScreen(null);
    }

    private static String readChatInput(GuiChat guiChat) {
        try {
            GuiTextField input = (GuiTextField) CHAT_INPUT_FIELD.get(guiChat);
            return input == null ? "" : input.getText();
        } catch (IllegalAccessException ignored) {
            return "";
        }
    }
}
