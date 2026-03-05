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
import java.util.concurrent.*;
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
    private static final TranslationDirection.Split<Map<Component, CompletableFuture<Component>>> finalTextsPending = TranslationDirection.Split.of(HashMap::new);
    private static final TranslationDirection.Split<Map<ComponentContents, CompletableFuture<ComponentContents>>> textContentsPending = TranslationDirection.Split.of(HashMap::new);
    private static final TranslationDirection.Split<Map<String, CompletableFuture<String>>> stringsPending = TranslationDirection.Split.of(HashMap::new);
    private static final TranslationDirection.Split<Map<String, String>> stringsBatch = TranslationDirection.Split.of(() -> new FixedSizeMap<>(GoogleChatConfig.Advanced.cacheSize));
    private static volatile ParsedLanguages c2sLanguages = ParsedLanguages.empty();
    private static volatile ParsedLanguages s2cLanguages = ParsedLanguages.empty();
    private static volatile int maxParallelRequests = Math.max(1, GoogleChatConfig.Advanced.maxParallelRequests);
    private static volatile Semaphore requestLimiter = new Semaphore(maxParallelRequests);
    private static final ExecutorService translationExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private static final Object breakerLock = new Object();
    private static volatile int consecutiveFailures = 0;
    private static volatile long circuitOpenUntilMs = 0L;
    private static volatile int dynamicParallelLimit = Math.max(1, GoogleChatConfig.Advanced.maxParallelRequests);
    private static int activeRequests = 0;

    @Override
    public void onInitialize() {
        onConfigChange();
        ForkJoinPool.commonPool().execute(Try.handle(Coerce.runnable(() -> TRANSLATE_SERVICE = TranslateService.getConfigured()), e -> LOGGER.error("Could not initialize translation service", e)));
    }

    public static void onConfigChange() {
        Stream.<TranslationDirection.Split<? extends Map<?, ?>>>of(finalTexts, textContents, strings, finalTextsPending, textContentsPending, stringsPending)
                .flatMap(TranslationDirection.Split::stream)
                .forEach(map -> {
            synchronized (map) {
                map.clear();
            }
        });
        Stream.of(stringsBatch).flatMap(TranslationDirection.Split::stream).forEach(map -> {
            synchronized (map) {
                map.clear();
            }
        });
        TranslationDirection.onConfigChange();
        updateParsedLanguages();
        synchronized (breakerLock) {
            consecutiveFailures = 0;
            circuitOpenUntilMs = 0L;
            dynamicParallelLimit = Math.max(1, GoogleChatConfig.Advanced.maxParallelRequests);
        }
        updateRequestLimiter();
    }

    public static Component translateIfNeeded(Component source, TranslationDirection direction, boolean respectRegex) {
        if (source == null) return null;
        if (direction.shouldSkipOutright()) return source;
        String sourceString = toString(source);
        if (respectRegex && direction.regexCanFilterNonBlankText() && direction.failsRegex(sourceString)) return source;
        return computeIfAbsent2(finalTexts.get(direction), finalTextsPending.get(direction), source, t -> {
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
        List<Component> translatedSiblings = mapPossiblyAsync(source.getSiblings(), sibling -> doTranslateIfNeeded(sibling, direction));
        translatedSiblings.forEach(translated::append);
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
        if (respectRegex && direction.regexCanFilterNonBlankText() && direction.failsRegex(sourceString)) return source;
        return computeIfAbsent2(textContents.get(direction), textContentsPending.get(direction), source, t -> switch (t) {
            case TranslatableContents tx -> {
                // TranslatableText is not translated, but its fallback and arguments are
                Object[] args = mapPossiblyAsync(Arrays.asList(tx.getArgs()), arg -> switch (arg) {
                    case Component tx1 -> doTranslateIfNeeded(tx1, direction);
                    case ComponentContents tx1 -> translateIfNeeded(tx1, direction, false);
                    case String tx1 -> translateIfNeededFromBatch(tx1, direction, false);
                    case null -> null;
                    default -> {
                        if (GoogleChatConfig.Advanced.debugLogs) LOGGER.warn("Unhandled argument type: {0} ({1})", arg.getClass().toString(), arg.toString());
                        yield arg;
                    }
                }).toArray();
                yield new TranslatableContents(tx.getKey(), translateIfNeeded(tx.getFallback(), direction, false), args);
            }
            case PlainTextContents.LiteralContents(var string) ->
                    new PlainTextContents.LiteralContents(translateIfNeeded(string, direction, false));
            case null, default -> {
                if (GoogleChatConfig.Advanced.debugLogs) LOGGER.warn("Unhandled text type: {0} ({1})", source.getClass().toString(), source.toString());
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

    private static <T, R> List<R> mapPossiblyAsync(Collection<T> source, Function<T, R> mapper) {
        if (!GoogleChatConfig.Advanced.async || source.size() <= 1) {
            return source.stream().map(mapper).toList();
        }
        List<CompletableFuture<R>> futures = source.stream()
                .map(value -> CompletableFuture.supplyAsync(() -> mapper.apply(value), translationExecutor))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private static final Pattern SURROUNDING_SPACE_PATTERN = Pattern.compile("^(\\s*)(.*\\S+)(\\s*)$", Pattern.MULTILINE);
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("(\u00A7[0-9a-fA-Fk-oK-OrR]|§[0-9a-fA-Fk-oK-OrR])");
    private static final Pattern FORMATTING_PLACEHOLDER_PATTERN = Pattern.compile("§FORMATTING_(\\d+)§");
    private static final String FORMATTING_DELIMITER = "\u0001";
    
    private static String protectFormattingCodes(String text) {
        StringBuilder protectedText = new StringBuilder();
        Matcher matcher = FORMATTING_CODE_PATTERN.matcher(text);
        int lastEnd = 0;
        int placeholderIndex = 0;
        List<String> placeholders = new ArrayList<>();
        
        while (matcher.find()) {
            protectedText.append(text, lastEnd, matcher.start());
            String placeholder = "§FORMATTING_" + placeholderIndex++ + "§";
            protectedText.append(placeholder);
            placeholders.add(matcher.group());
            lastEnd = matcher.end();
        }
        protectedText.append(text.substring(lastEnd));
        
        return protectedText.toString() + "\u0000" + String.join(FORMATTING_DELIMITER, placeholders);
    }
    
    private static String restoreFormattingCodes(String text) {
        String[] parts = text.split("\u0000", 2);
        if (parts.length < 2) return text;
        
        String protectedText = parts[0];
        String[] formattingCodes = parts[1].isEmpty() ? new String[0] : parts[1].split(FORMATTING_DELIMITER, -1);
        
        Matcher matcher = FORMATTING_PLACEHOLDER_PATTERN.matcher(protectedText);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        
        while (matcher.find()) {
            result.append(protectedText, lastEnd, matcher.start());
            int codeIndex = Integer.parseInt(matcher.group(1));
            if (codeIndex >= 0 && codeIndex < formattingCodes.length) {
                result.append(formattingCodes[codeIndex]);
            }
            lastEnd = matcher.end();
        }
        result.append(protectedText.substring(lastEnd));
        
        return result.toString();
    }
    
    public static String translateIfNeeded(String source, TranslationDirection direction, boolean respectRegex) {
        if (source == null || source.isBlank()) return source;
        if (direction.shouldSkipOutright()) return source;
        if (respectRegex && direction.regexCanFilterNonBlankText() && direction.failsRegex(source)) return source;
        if (isCircuitOpen()) return source;
        if (TRANSLATE_SERVICE == null) return source;

        ParsedLanguages parsed = parsedLanguages(direction);
        if (!parsed.ready()) return source;
        
        String protectedSource = protectFormattingCodes(source);
        String[] parts = protectedSource.split("\u0000", 2);
        String textToTranslate = parts[0];
        String originalFormatting = parts.length > 1 ? parts[1] : "";
        
        try {
            return computeIfAbsent2(strings.get(direction), stringsPending.get(direction), source, t -> {
                Matcher m = SURROUNDING_SPACE_PATTERN.matcher(textToTranslate);
                if (!m.find()) return source;
                @SuppressWarnings("rawtypes") TranslateService svc = GoogleChat.TRANSLATE_SERVICE;
                if (svc == null) throw new IllegalStateException("Translate service uninitialized");
                try {
                    String translated = m.group(1) + translateWithRetryAndPermit(svc, m.group(2), parsed.sourceLang(), parsed.targetLang()) + m.group(3);

                    if (originalFormatting.isEmpty()) {
                        return translated;
                    }

                    String restored = translated + "\u0000" + originalFormatting;
                    return restoreFormattingCodes(restored);
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            });
        } catch (RuntimeException | Error throwable) {
            if (GoogleChatConfig.Advanced.debugLogs) {
                LOGGER.error("Could not translate text: {0}", throwable, source);
            }
            return source;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    private static <T> T withRequestPermit(ThrowingSupplier<T> supplier) throws Throwable {
        updateRequestLimiter();
        if (!GoogleChatConfig.Advanced.async || maxParallelRequests <= 1) {
            return supplier.get();
        }
        Semaphore limiter = requestLimiter;
        boolean interrupted = false;
        while (true) {
            try {
                limiter.acquire();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        boolean activeSlotAcquired = false;
        while (true) {
            synchronized (breakerLock) {
                int dynamicLimit = GoogleChatConfig.Advanced.adaptiveParallelism
                        ? Math.max(1, Math.min(maxParallelRequests, dynamicParallelLimit))
                        : maxParallelRequests;
                if (activeRequests < dynamicLimit) {
                    activeRequests++;
                    activeSlotAcquired = true;
                    break;
                }
                try {
                    breakerLock.wait(25L);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        }
        try {
            return supplier.get();
        } finally {
            if (activeSlotAcquired) {
                synchronized (breakerLock) {
                    activeRequests = Math.max(0, activeRequests - 1);
                    breakerLock.notifyAll();
                }
            }
            limiter.release();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("rawtypes")
    private static String translateWithRetryAndPermit(TranslateService svc, String text, Language sourceLang, Language targetLang) throws Throwable {
        int retries = Math.max(0, GoogleChatConfig.Advanced.retryAttempts);
        int backoff = Math.max(0, GoogleChatConfig.Advanced.retryBackoffMs);
        Throwable last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            if (attempt > 0 && isCircuitOpen()) {
                break;
            }
            try {
                String translated = withRequestPermit(() -> (String) svc.translate(text, sourceLang, targetLang));
                onRequestSuccess();
                return translated;
            } catch (Throwable throwable) {
                last = throwable;
                onRequestFailure(throwable);
                if (attempt >= retries) break;
                long sleep = (long) backoff * (attempt + 1);
                if (sleep <= 0) continue;
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw throwable;
                }
            }
        }
        throw last == null ? new IllegalStateException("Unknown translation failure") : last;
    }

    private static String translateIfNeededFromBatch(String source, TranslationDirection direction, boolean respectRegex) {
        if (source == null || source.isBlank()) return source;
        Map<String, String> map = stringsBatch.get(direction);
        synchronized (map) {
            if (map.containsKey(source)) return map.get(source);
        }
        String translated = translateIfNeeded(source, direction, respectRegex);
        synchronized (map) {
            map.put(source, translated);
        }
        return translated;
    }

    private static boolean isCircuitOpen() {
        if (!GoogleChatConfig.Advanced.circuitBreaker) return false;
        long now = System.currentTimeMillis();
        long openUntil = circuitOpenUntilMs;
        return openUntil > now;
    }

    private static void onRequestSuccess() {
        synchronized (breakerLock) {
            consecutiveFailures = 0;
            if (!GoogleChatConfig.Advanced.adaptiveParallelism) {
                dynamicParallelLimit = Math.max(1, GoogleChatConfig.Advanced.maxParallelRequests);
                return;
            }
            int configured = Math.max(1, GoogleChatConfig.Advanced.maxParallelRequests);
            if (dynamicParallelLimit < configured) {
                dynamicParallelLimit++;
                breakerLock.notifyAll();
            }
        }
    }

    private static void onRequestFailure(Throwable throwable) {
        synchronized (breakerLock) {
            consecutiveFailures++;
            if (GoogleChatConfig.Advanced.adaptiveParallelism && dynamicParallelLimit > 1) {
                dynamicParallelLimit = Math.max(1, dynamicParallelLimit / 2);
                breakerLock.notifyAll();
            }
            if (GoogleChatConfig.Advanced.circuitBreaker
                    && consecutiveFailures >= Math.max(1, GoogleChatConfig.Advanced.circuitBreakerFailures)) {
                long cooldownMs = Math.max(1, GoogleChatConfig.Advanced.circuitBreakerCooldownSeconds) * 1000L;
                circuitOpenUntilMs = System.currentTimeMillis() + cooldownMs;
                consecutiveFailures = 0;
                breakerLock.notifyAll();
                if (GoogleChatConfig.Advanced.debugLogs) {
                    LOGGER.warn("Circuit breaker opened for {0} ms after failure: {1}", cooldownMs, throwable.toString());
                }
            }
        }
    }

    private static void updateParsedLanguages() {
        ParsedLanguages c2s = ParsedLanguages.empty();
        ParsedLanguages s2c = ParsedLanguages.empty();
        @SuppressWarnings("rawtypes") TranslateService svc = GoogleChat.TRANSLATE_SERVICE;
        if (svc != null) {
            c2s = ParsedLanguages.parse(svc, TranslationDirection.C2S.source(), TranslationDirection.C2S.target());
            s2c = ParsedLanguages.parse(svc, TranslationDirection.S2C.source(), TranslationDirection.S2C.target());
        }
        c2sLanguages = c2s;
        s2cLanguages = s2c;
    }

    private static ParsedLanguages parsedLanguages(TranslationDirection direction) {
        ParsedLanguages cached = direction == TranslationDirection.C2S ? c2sLanguages : s2cLanguages;
        String source = direction.source();
        String target = direction.target();
        if (cached.matches(source, target) && cached.ready()) {
            return cached;
        }
        synchronized (GoogleChat.class) {
            cached = direction == TranslationDirection.C2S ? c2sLanguages : s2cLanguages;
            if (cached.matches(source, target) && cached.ready()) {
                return cached;
            }
            @SuppressWarnings("rawtypes") TranslateService svc = GoogleChat.TRANSLATE_SERVICE;
            if (svc == null) {
                ParsedLanguages unresolved = new ParsedLanguages(source, target, null, null);
                if (direction == TranslationDirection.C2S) c2sLanguages = unresolved;
                else s2cLanguages = unresolved;
                return unresolved;
            }
            ParsedLanguages parsed = ParsedLanguages.parse(svc, source, target);
            if (direction == TranslationDirection.C2S) c2sLanguages = parsed;
            else s2cLanguages = parsed;
            return parsed;
        }
    }

    private static void updateRequestLimiter() {
        int configuredMax = Math.max(1, GoogleChatConfig.Advanced.maxParallelRequests);
        if (configuredMax == maxParallelRequests) return;
        synchronized (GoogleChat.class) {
            if (configuredMax == maxParallelRequests) return;
            maxParallelRequests = configuredMax;
            requestLimiter = new Semaphore(configuredMax);
            synchronized (breakerLock) {
                if (dynamicParallelLimit > configuredMax) dynamicParallelLimit = configuredMax;
                if (dynamicParallelLimit < 1) dynamicParallelLimit = 1;
                breakerLock.notifyAll();
            }
        }
    }

    private static <K, V> V computeIfAbsent2(Map<K, V> map, Map<K, CompletableFuture<V>> pending, K key, Function<K, V> compute) {
        if (!GoogleChatConfig.Advanced.async && !IS_SERVER) return map.computeIfAbsent(key, compute);
        synchronized (map) {
            if (map.containsKey(key)) return map.get(key);
        }

        CompletableFuture<V> ownFuture = new CompletableFuture<>();
        CompletableFuture<V> existingFuture;
        synchronized (pending) {
            existingFuture = pending.get(key);
            if (existingFuture == null) {
                pending.put(key, ownFuture);
            }
        }
        if (existingFuture != null) {
            return existingFuture.join();
        }

        synchronized (map) {
            if (map.containsKey(key)) {
                V value = map.get(key);
                ownFuture.complete(value);
                synchronized (pending) {
                    pending.remove(key, ownFuture);
                }
                return value;
            }
        }

        try {
            V computed = compute.apply(key);
            synchronized (map) {
                if (map.containsKey(key)) {
                    computed = map.get(key);
                } else {
                    map.put(key, computed);
                }
            }
            ownFuture.complete(computed);
            return computed;
        } catch (RuntimeException | Error throwable) {
            ownFuture.completeExceptionally(throwable);
            throw throwable;
        } finally {
            synchronized (pending) {
                pending.remove(key, ownFuture);
            }
        }
    }

    private record ParsedLanguages(String sourceCode, String targetCode, Language sourceLang, Language targetLang) {
        static ParsedLanguages empty() {
            return new ParsedLanguages("", "", null, null);
        }

        @SuppressWarnings("rawtypes")
        static ParsedLanguages parse(TranslateService svc, String sourceCode, String targetCode) {
            return new ParsedLanguages(sourceCode, targetCode, svc.parseLang(sourceCode), svc.parseLang(targetCode));
        }

        boolean matches(String sourceCode, String targetCode) {
            return this.sourceCode.equals(sourceCode) && this.targetCode.equals(targetCode);
        }

        boolean ready() {
            return sourceLang != null && targetLang != null;
        }
    }
}
