package io.gitlab.jfronny.googlechat;

import io.gitlab.jfronny.commons.serialize.annotations.Ignore;
import io.gitlab.jfronny.libjf.config.api.v2.*;
import io.gitlab.jfronny.libjf.config.api.v2.dsl.CategoryBuilder;
import io.gitlab.jfronny.libjf.config.api.v2.dsl.ConfigBuilder;
import net.fabricmc.api.*;
import net.fabricmc.loader.api.*;

import static io.gitlab.jfronny.libjf.config.api.v2.dsl.Migration.of;

@JfConfig(tweaker = GoogleChatConfig.class)
public class GoogleChatConfig {
    @Category(referencedConfigs = "libjf-translate-v1", tweaker = General.class)
    public static class General {
        @Entry public static boolean enabled = true;
        @Entry public static String serverLanguage = "auto";
        @Entry public static String clientLanguage = "en";
        @Entry public static DisplayMode displayMode = DisplayMode.REPLACE;

        public enum DisplayMode {
            REPLACE,
            TOOLTIP,
            APPEND,
        }

        @Preset
        public static void client() {
            enabled = true;
            if (!serverLanguage.equals("auto")) {
                serverLanguage = "auto";
                clientLanguage = "en";
            }
        }

        @Preset
        public static void server() {
            enabled = true;
            if (!clientLanguage.equals("auto")) {
                clientLanguage = "auto";
                serverLanguage = "en";
            }
        }

        public static CategoryBuilder<?> tweak(CategoryBuilder<?> builder) {
            return builder
                    .addMigration("translationTooltip", of(reader -> translationTooltip(reader.nextBoolean())));
        }

        private static void translationTooltip(boolean value) {
            General.displayMode = value ? DisplayMode.TOOLTIP : DisplayMode.REPLACE;
        }
    }

    @Category
    public static class Processing {
        @Entry public static boolean desugar = false;
        @Entry public static String receivingRegex = "";
        @Entry public static boolean receivingRegexIsBlacklist = true;
        @Entry public static String sendingRegex = "";
        @Entry public static boolean sendingRegexIsBlacklist = true;
    }

    @Category
    public static class Advanced {
        @Entry(min = 1, max = 1024) public static int cacheSize = 256;
        @Entry public static boolean async = true;
        @Entry(min = 1, max = 64) public static int maxParallelRequests = 4;
        @Entry(min = 0, max = 8) public static int retryAttempts = 2;
        @Entry(min = 0, max = 5000) public static int retryBackoffMs = 150;
        @Entry public static boolean circuitBreaker = true;
        @Entry(min = 1, max = 100) public static int circuitBreakerFailures = 6;
        @Entry(min = 1, max = 300) public static int circuitBreakerCooldownSeconds = 20;
        @Entry public static boolean adaptiveParallelism = true;
        @Entry public static boolean translateMessageArguments = true;
        @Entry public static boolean debugLogs = FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Ignore private static boolean initial = true;
    @Verifier
    public static void verify() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER && !General.clientLanguage.equals("auto")) {
            System.err.println("""
                    Your client language is not set to "auto" and you are using a server.
                    This setup is not recommended! Please set up GoogleChat according to its documentation!""");
        }
        if (!initial) GoogleChat.onConfigChange();
        initial = false;
    }

    public static ConfigBuilder<?> tweak(ConfigBuilder<?> builder) {
        return builder
                .addMigration("enabled", of(reader -> General.enabled = reader.nextBoolean()))
                .addMigration("serverLanguage", of(reader -> General.serverLanguage = reader.nextString()))
                .addMigration("clientLanguage", of(reader -> General.clientLanguage = reader.nextString()))
                .addMigration("translationTooltip", of(reader -> General.translationTooltip(reader.nextBoolean())))
                .addMigration("desugar", of(reader -> Processing.desugar = reader.nextBoolean()))
                .addMigration("receivingRegex", of(reader -> Processing.receivingRegex = reader.nextString()))
                .addMigration("receivingRegexIsBlacklist", of(reader -> Processing.receivingRegexIsBlacklist = reader.nextBoolean()))
                .addMigration("sendingRegex", of(reader -> Processing.sendingRegex = reader.nextString()))
                .addMigration("sendingRegexIsBlacklist", of(reader -> Processing.sendingRegexIsBlacklist = reader.nextBoolean()))
                .addMigration("cacheSize", of(reader -> Advanced.cacheSize = reader.nextInt()))
                .addMigration("debugLogs", of(reader -> Advanced.debugLogs = reader.nextBoolean()));
    }

    static {
        JFC_GoogleChatConfig.ensureInitialized();
    }
}
