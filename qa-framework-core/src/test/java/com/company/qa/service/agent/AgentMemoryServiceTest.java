package com.company.qa.service.agent;

import com.company.qa.model.agent.AgentContext;
import com.company.qa.model.agent.AgentGoal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentMemoryServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private AgentMemoryService memoryService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // For Java 8 time types
        memoryService = new AgentMemoryService(redisTemplate, objectMapper);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void testSaveContext() throws Exception {
        UUID executionId = UUID.randomUUID();
        AgentContext context = createTestContext();

        memoryService.saveContext(executionId, context);

        String json = objectMapper.writeValueAsString(context);
        System.out.println("JSON being saved: " + json);

        verify(valueOps).set(
                eq("agent:context:" + executionId),
                anyString(),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    void testLoadContext() throws Exception {
        UUID executionId = UUID.randomUUID();
        AgentContext context = createTestContext();
        String contextJson = objectMapper.writeValueAsString(context);

        when(valueOps.get("agent:context:" + executionId)).thenReturn(contextJson);

        AgentContext loaded = memoryService.loadContext(executionId);

        assertNotNull(loaded);
        assertEquals(context.getCurrentIteration(), loaded.getCurrentIteration());
        assertEquals(context.getMaxIterations(), loaded.getMaxIterations());
    }

    @Test
    void testLoadContextNotFound() {
        UUID executionId = UUID.randomUUID();
        when(valueOps.get(anyString())).thenReturn(null);

        AgentContext loaded = memoryService.loadContext(executionId);

        assertNull(loaded);
    }

    @Test
    void testClearContext() {
        UUID executionId = UUID.randomUUID();
        when(redisTemplate.delete(anyString())).thenReturn(true);

        memoryService.clearContext(executionId);

        verify(redisTemplate).delete("agent:context:" + executionId);
    }

    @Test
    void testContextExists() {
        UUID executionId = UUID.randomUUID();
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        boolean exists = memoryService.contextExists(executionId);

        assertTrue(exists);
    }

    @Test
    void testContextNotExists() {
        UUID executionId = UUID.randomUUID();
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        boolean exists = memoryService.contextExists(executionId);

        assertFalse(exists);
    }

    private AgentContext createTestContext() {
        return AgentContext.builder()
                .goal(AgentGoal.builder()
                        .goalType("TEST_GOAL")
                        .parameters(new HashMap<>())
                        .build())
                .currentIteration(5)
                .maxIterations(20)
                .actionHistory(new ArrayList<>())
                .workProducts(new HashMap<>())
                .state(new HashMap<>())
                .totalAICost(0.0)
                .startedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .build();
    }
}