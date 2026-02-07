package com.company.qa.model.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentGoalTest {

    @Test
    void testAgentGoalCreation() {
        AgentGoal goal = AgentGoal.builder()
                .goalId(UUID.randomUUID())
                .goalType("GENERATE_TEST")
                .parameters(Map.of(
                        "jiraKey", "PROJ-123",
                        "framework", "PLAYWRIGHT"
                ))
                .successCriteria("Test generated and executed successfully")
                .build();

        assertNotNull(goal.getGoalId());
        assertEquals("GENERATE_TEST", goal.getGoalType());
        assertEquals("PROJ-123", goal.getParameter("jiraKey", String.class));
    }

    @Test
    void testHasRequiredParameters() {
        AgentGoal goal = AgentGoal.builder()
                .parameters(Map.of(
                        "jiraKey", "PROJ-123",
                        "framework", "PLAYWRIGHT"
                ))
                .build();

        assertTrue(goal.hasRequiredParameters("jiraKey", "framework"));
        assertFalse(goal.hasRequiredParameters("jiraKey", "framework", "missing"));
    }
}