package com.company.qa.model.agent;

import com.company.qa.model.enums.AgentActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentContextTest {

    private AgentContext context;

    @BeforeEach
    void setUp() {
        context = AgentContext.builder()
                .maxIterations(10)
                .startedAt(Instant.now())
                .build();
    }

    @Test
    void testIncrementIteration() {
        assertEquals(0, context.getCurrentIteration());
        context.incrementIteration();
        assertEquals(1, context.getCurrentIteration());
    }

    @Test
    void testMaxIterationsReached() {
        assertFalse(context.isMaxIterationsReached());

        for (int i = 0; i < 10; i++) {
            context.incrementIteration();
        }

        assertTrue(context.isMaxIterationsReached());
    }

    @Test
    void testAddToHistory() {
        AgentHistoryEntry entry = AgentHistoryEntry.builder()
                .iteration(1)
                .actionType(AgentActionType.FETCH_JIRA_STORY)
                .success(true)
                .timestamp(Instant.now())
                .build();

        context.addToHistory(entry);

        assertEquals(1, context.getActionHistory().size());
        assertEquals(AgentActionType.FETCH_JIRA_STORY, context.getActionHistory().get(0).getActionType());
    }

    @Test
    void testWorkProducts() {
        context.putWorkProduct("testCode", "public class Test {}");

        String testCode = context.getWorkProduct("testCode", String.class);
        assertEquals("public class Test {}", testCode);
    }

    @Test
    void testAddAICost() {
        context.addAICost(0.05);
        context.addAICost(0.03);

        assertEquals(0.08, context.getTotalAICost(), 0.001);
    }
}