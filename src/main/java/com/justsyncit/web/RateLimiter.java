package com.justsyncit.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe, memory-bounded rate limiter for preventing brute-force and DoS
 * attacks.
 * Uses a synchronized LRU cache to limit memory usage.
 */
public class RateLimiter {

    private final Map<String, RateLimitRecord> attempts;
    private final int maxAttempts;
    private final long windowMs;

    private static class RateLimitRecord {
        final AtomicInteger count;
        final long windowStart;

        RateLimitRecord(int initialCount, long windowStart) {
            this.count = new AtomicInteger(initialCount);
            this.windowStart = windowStart;
        }
    }

    /**
     * Creates a new RateLimiter.
     *
     * @param maxEntries  Maximum number of IP addresses to track (memory bound)
     * @param maxAttempts Maximum allowed attempts within the window
     * @param windowMs    Time window in milliseconds
     */
    public RateLimiter(int maxEntries, int maxAttempts, long windowMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;

        // LRU Cache implementation
        this.attempts = Collections
                .synchronizedMap(new LinkedHashMap<String, RateLimitRecord>(maxEntries + 1, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, RateLimitRecord> eldest) {
                        return size() > maxEntries;
                    }
                });
    }

    /**
     * Checks if an action is allowed for the given key (e.g., IP address).
     * Increments the counter if allowed.
     *
     * @param key The identifier to rate limit (e.g., IP address)
     * @return true if allowed, false if limit exceeded
     */
    public boolean checkAndIncrement(String key) {
        long now = System.currentTimeMillis();

        // This compute operation is atomic under the map's lock
        // However, since we use Collections.synchronizedMap, the lambda execution is
        // technically inside the synchronized block
        // for the map operation itself, ensuring safety.
        RateLimitRecord record = attempts.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > windowMs) {
                return new RateLimitRecord(1, now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return record.count.get() <= maxAttempts;
    }

    /**
     * Clears all entries.
     */
    public void clear() {
        attempts.clear();
    }
}
