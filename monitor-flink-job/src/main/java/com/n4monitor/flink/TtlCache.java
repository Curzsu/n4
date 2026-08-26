package com.n4monitor.flink;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Minimal TTL cache for rarely-changing Redis metadata (rule definitions,
 * point metadata). Entries expire a fixed TTL after they are loaded; an
 * absent or expired entry is reloaded through the supplied loader function.
 * {@code null} results are cached too (negative caching), so probing an
 * unknown alarm code or an unmapped path costs one Redis hit per TTL window
 * instead of one hit per frame.
 * <p>
 * Each Flink subtask owns its own instance. Because the stream is keyed by
 * path, a subtask only ever loads the paths routed to it, so the cache is a
 * sharded copy of the Redis map and stays bounded by (total entries /
 * parallelism). No background eviction: entries are simply replaced on the
 * next load after expiry, and the key space is bounded by the deployment
 * size. Rule caches are tiny (hundreds of entries) and point caches are
 * sharded, so unbounded growth is not a concern at the planned scale.
 * <p>
 * Not thread-safe by requirement but backed by {@link ConcurrentHashMap}
 * anyway: Flink invokes processElement and onTimer from the same mailbox
 * thread, but defensive concurrency costs nothing here.
 *
 * @param <V> cached value type; {@code null} values are allowed and cached
 */
final class TtlCache<V> {

    /** ConcurrentHashMap forbids null values, so nulls are stored wrapped. */
    private record Entry(Object value, long expiresAtNanos) {}

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final long ttlNanos;

    TtlCache(long ttl, TimeUnit unit) {
        this.ttlNanos = unit.toNanos(ttl);
    }

    /**
     * Cached lookup: returns the live entry's value (possibly {@code null}
     * for a cached negative result), or loads, caches and returns the
     * loader's result when the entry is absent or expired.
     */
    V get(String key, Function<String, V> loader) {
        long now = System.nanoTime();
        Entry entry = map.get(key);
        if (entry != null && entry.expiresAtNanos() > now) {
            return cast(entry.value());
        }
        V value = loader.apply(key);
        map.put(key, new Entry(value, now + ttlNanos));
        return value;
    }

    /** Number of cached entries (live and expired), for logging and tests. */
    int size() {
        return map.size();
    }

    @SuppressWarnings("unchecked")
    private V cast(Object value) {
        return (V) value;
    }
}
