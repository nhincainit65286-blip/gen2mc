package io.gitlab.jfronny.googlechat.forge189;

import java.util.LinkedHashMap;
import java.util.Map;

final class FixedSizeMap<K, V> extends LinkedHashMap<K, V> {
    private final int max;

    FixedSizeMap(int max) {
        super(Math.max(16, max), 0.75f, true);
        this.max = max;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > max;
    }
}
