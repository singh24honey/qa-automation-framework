package com.company.qa.service.security;

import com.company.qa.model.enums.UserRole;
import com.company.qa.service.security.RateLimiterService.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rate Limiter Service Tests")
class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiterService = new RateLimiterService(redisTemplate);
    }

    @Test
    @DisplayName("Should allow request within rate limit")
    void shouldAllowRequestWithinRateLimit() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.increment(anyString())).thenReturn(1L);

        RateLimitResult result = rateLimiterService.checkRateLimit(userId, UserRole.DEVELOPER);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingRequests()).isEqualTo(49); // 50 - 1
        assertThat(result.maxRequests()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should deny request when rate limit exceeded")
    void shouldDenyRequestWhenRateLimitExceeded() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.increment(anyString())).thenReturn(51L); // Exceeds DEVELOPER limit of 50

        RateLimitResult result = rateLimiterService.checkRateLimit(userId, UserRole.DEVELOPER);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remainingRequests()).isZero();
    }

    @Test
    @DisplayName("Should apply different limits for different roles")
    void shouldApplyDifferentLimitsForDifferentRoles() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.increment(anyString())).thenReturn(100L);

        // DEVELOPER (50 limit) - should be denied
        RateLimitResult devResult = rateLimiterService.checkRateLimit(userId, UserRole.DEVELOPER);
        assertThat(devResult.allowed()).isFalse();

        // QA_ENGINEER (200 limit) - should be allowed
        RateLimitResult qaResult = rateLimiterService.checkRateLimit(userId, UserRole.QA_ENGINEER);
        assertThat(qaResult.allowed()).isTrue();

        // QA_LEAD (500 limit) - should be allowed
        RateLimitResult leadResult = rateLimiterService.checkRateLimit(userId, UserRole.QA_LEAD);
        assertThat(leadResult.allowed()).isTrue();
    }

    @Test
    @DisplayName("Should set expiry on first request")
    void shouldSetExpiryOnFirstRequest() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimiterService.checkRateLimit(userId, UserRole.DEVELOPER);

        verify(redisTemplate).expire(anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("Should record token usage")
    void shouldRecordTokenUsage() {
        UUID userId = UUID.randomUUID();
        int tokensUsed = 1000;

        rateLimiterService.recordTokenUsage(userId, tokensUsed);

        verify(valueOperations).increment(contains("token_count:"), eq((long) tokensUsed));
        verify(redisTemplate).expire(anyString(), eq(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("Should record cost")
    void shouldRecordCost() {
        UUID userId = UUID.randomUUID();
        double cost = 5.50;

        when(valueOperations.get(anyString())).thenReturn("10.00");

        rateLimiterService.recordCost(userId, cost);

        verify(valueOperations).set(contains("cost:"), eq("15.5"), eq(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("Should get token usage")
    void shouldGetTokenUsage() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.get(anyString())).thenReturn("5000");

        long usage = rateLimiterService.getTokenUsage(userId);

        assertThat(usage).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Should get current cost")
    void shouldGetCurrentCost() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.get(anyString())).thenReturn("25.75");

        double cost = rateLimiterService.getCurrentCost(userId);

        assertThat(cost).isEqualTo(25.75);
    }

    @Test
    @DisplayName("Should reset rate limit")
    void shouldResetRateLimit() {
        UUID userId = UUID.randomUUID();

        rateLimiterService.resetRateLimit(userId);

        verify(redisTemplate).delete(contains("rate_limit:"));
    }

    @Test
    @DisplayName("Should fallback to in-memory when Redis fails")
    void shouldFallbackToInMemoryWhenRedisFails() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        // First request should succeed using in-memory
        RateLimitResult result1 = rateLimiterService.checkRateLimit(userId, UserRole.DEVELOPER);
        assertThat(result1.allowed()).isTrue();

        // Subsequent requests should also work
        RateLimitResult result2 = rateLimiterService.checkRateLimit(userId, UserRole.DEVELOPER);
        assertThat(result2.allowed()).isTrue();
    }

    @Test
    @DisplayName("Should handle null token usage gracefully")
    void shouldHandleNullTokenUsageGracefully() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.get(anyString())).thenReturn(null);

        long usage = rateLimiterService.getTokenUsage(userId);

        assertThat(usage).isZero();
    }

    @Test
    @DisplayName("Should handle null cost gracefully")
    void shouldHandleNullCostGracefully() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.get(anyString())).thenReturn(null);

        double cost = rateLimiterService.getCurrentCost(userId);

        assertThat(cost).isZero();
    }
}