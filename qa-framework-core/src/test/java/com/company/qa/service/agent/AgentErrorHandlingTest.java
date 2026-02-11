package com.company.qa.service.agent;

import com.company.qa.model.agent.AgentConfig;
import com.company.qa.model.agent.AgentGoal;
import com.company.qa.model.agent.AgentResult;
import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import com.company.qa.repository.AgentExecutionRepository;
import com.company.qa.service.ai.AITestGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests error handling, retry logic, and graceful degradation.
 *
 * Scenarios:
 * 1. Retry on transient failures
 * 2. Timeout handling
 * 3. Budget exceeded
 * 4. Service unavailable (circuit breaker)
 * 5. Invalid input handling
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentErrorHandlingTest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private AgentExecutionRepository executionRepository;

    @MockBean
    private AITestGenerationService aiTestGenerationService;

    @Test
    @DisplayName("Error Handling: Retry on transient AI service failure")
    void testRetryOnTransientFailure() throws Exception {
        // Simulate AI service failing 2 times, then succeeding
        when(aiTestGenerationService.generateTestFromStory(any()))
                .thenThrow(new RuntimeException("Service temporarily unavailable"))
                .thenThrow(new RuntimeException("Service temporarily unavailable"))
                .thenReturn(createSuccessfulTestResponse());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jiraKey", "RETRY-TEST");

        AgentGoal goal = AgentGoal.builder()
                .goalType("GENERATE_TEST")
                .parameters(parameters)
                .build();

        AgentConfig config = AgentConfig.builder()
                .maxIterations(5)
                .maxAICost(5.0)
                .build();

        // Start agent
        CompletableFuture<AgentResult> future = orchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "test-user"
        );

        // Wait for completion
        AgentResult result = future.get(2, TimeUnit.MINUTES);

        // Verify: Agent should succeed after retries
        assertThat(result.getStatus()).isEqualTo(AgentStatus.SUCCEEDED);

        // Verify AI service was called 3 times (2 failures + 1 success)
        verify(aiTestGenerationService, times(3)).generateTestFromStory(any());
    }

    @Test
    @DisplayName("Error Handling: Timeout when max iterations exceeded")
    void testTimeoutOnMaxIterations() throws Exception {
        // Set very low max iterations
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jiraKey", "TIMEOUT-TEST");

        AgentGoal goal = AgentGoal.builder()
                .goalType("GENERATE_TEST")
                .parameters(parameters)
                .build();

        AgentConfig config = AgentConfig.builder()
                .maxIterations(2) // Very low - will timeout
                .maxAICost(5.0)
                .build();

        // Start agent
        CompletableFuture<AgentResult> future = orchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "test-user"
        );

        // Wait for completion
        AgentResult result = future.get(2, TimeUnit.MINUTES);

        // Verify: Agent should timeout
        assertThat(result.getStatus()).isEqualTo(AgentStatus.TIMEOUT);
        assertThat(result.getIterationsCompleted()).isEqualTo(2);
        assertThat(result.getErrorMessage()).contains("Max iterations");
    }

    @Test
    @DisplayName("Error Handling: Budget exceeded stops execution")
    void testBudgetExceeded() throws Exception {
        // Mock expensive AI call
        var expensiveResponse = createSuccessfulTestResponse();
        expensiveResponse.setTotalCostUsd(java.math.BigDecimal.valueOf(6.0)); // Over budget

        when(aiTestGenerationService.generateTestFromStory(any()))
                .thenReturn(expensiveResponse);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jiraKey", "BUDGET-TEST");

        AgentGoal goal = AgentGoal.builder()
                .goalType("GENERATE_TEST")
                .parameters(parameters)
                .build();

        AgentConfig config = AgentConfig.builder()
                .maxIterations(20)
                .maxAICost(5.0) // Budget limit
                .build();

        // Start agent
        CompletableFuture<AgentResult> future = orchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "test-user"
        );

        // Wait for completion
        AgentResult result = future.get(2, TimeUnit.MINUTES);

        // Verify: Agent should stop due to budget
        assertThat(result.getStatus()).isEqualTo(AgentStatus.BUDGET_EXCEEDED);
        assertThat(result.getTotalAICost()).isGreaterThan(5.0);
        assertThat(result.getErrorMessage()).contains("Budget");
    }

    @Test
    @DisplayName("Error Handling: Invalid input handled gracefully")
    void testInvalidInputHandling() throws Exception {
        // Missing required parameter
        Map<String, Object> parameters = new HashMap<>();
        // No jiraKey provided!

        AgentGoal goal = AgentGoal.builder()
                .goalType("GENERATE_TEST")
                .parameters(parameters)
                .build();

        AgentConfig config = AgentConfig.builder()
                .maxIterations(20)
                .maxAICost(5.0)
                .build();

        // Start agent
        CompletableFuture<AgentResult> future = orchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "test-user"
        );

        // Wait for completion
        AgentResult result = future.get(2, TimeUnit.MINUTES);

        // Verify: Agent should fail gracefully
        assertThat(result.getStatus()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.getErrorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("Error Handling: Agent can be stopped mid-execution")
    void testAgentCanBeStopped() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jiraKey", "STOP-TEST");

        AgentGoal goal = AgentGoal.builder()
                .goalType("GENERATE_TEST")
                .parameters(parameters)
                .build();

        AgentConfig config = AgentConfig.builder()
                .maxIterations(100) // Long-running
                .maxAICost(50.0)
                .build();

        // Start agent
        CompletableFuture<AgentResult> future = orchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "test-user"
        );

        // Get execution ID
        Thread.sleep(2000);
        AgentExecution execution = executionRepository.findRunningAgents().get(0);
        UUID executionId = execution.getId();

        // Stop the agent
        orchestrator.stopAgent(executionId);

        // Wait for completion
        AgentResult result = future.get(1, TimeUnit.MINUTES);

        // Verify: Agent should be stopped
        assertThat(result.getStatus()).isEqualTo(AgentStatus.STOPPED);
    }

    // Helper method
    private com.company.qa.model.dto.response.TestGenerationResponse createSuccessfulTestResponse() {
        return com.company.qa.model.dto.response.TestGenerationResponse.builder()
                .success(true)
                .testId(UUID.randomUUID())
                .testName("GeneratedTest")
                .testCode(Map.of("testClass", "public class GeneratedTest {}"))
                .qualityScore(java.math.BigDecimal.valueOf(75.0))
                .totalCostUsd(java.math.BigDecimal.valueOf(0.25))
                .promptTokens(1000)
                .completionTokens(2000)
                .draftFolderPath("/tmp/draft")
                .build();
    }
}