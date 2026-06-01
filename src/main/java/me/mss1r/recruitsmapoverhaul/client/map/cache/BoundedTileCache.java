package me.mss1r.recruitsmapoverhaul.client.map.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BoundedTileCache<K, V> {
    private final LinkedHashMap<K, V> entries;
    private final int maxEntries;
    private final EvictionListener<K, V> evictionListener;

    BoundedTileCache(int maxEntries, EvictionListener<K, V> evictionListener) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        this.evictionListener = evictionListener;
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    V get(K key) {
        return entries.get(key);
    }

    void put(K key, V value) {
        V oldValue = entries.put(key, value);
        if (oldValue != null && oldValue != value) {
            evict(key, oldValue);
        }
        trimToBudget();
    }

    V removeWithoutEviction(K key) {
        return entries.remove(key);
    }

    int size() {
        return entries.size();
    }

    List<V> valuesSnapshot() {
        return new ArrayList<>(entries.values());
    }

    Map<K, V> snapshot() {
        return Map.copyOf(entries);
    }

    void clearWithoutEviction() {
        entries.clear();
    }

    void clearWithEviction() {
        for (Map.Entry<K, V> entry : new ArrayList<>(entries.entrySet())) {
            evict(entry.getKey(), entry.getValue());
        }
        entries.clear();
    }

    private void trimToBudget() {
        while (entries.size() > maxEntries) {
            Map.Entry<K, V> eldest = entries.entrySet().iterator().next();
            K key = eldest.getKey();
            V value = eldest.getValue();
            entries.remove(key);
            evict(key, value);
        }
    }

    private void evict(K key, V value) {
        if (evictionListener != null && value != null) {
            evictionListener.onEvict(key, value);
        }
    }

    interface EvictionListener<K, V> {
        void onEvict(K key, V value);
    }
}
