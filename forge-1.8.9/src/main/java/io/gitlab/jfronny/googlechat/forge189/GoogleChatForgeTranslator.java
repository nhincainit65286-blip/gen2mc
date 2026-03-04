package io.gitlab.jfronny.googlechat.forge189;

import net.minecraftforge.fml.common.FMLLog;

import java.util.Map;
import java.util.concurrent.*;

final class GoogleChatForgeTranslator {
    private static final Object LOCK = new Object();
    private static Map<String, String> cache = new FixedSizeMap<String, String>(GoogleChatForgeConfig.cacheSize);
    private static final Map<String, CompletableFuture<String>> pending = new java.util.HashMap<String, CompletableFuture<String>>();

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static Semaphore limiter = new Semaphore(Math.max(1, GoogleChatForgeConfig.maxParallelRequests));

    private GoogleChatForgeTranslator() {
    }

    static void reconfigure() {
        synchronized (LOCK) {
            cache = new FixedSizeMap<String, String>(GoogleChatForgeConfig.cacheSize);
            pending.clear();
            limiter = new Semaphore(Math.max(1, GoogleChatForgeConfig.maxParallelRequests));
        }
    }

    static String translateIncoming(String text) {
        return translate(text, GoogleChatForgeConfig.incomingSourceLanguage, GoogleChatForgeConfig.incomingTargetLanguage);
    }

    static String translateOutgoing(String text) {
        return translate(text, GoogleChatForgeConfig.outgoingSourceLanguage, GoogleChatForgeConfig.outgoingTargetLanguage);
    }

    private static String translate(String text, String source, String target) {
        if (!GoogleChatForgeConfig.enabled) return text;
        if (text == null || text.trim().isEmpty()) return text;
        if (target == null || target.trim().isEmpty() || "auto".equalsIgnoreCase(target)) return text;
        if (source != null && source.equalsIgnoreCase(target)) return text;

        final String key = source + "\n" + target + "\n" + text;
        final CompletableFuture<String> future;
        final boolean isOwner;
        synchronized (LOCK) {
            String cached = cache.get(key);
            if (cached != null) return cached;
            CompletableFuture<String> inFlight = pending.get(key);
            if (inFlight != null) {
                future = inFlight;
                isOwner = false;
            } else {
                CompletableFuture<String> ownFuture = new CompletableFuture<String>();
                pending.put(key, ownFuture);
                future = ownFuture;
                isOwner = true;
            }
        }

        if (isOwner) {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        String result = text;
                        boolean acquired = false;
                        try {
                            limiter.acquire();
                            acquired = true;
                            result = TranslationService.translate(text, source, target, GoogleChatForgeConfig.requestTimeoutMs);
                        } catch (Throwable t) {
                            if (GoogleChatForgeConfig.debugLogs) {
                                FMLLog.warning("[GoogleChatForge] Translation failed: %s", t.toString());
                            }
                        } finally {
                            if (acquired) limiter.release();
                        }

                        synchronized (LOCK) {
                            cache.put(key, result);
                            pending.remove(key, future);
                        }
                        future.complete(result);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                if (GoogleChatForgeConfig.debugLogs) {
                    FMLLog.warning("[GoogleChatForge] Executor rejected task: %s", rejected.toString());
                }
                synchronized (LOCK) {
                    pending.remove(key, future);
                }
                future.complete(text);
            }
        }

        return await(future, text);
    }

    private static String await(CompletableFuture<String> future, String fallback) {
        try {
            return future.get();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
