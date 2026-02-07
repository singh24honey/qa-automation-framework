package com.company.qa.model.agent;

import com.company.qa.model.enums.AgentActionType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {

    @Test
    void testDefaultConfig() {
        AgentConfig config = AgentConfig.builder().build();

        assertEquals(20, config.getMaxIterations());
        assertEquals(5.0, config.getMaxAICost());
        assertTrue(config.getActionsRequiringApproval().contains(AgentActionType.COMMIT_CHANGES));
    }

    @Test
    void testRequiresApproval() {
        AgentConfig config = AgentConfig.builder()
                .actionsRequiringApproval(Set.of(AgentActionType.COMMIT_CHANGES))
                .actionsNeverRequiringApproval(Set.of(AgentActionType.FETCH_JIRA_STORY))
                .build();

        assertTrue(config.requiresApproval(AgentActionType.COMMIT_CHANGES));
        assertFalse(config.requiresApproval(AgentActionType.FETCH_JIRA_STORY));
    }

    @Test
    void testNeverOverridesAlways() {
        AgentConfig config = AgentConfig.builder()
                .actionsRequiringApproval(Set.of(AgentActionType.COMMIT_CHANGES))
                .actionsNeverRequiringApproval(Set.of(AgentActionType.COMMIT_CHANGES)) // Conflict
                .build();

        // "Never" takes precedence over "Always"
        assertFalse(config.requiresApproval(AgentActionType.COMMIT_CHANGES));
    }
}