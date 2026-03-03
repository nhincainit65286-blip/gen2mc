package io.gitlab.jfronny.googlechat;

import io.gitlab.jfronny.commons.io.cache.FixedSizeMap;
import io.gitlab.jfronny.commons.logger.SystemLoggerPlus;
import io.gitlab.jfronny.commons.throwable.Coerce;
import io.gitlab.jfronny.commons.throwable.Try;
import io.gitlab.jfronny.libjf.translate.api.Language;
import io.gitlab.jfronny.libjf.translate.api.TranslateService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GoogleChat implements ModInitializer {
    public static final String MOD_ID = "google-chat";
    public static final SystemLoggerPlus LOGGER = SystemLoggerPlus.forName(MOD_ID);
    public static TranslateService<?> TRANSLATE_SERVICE;
    private static final boolean IS_SERVER = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;

    private static final TranslationDirection.Split<Map<Component, Component>> finalTexts = TranslationDirection.Split.of(() -> new FixedSizeMap<>(GoogleChatConfig.Advanced.cacheSize));
    private static final TranslationDirection.Split<Map<ComponentContents, ComponentContents>> textContents = TranslationDirection.Split.of(() -> new FixedSizeMap<>(GoogleChatConfig.Advanced.cacheSize));
    private static final TranslationDirection.Split<Map<String, String>> strings = TranslationDirection.Split.of(() -> new FixedSizeMap<>(GoogleChatConfig.Advanced.cacheSize));

    @Override
    public void onInitialize() {
        ForkJoinPool.commonPool().execute(Try.handle(Coerce.runnable(() -> TRANSLATE_SERVICE = TranslateService.getConfigured()), e -> LOGGER.error("Could not initialize translation service", e)));
    }

    public static void onConfigChange() {
        Stream.of(finalTexts, textContents, strings).flatMap(TranslationDirection.Split::stream).forEach(map -> {
            synchronized (map) {
                map.clear();
            }
        });
    }

    public static Component translateIfNeeded(Component source, TranslationDirection direction, boolean respectRegex) {
        if (source == null) return null;
        if (direction.shouldSkipOutright()) return source;
        String sourceString = toString(source);
        if (respectRegex && direction.failsRegex(sourceString)) return source;
        return computeIfAbsent2(finalTexts.get(direction), source, t -> {
            MutableComponent translated = GoogleChatConfig.Processing.desugar
                    ? Component.literal(translateIfNeeded(sourceString, direction, true))
                    : doTranslateIfNeeded(t, direction);
            if (GoogleChatConfig.Advanced.debugLogs) LOGGER.info("Translated {0} to {1}", sourceString, toString(translated));
            return switch (GoogleChatConfig.General.displayMode) {
                case REPLACE -> {
                    if (translated.getStyle().getHoverEvent() == null) {
                        var hoverText = Component.translatableWithFallback("google-chat.display.original", "Original: %1$s", t);
                        yield translated.withStyle(style -> addHover(style, hoverText));
                    } else {
                        yield translated;
                    }
                }
                case TOOLTIP -> {
                    var hoverText = Component.translatableWithFallback("google-chat.display.translated", "Translated: %1$s", translated);
                    yield t.copy().withStyle(style -> addHover(style, hoverText));
                }
                case APPEND -> Component.translatableWithFallback("google-chat.display.appended", "%1$s§r §7§o(Translated: %2$s§r§7§o)", t, translated.withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(true)));
            };
        });
    }

    private static MutableComponent doTranslateIfNeeded(Component source, TranslationDirection direction) {
        MutableComponent translated = MutableComponent.create(translateIfNeeded(source.getContents(), direction, false))
                .setStyle(source.getStyle());
        for (Component sibling : source.getSiblings()) {
            translated.append(doTranslateIfNeeded(sibling, direction));
        }
        return translated;
    }

    private static String toString(Component text) {
        StringBuilder sb = new StringBuilder();
        text.getVisualOrderText().accept((index, style, codePoint) -> {
            sb.append((char)codePoint);
            return true;
        });
        return sb.toString();
    }

    public static ComponentContents translateIfNeeded(ComponentContents source, TranslationDirection direction, boolean respectRegex) {
        if (source == null || source == PlainTextContents.EMPTY) return source;
        if (direction.shouldSkipOutright()) return source;
        String sourceString = toString(source);
        if (respectRegex && direction.failsRegex(sourceString)) return source;
        return computeIfAbsent2(textContents.get(direction), source, t -> switch (t) {
            case TranslatableContents tx -> {
                // TranslatableText is not translated, but its fallback and arguments are
                Object[] args = Arrays.stream(tx.getArgs()).map(arg -> switch (arg) {
                    case Component tx1 -> doTranslateIfNeeded(tx1, direction);
                    case ComponentContents tx1 -> translateIfNeeded(tx1, direction, false);
                    case String tx1 -> translateIfNeeded(tx1, direction, false);
                    case null -> null;
                    default -> {
                        if (GoogleChatConfig.Advanced.debugLogs) LOGGER.warn("Unhandled argument type: {0} ({1}})", arg.getClass().toString(), arg.toString());
                        yield arg;
                    }
                }).toArray();
                yield new TranslatableContents(tx.getKey(), translateIfNeeded(tx.getFallback(), direction, false), args);
            }
            case PlainTextContents.LiteralContents(var string) ->
                    new PlainTextContents.LiteralContents(translateIfNeeded(string, direction, false));
            case null, default -> {
                if (GoogleChatConfig.Advanced.debugLogs) LOGGER.warn("Unhandled text type: {0} ({1}})", source.getClass().toString(), source.toString());
                yield t;
            }
        });
    }

    private static String toString(ComponentContents text) {
        StringBuilder sb = new StringBuilder();
        text.visit(asString -> {
            sb.append(asString);
            return Optional.empty();
        });
        return sb.toString();
    }

    private static Style addHover(Style style, Component hoverText) {
        return style.withHoverEvent(new HoverEvent.ShowText(hoverText));
    }

    private static final Pattern SURROUNDING_SPACE_PATTERN = Pattern.compile("^(\\s*)(.*\\S+)(\\s*)$", Pattern.MULTILINE);
    public static String translateIfNeeded(String source, TranslationDirection direction, boolean respectRegex) {
        if (source == null || source.isBlank()) return source;
        if (direction.shouldSkipOutright()) return source;
        if (respectRegex && direction.failsRegex(source)) return source;
        return computeIfAbsent2(strings.get(direction), source, t -> {
            try {
                Matcher m = SURROUNDING_SPACE_PATTERN.matcher(source);
                if (!m.find()) return source;
                // Ignore generics since this is apparently not something java supports (even with var)
                @SuppressWarnings("rawtypes") TranslateService svc = GoogleChat.TRANSLATE_SERVICE;
                if (svc == null) throw new NullPointerException("Translate service uninitialized");
                Language sourceLang = svc.parseLang(direction.source());
                Language targetLang = svc.parseLang(direction.target());
                //noinspection unchecked
                return m.group(1) + svc.translate(m.group(2), sourceLang, targetLang) + m.group(3);
            } catch (Throwable e) {
                LOGGER.error("Could not translate text: {0}", e, source);
                return source;
            }
        });
    }

    private static <K, V> V computeIfAbsent2(Map<K, V> map, K key, Function<K, V> compute) {
        if (!GoogleChatConfig.Advanced.async && !IS_SERVER) return map.computeIfAbsent(key, compute);
        synchronized (map) {
            if (map.containsKey(key)) return map.get(key);
            V value = compute.apply(key);
            map.put(key, value);
            return value;
        }
    }
}
