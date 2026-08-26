package com.hireflow.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain-Java unit tests for the token-bucket algorithm itself - no Spring context, no servlet
 * classes, deterministic via an injectable fake nanosecond clock (no real sleeping). See
 * {@code RateLimitingFilterTest} for the servlet-filter-level behavior built on top of this.
 */
class TokenBucketRateLimiterTest {

    @Test
    void allowsUpToCapacity_thenDeniesTheNext() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume("1.2.3.4")).isFalse();
    }

    @Test
    void differentKeys_areTrackedIndependently() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume("keyA")).isTrue();
        assertThat(limiter.tryConsume("keyA")).isFalse();
        // keyB has never been seen before, so it gets its own fresh, full bucket.
        assertThat(limiter.tryConsume("keyB")).isTrue();
    }

    @Test
    void refillsGraduallyOverTime_cappedAtCapacity() {
        AtomicLong fakeNanos = new AtomicLong(0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, Duration.ofSeconds(60), fakeNanos::get);

        assertThat(limiter.tryConsume("k")).isTrue();
        assertThat(limiter.tryConsume("k")).isTrue();
        assertThat(limiter.tryConsume("k")).isFalse(); // bucket empty

        // Half the refill period elapses for a capacity-2 bucket -> exactly 1 token back.
        fakeNanos.addAndGet(Duration.ofSeconds(30).toNanos());
        assertThat(limiter.tryConsume("k")).isTrue();
        assertThat(limiter.tryConsume("k")).isFalse();

        // Far more than a full refill period elapses -> back to full capacity, not unbounded.
        fakeNanos.addAndGet(Duration.ofSeconds(600).toNanos());
        assertThat(limiter.tryConsume("k")).isTrue();
        assertThat(limiter.tryConsume("k")).isTrue();
        assertThat(limiter.tryConsume("k")).isFalse();
    }

    @Test
    void nonPositiveCapacity_isRejected() {
        assertThatThrownBy(() -> new TokenBucketRateLimiter(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
