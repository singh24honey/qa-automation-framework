package com.company.qa.service.agent;

import com.company.qa.model.agent.AgentConfig;
import com.company.qa.model.agent.AgentGoal;
import com.company.qa.model.agent.AgentResult;
import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    private AgentExecutionService executionService;

    @Mock
    private BaseAgent mockAgent;

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(executionService);
    }

    @Test
    void testRegisterAgent() {
        orchestrator.registerAgent(AgentType.PLAYWRIGHT_TEST_GENERATOR, mockAgent);

        List<AgentType> types = orchestrator.getAvailableAgentTypes();
        assertTrue(types.contains(AgentType.PLAYWRIGHT_TEST_GENERATOR));
    }

    @Test
    void testGetRunningAgents() {
        AgentExecution mockExecution = createMockExecution();
        when(executionService.getRunningExecutions()).thenReturn(List.of(mockExecution));

        List<AgentExecution> running = orchestrator.getRunningAgents();

        assertEquals(1, running.size());
        assertEquals(mockExecution, running.get(0));
    }

    @Test
    void testGetExecutionStatus() {
        UUID executionId = UUID.randomUUID();
        AgentExecution mockExecution = createMockExecution();
        when(executionService.getExecution(executionId)).thenReturn(mockExecution);

        AgentExecution result = orchestrator.getExecutionStatus(executionId);

        assertNotNull(result);
        assertEquals(mockExecution, result);
    }

    private AgentExecution createMockExecution() {
        return AgentExecution.builder()
                .id(UUID.randomUUID())
                .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                .status(AgentStatus.RUNNING)
                .goal(new HashMap<>())
                .currentIteration(0)
                .maxIterations(20)
                .build();
    }
}