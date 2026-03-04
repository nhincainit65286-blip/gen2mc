package io.gitlab.jfronny.googlechat.forge189;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class GoogleChatForgeConfig {
    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_ADVANCED = "advanced";

    private static Configuration config;

    public static boolean enabled = true;
    public static String incomingSourceLanguage = "auto";
    public static String incomingTargetLanguage = "en";
    public static String outgoingSourceLanguage = "vi";
    public static String outgoingTargetLanguage = "en";
    public static boolean translateOwnMessagesInChat = true;
    public static int cacheSize = 512;
    public static int requestTimeoutMs = 6000;
    public static int maxParallelRequests = 4;
    public static boolean debugLogs = false;

    private GoogleChatForgeConfig() {
    }

    public static void init(File file) {
        config = new Configuration(file);
        sync();
    }

    public static void sync() {
        if (config == null) return;

        enabled = config.getBoolean("enabled", CATEGORY_GENERAL, enabled, "Enable chat translation");
        incomingSourceLanguage = config.getString("incomingSourceLanguage", CATEGORY_GENERAL, incomingSourceLanguage, "Source language for received chat, use auto to detect");
        incomingTargetLanguage = config.getString("incomingTargetLanguage", CATEGORY_GENERAL, incomingTargetLanguage, "Target language for received chat");
        outgoingSourceLanguage = config.getString("outgoingSourceLanguage", CATEGORY_GENERAL, outgoingSourceLanguage, "Source language for your sent chat, use auto to detect");
        outgoingTargetLanguage = config.getString("outgoingTargetLanguage", CATEGORY_GENERAL, outgoingTargetLanguage, "Target language for your sent chat");
        translateOwnMessagesInChat = config.getBoolean("translateOwnMessagesInChat", CATEGORY_GENERAL, translateOwnMessagesInChat, "Replace your own displayed message with translated text");

        cacheSize = config.getInt("cacheSize", CATEGORY_ADVANCED, cacheSize, 64, 8192, "LRU cache size for translated strings");
        requestTimeoutMs = config.getInt("requestTimeoutMs", CATEGORY_ADVANCED, requestTimeoutMs, 1000, 30000, "HTTP timeout in milliseconds");
        maxParallelRequests = config.getInt("maxParallelRequests", CATEGORY_ADVANCED, maxParallelRequests, 1, 32, "Maximum concurrent translation requests");
        debugLogs = config.getBoolean("debugLogs", CATEGORY_ADVANCED, debugLogs, "Enable debug logs");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
