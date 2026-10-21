package android.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> {
    private final LinkedHashMap<K, V> map;

    public LruCache(int maxSize) {
        map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > maxSize; }
        };
    }

    public synchronized V get(K key) { return map.get(key); }

    public synchronized V put(K key, V value) { return map.put(key, value); }

    public synchronized V remove(K key) { return map.remove(key); }

    public synchronized void evictAll() { map.clear(); }
}
