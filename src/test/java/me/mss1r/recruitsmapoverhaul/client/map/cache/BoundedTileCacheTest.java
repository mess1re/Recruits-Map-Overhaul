package me.mss1r.recruitsmapoverhaul.client.map.cache;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedTileCacheTest {
    @Test
    void evictsLeastRecentlyUsedEntry() {
        List<String> evicted = new ArrayList<>();
        BoundedTileCache<String, String> cache = new BoundedTileCache<>(3, (key, value) -> evicted.add(key));

        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C");
        assertEquals("A", cache.get("a"));

        cache.put("d", "D");

        assertEquals(3, cache.size());
        assertTrue(cache.snapshot().containsKey("a"));
        assertFalse(cache.snapshot().containsKey("b"));
        assertTrue(cache.snapshot().containsKey("c"));
        assertTrue(cache.snapshot().containsKey("d"));
        assertEquals(List.of("b"), evicted);
    }

    @Test
    void clearWithoutEvictionDoesNotNotifyListener() {
        List<String> evicted = new ArrayList<>();
        BoundedTileCache<String, String> cache = new BoundedTileCache<>(2, (key, value) -> evicted.add(key));

        cache.put("a", "A");
        cache.put("b", "B");
        cache.clearWithoutEviction();

        assertEquals(0, cache.size());
        assertTrue(evicted.isEmpty());
    }

    @Test
    void clearWithEvictionNotifiesListener() {
        List<String> evicted = new ArrayList<>();
        BoundedTileCache<String, String> cache = new BoundedTileCache<>(2, (key, value) -> evicted.add(key));

        cache.put("a", "A");
        cache.put("b", "B");
        cache.clearWithEviction();

        assertEquals(0, cache.size());
        assertEquals(List.of("a", "b"), evicted);
    }
}
