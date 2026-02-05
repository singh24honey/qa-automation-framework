package com.company.qa.service.security;

import com.company.qa.model.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter for AI requests. Prevents abuse and controls costs.
 *
 * Features:
 * - Per-user, per-role rate limiting
 * - Redis-based distributed limiting
 * - In-memory fallback for Redis failures
 * - Token and cost tracking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;

    // In-memory fallback buckets
    private final ConcurrentHashMap<String, RateLimitBucket> inMemoryBuckets = new ConcurrentHashMap<>();

    // Rate limit configurations per role
    private static final ConcurrentHashMap<UserRole, RateLimitConfig> RATE_LIMITS = new ConcurrentHashMap<>();

    static {
        // Configure rate limits per role
        RATE_LIMITS.put(UserRole.DEVELOPER, new RateLimitConfig(50, 100_000, 50.0));
        RATE_LIMITS.put(UserRole.QA_ENGINEER, new RateLimitConfig(200, 500_000, 200.0));
        RATE_LIMITS.put(UserRole.QA_LEAD, new RateLimitConfig(500, 1_000_000, 500.0));
        RATE_LIMITS.put(UserRole.ADMIN, new RateLimitConfig(1000, 2_000_000, 1000.0));
    }

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final String TOKEN_COUNT_PREFIX = "token_count:";
    private static final String COST_PREFIX = "cost:";
    private static final Duration WINDOW_DURATION = Duration.ofHours(1);

    /**
     * Check if user is within rate limits.
     *
     * @param userId User ID
     * @param role User role
     * @return Rate limit result
     */
    public RateLimitResult checkRateLimit(UUID userId, UserRole role) {
        RateLimitConfig config = RATE_LIMITS.getOrDefault(role, RATE_LIMITS.get(UserRole.DEVELOPER));
        String key = RATE_LIMIT_PREFIX + userId.toString();

        try {
            return checkRedisRateLimit(key, config);
        } catch (Exception e) {
            log.warn("Redis unavailable, using in-memory fallback: {}", e.getMessage());
            return checkInMemoryRateLimit(userId.toString(), config);
        }
    }

    /**
     * Record token usage for a user.
     *
     * @param userId User ID
     * @param tokensUsed Number of tokens used
     */
    public void recordTokenUsage(UUID userId, int tokensUsed) {
        String key = TOKEN_COUNT_PREFIX + userId.toString();
        try {
            redisTemplate.opsForValue().increment(key, tokensUsed);
            redisTemplate.expire(key, Duration.ofDays(30));
        } catch (Exception e) {
            log.warn("Failed to record token usage in Redis: {}", e.getMessage());
            // Could add in-memory fallback here if needed
        }
    }

    /**
     * Record cost for a user.
     *
     * @param userId User ID
     * @param cost Cost in dollars
     */
    public void recordCost(UUID userId, double cost) {
        String key = COST_PREFIX + userId.toString();
        try {
            String current = redisTemplate.opsForValue().get(key);
            double newCost = (current != null ? Double.parseDouble(current) : 0.0) + cost;
            redisTemplate.opsForValue().set(key, String.valueOf(newCost), Duration.ofDays(30));
        } catch (Exception e) {
            log.warn("Failed to record cost in Redis: {}", e.getMessage());
        }
    }

    /**
     * Get current token usage for a user.
     *
     * @param userId User ID
     * @return Token count
     */
    public long getTokenUsage(UUID userId) {
        String key = TOKEN_COUNT_PREFIX + userId.toString();
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            log.warn("Failed to get token usage: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Get current cost for a user.
     *
     * @param userId User ID
     * @return Cost in dollars
     */
    public double getCurrentCost(UUID userId) {
        String key = COST_PREFIX + userId.toString();
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Double.parseDouble(value) : 0.0;
        } catch (Exception e) {
            log.warn("Failed to get cost: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Reset rate limit for a user (admin function).
     *
     * @param userId User ID
     */
    public void resetRateLimit(UUID userId) {
        String key = RATE_LIMIT_PREFIX + userId.toString();
        try {
            redisTemplate.delete(key);
            inMemoryBuckets.remove(userId.toString());
            log.info("Rate limit reset for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to reset rate limit: {}", e.getMessage());
        }
    }

    /**
     * Check rate limit using Redis.
     */
    private RateLimitResult checkRedisRateLimit(String key, RateLimitConfig config) {
        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount == null) {
            currentCount = 1L;
        }

        if (currentCount == 1) {
            redisTemplate.expire(key, WINDOW_DURATION);
        }

        boolean allowed = currentCount <= config.requestsPerHour();
        int remaining = Math.max(0, config.requestsPerHour() - currentCount.intValue());

        if (!allowed) {
            log.warn("Rate limit exceeded for key: {} (count: {})", key, currentCount);
        }

        return new RateLimitResult(
                allowed,
                remaining,
                config.requestsPerHour(),
                Instant.now().plus(WINDOW_DURATION)
        );
    }

    /**
     * Check rate limit using in-memory storage (fallback).
     */
    private RateLimitResult checkInMemoryRateLimit(String userId, RateLimitConfig config) {
        RateLimitBucket bucket = inMemoryBuckets.computeIfAbsent(userId, k -> new RateLimitBucket());
        bucket.cleanOldEntries();

        boolean allowed = bucket.getCount() < config.requestsPerHour();
        if (allowed) {
            bucket.increment();
        }

        int remaining = Math.max(0, config.requestsPerHour() - bucket.getCount());

        return new RateLimitResult(
                allowed,
                remaining,
                config.requestsPerHour(),
                Instant.now().plus(WINDOW_DURATION)
        );
    }

    /**
     * Rate limit configuration per role.
     *
     * @param requestsPerHour Maximum requests per hour
     * @param maxTokensPerMonth Maximum tokens per month
     * @param maxCostPerMonth Maximum cost per month (USD)
     */
    public record RateLimitConfig(
            int requestsPerHour,
            long maxTokensPerMonth,
            double maxCostPerMonth
    ) {}

    /**
     * Result of rate limit check.
     *
     * @param allowed Is the request allowed?
     * @param remainingRequests Remaining requests in window
     * @param maxRequests Maximum requests allowed
     * @param resetTime When the rate limit resets
     */
    public record RateLimitResult(
            boolean allowed,
            int remainingRequests,
            int maxRequests,
            Instant resetTime
    ) {}

    /**
     * In-memory rate limit bucket (sliding window).
     */
    private static class RateLimitBucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

        void increment() {
            count.incrementAndGet();
        }

        int getCount() {
            return count.get();
        }

        void cleanOldEntries() {
            long now = System.currentTimeMillis();
            long windowStartTime = windowStart.get();

            if (now - windowStartTime > Duration.ofHours(1).toMillis()) {
                if (windowStart.compareAndSet(windowStartTime, now)) {
                    count.set(0);
                }
            }
        }
    }
}