package com.company.qa.service.agent;

import com.company.qa.model.agent.AgentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manages agent context persistence using Redis.
 *
 * Features:
 * - Save/load agent context with automatic serialization
 * - Auto-expiration (TTL) to prevent memory leaks
 * - Fast in-memory access for agent state
 * - Survives application restarts
 *
 * Redis Key Format: "agent:context:{executionId}"
 * Default TTL: 24 hours
 *
 * Used by:
 * - BaseAgent: saveContext() after each action
 * - AgentOrchestrator: loadContext() to resume execution
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "agent:context:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /**
     * Save complete agent context to Redis.
     *
     * Serializes AgentContext to JSON and stores with TTL.
     *
     * @param executionId Agent execution ID
     * @param context Agent context to save
     */
    public void saveContext(UUID executionId, AgentContext context) {
        String key = buildKey(executionId);

        try {
            String contextJson = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(key, contextJson, DEFAULT_TTL);

            log.debug("💾 Saved agent context: {} (iteration: {})",
                    executionId, context.getCurrentIteration());

        } catch (Exception e) {
            log.error("Failed to save agent context: {}", executionId, e);
            throw new RuntimeException("Failed to save agent context", e);
        }
    }

    /**
     * Load agent context from Redis.
     *
     * Deserializes JSON back to AgentContext object.
     *
     * @param executionId Agent execution ID
     * @return Agent context or null if not found/expired
     */
    public AgentContext loadContext(UUID executionId) {
        String key = buildKey(executionId);

        try {
            String contextJson = (String) redisTemplate.opsForValue().get(key);

            if (contextJson == null) {
                log.warn("⚠️  No context found for execution: {}", executionId);
                return null;
            }

            AgentContext context = objectMapper.readValue(contextJson, AgentContext.class);

            log.debug("📂 Loaded agent context: {} (iteration: {})",
                    executionId, context.getCurrentIteration());

            return context;

        } catch (Exception e) {
            log.error("Failed to load agent context: {}", executionId, e);
            return null;
        }
    }

    /**
     * Clear agent context from Redis.
     *
     * Called when agent execution completes or is stopped.
     *
     * @param executionId Agent execution ID
     */
    public void clearContext(UUID executionId) {
        String key = buildKey(executionId);

        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.debug("🗑️  Cleared agent context: {}", executionId);
        } else {
            log.debug("ℹ️  No context to clear: {}", executionId);
        }
    }

    /**
     * Check if context exists in Redis.
     *
     * @param executionId Agent execution ID
     * @return true if context exists and not expired
     */
    public boolean contextExists(UUID executionId) {
        String key = buildKey(executionId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Extend TTL for long-running agents.
     *
     * Useful when agent execution exceeds 24 hours.
     *
     * @param executionId Agent execution ID
     * @param duration New TTL duration
     */
    public void extendTTL(UUID executionId, Duration duration) {
        String key = buildKey(executionId);

        Boolean extended = redisTemplate.expire(key, duration.getSeconds(), TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(extended)) {
            log.debug("⏰ Extended TTL for execution: {} to {} hours",
                    executionId, duration.toHours());
        } else {
            log.warn("Failed to extend TTL - context may not exist: {}", executionId);
        }
    }

    /**
     * Get remaining TTL for context.
     *
     * @param executionId Agent execution ID
     * @return Remaining TTL in seconds, or -1 if no TTL, -2 if key doesn't exist
     */
    public Long getRemainingTTL(UUID executionId) {
        String key = buildKey(executionId);
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * Get all active agent executions (for monitoring).
     *
     * Scans Redis for all agent context keys.
     *
     * @return List of execution IDs with active contexts
     */
    public List<UUID> getActiveExecutions() {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");

            if (keys == null || keys.isEmpty()) {
                return List.of();
            }

            return keys.stream()
                    .map(key -> key.replace(KEY_PREFIX, ""))
                    .map(UUID::fromString)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to get active executions", e);
            return List.of();
        }
    }

    /**
     * Build Redis key for execution context.
     */
    private String buildKey(UUID executionId) {
        return KEY_PREFIX + executionId.toString();
    }
}