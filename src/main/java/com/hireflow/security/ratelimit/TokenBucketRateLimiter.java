package com.hireflow.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A small, in-memory, per-key token-bucket rate limiter - plain Java, no Spring/servlet imports,
 * in the same spirit as {@code TransitionValidator}: framework-free and trivially unit-testable
 * in isolation (see {@code TokenBucketRateLimiterTest}).
 *
 * <p><b>Why hand-rolled instead of a library (e.g. Bucket4j)</b>: this project's rate-limiting
 * need is narrow (two auth endpoints, single-instance deployment, no need for the distributed/
 * Redis-backed bucket support a library like Bucket4j is mainly valuable for) and a ~50-line
 * token bucket is easy to read, easy to unit test with a fake clock, and doesn't add a new
 * runtime dependency for something this small. See the README's "Rate limiting" section for the
 * full trade-off discussion, including the documented limitation that this state is in-memory
 * and therefore per-instance only - it does not share across replicas.
 *
 * <p>Each key (e.g. a client IP) gets its own bucket, created lazily on first use, holding up to
 * {@code capacity} tokens and refilling at a steady rate of {@code capacity} tokens per
 * {@code refillPeriod}. A request is allowed by consuming one token; if none are available, it
 * is denied. Buckets are never evicted (acceptable at this project's scale - see README), but
 * each one is a handful of longs, not a meaningful memory concern.
 */
public class TokenBucketRateLimiter {

    private final long capacity;
    private final long refillPeriodNanos;
    private final Supplier<Long> nanoClock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(long capacity, Duration refillPeriod) {
        this(capacity, refillPeriod, defaultNanoClock());
    }

    /** Test-only constructor: an injectable nanosecond clock so refill behavior is deterministic without real sleeping. */
    TokenBucketRateLimiter(long capacity, Duration refillPeriod, Supplier<Long> nanoClock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.refillPeriodNanos = refillPeriod.toNanos();
        this.nanoClock = nanoClock;
    }

    /** Attempts to consume one token for {@code key}. Returns true if allowed, false if the bucket is empty. */
    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, nanoClock.get()));
        return bucket.tryConsume();
    }

    private static Supplier<Long> defaultNanoClock() {
        Clock clock = Clock.systemUTC();
        return () -> clock.instant().toEpochMilli() * 1_000_000L;
    }

    /**
     * One bucket per key. Refill is computed lazily on each {@code tryConsume} call based on
     * elapsed time (no background thread/scheduler needed): tokens accrue continuously at
     * {@code capacity / refillPeriod}, capped at {@code capacity}.
     */
    private final class Bucket {
        private final AtomicLong availableTokens;
        private final AtomicLong lastRefillNanos;

        Bucket(long capacity, long nowNanos) {
            this.availableTokens = new AtomicLong(capacity);
            this.lastRefillNanos = new AtomicLong(nowNanos);
        }

        synchronized boolean tryConsume() {
            refill();
            if (availableTokens.get() > 0) {
                availableTokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = nanoClock.get();
            long elapsed = now - lastRefillNanos.get();
            if (elapsed <= 0) {
                return;
            }
            long tokensToAdd = (elapsed * capacity) / refillPeriodNanos;
            if (tokensToAdd > 0) {
                long updated = Math.min(capacity, availableTokens.get() + tokensToAdd);
                availableTokens.set(updated);
                lastRefillNanos.set(now);
            }
        }
    }
}
