package com.company.qa.controller;

import com.company.qa.model.agent.*;
import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.agent.entity.AgentActionHistory;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import com.company.qa.service.agent.AgentExecutionService;
import com.company.qa.service.agent.AgentOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.company.qa.model.dto.ApprovalDecisionDTO;
import com.company.qa.model.dto.ApprovalRequestDTO;
import com.company.qa.model.enums.ApprovalStatus;
import com.company.qa.service.approval.ApprovalRequestService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller test for AgentController.
 *
 * Designed for debugging agent flows without triggering from the frontend.
 * Each @Nested class maps to one agent type or one endpoint group.
 *
 * To debug a specific agent run manually:
 *   1. Run the test for that agent (e.g. shouldStartPlaywrightTestGeneratorAgent)
 *   2. Capture the executionId from the response
 *   3. Use that ID in shouldGetExecutionStatus or shouldGetActionHistory tests
 *
 * Security note: @WebMvcTest slices out the full security filter chain.
 * X-API-Key and X-User-ID headers are included for realism but are not validated.
 */
@WebMvcTest(AgentController.class)
@DisplayName("AgentController — Debug Test Suite")
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentOrchestrator orchestrator;

    @MockBean
    private AgentExecutionService executionService;

    @MockBean
    private ApprovalRequestService approvalRequestService;

    // ─── Shared test data ───────────────────────────────────────────────────────

    private static final UUID EXECUTION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String API_KEY = "test-api-key-debug";
    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String USER_NAME = "debug-user";

    private AgentExecution mockExecution;

    @BeforeEach
    void setUp() {
        mockExecution = AgentExecution.builder()
                .id(EXECUTION_ID)
                .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                .status(AgentStatus.RUNNING)
                .goal(Map.of(
                        "goalType", "GENERATE_TEST",
                        "parameters", Map.of("jiraKey", "SCRUM-15", "framework", "PLAYWRIGHT")
                ))
                .currentIteration(0)
                .maxIterations(20)
                .startedAt(Instant.now())
                .triggeredByName(USER_NAME)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. PLAYWRIGHT TEST GENERATOR
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PlaywrightTestGeneratorAgent")
    class PlaywrightTestGeneratorAgentTests {

        @Test
        @DisplayName("START → should trigger PLAYWRIGHT_TEST_GENERATOR for SCRUM-15")
        void shouldStartPlaywrightTestGeneratorAgent() throws Exception {
            // Arrange
            when(orchestrator.createAndStartAgent(
                    eq(AgentType.PLAYWRIGHT_TEST_GENERATOR),
                    any(AgentGoal.class),
                    any(AgentConfig.class),
                    any(),
                    anyString()
            )).thenReturn(mockExecution);

            StartAgentRequest request = StartAgentRequest.builder()
                    .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                    .goalType("GENERATE_TEST")
                    .parameters(Map.of(
                            "jiraKey", "SCRUM-15",   // ← change this to test different stories
                            "framework", "PLAYWRIGHT"
                    ))
                    .maxIterations(20)
                    .maxAICost(5.0)
                    .build();

            // Act & Assert
            MvcResult result = mockMvc.perform(post("/api/v1/agents/start")
                            .header("X-API-Key", API_KEY)
                            .header("X-User-ID", USER_ID)
                            .header("X-User-Name", USER_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())  // prints full request/response to console for debugging
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").value(EXECUTION_ID.toString()))
                    .andExpect(jsonPath("$.agentType").value("PLAYWRIGHT_TEST_GENERATOR"))
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andReturn();

            // Log the executionId so you can paste it into the status/action tests below
            String responseBody = result.getResponse().getContentAsString();
            System.out.println("\n=== EXECUTION STARTED ===");
            System.out.println("Response: " + responseBody);
            System.out.println("Use executionId for status/action checks: " + EXECUTION_ID);
            System.out.println("=========================\n");

            // Verify orchestrator was called with correct agent type
            verify(orchestrator).createAndStartAgent(
                    eq(AgentType.PLAYWRIGHT_TEST_GENERATOR),
                    argThat(goal -> "GENERATE_TEST".equals(goal.getGoalType())
                            && "SCRUM-15".equals(goal.getParameters().get("jiraKey"))),
                    any(AgentConfig.class),
                    any(),
                    anyString()
            );
        }

        @Test
        @DisplayName("START → should accept custom maxIterations and maxAICost")
        void shouldRespectCustomConfig() throws Exception {
            when(orchestrator.createAndStartAgent(any(), any(), any(), any(), any()))
                    .thenReturn(mockExecution);

            StartAgentRequest request = StartAgentRequest.builder()
                    .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                    .goalType("GENERATE_TEST")
                    .parameters(Map.of("jiraKey", "SCRUM-20", "framework", "PLAYWRIGHT"))
                    .maxIterations(30)     // extended for complex stories
                    .maxAICost(10.0)       // higher budget
                    .approvalTimeoutSeconds(7200)  // 2h approval window
                    .build();

            mockMvc.perform(post("/api/v1/agents/start")
                            .header("X-API-Key", API_KEY)
                            .header("X-User-ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isAccepted());

            verify(orchestrator).createAndStartAgent(
                    eq(AgentType.PLAYWRIGHT_TEST_GENERATOR),
                    any(AgentGoal.class),
                    argThat(config -> config.getMaxIterations() == 30
                            && config.getMaxAICost() == 10.0
                            && config.getApprovalTimeoutSeconds() == 7200),
                    any(),
                    anyString()
            );
        }

        @Test
        @DisplayName("START → should reject request missing agentType")
        void shouldRejectMissingAgentType() throws Exception {
            StartAgentRequest request = StartAgentRequest.builder()
                    // agentType deliberately omitted
                    .goalType("GENERATE_TEST")
                    .parameters(Map.of("jiraKey", "SCRUM-15"))
                    .build();

            mockMvc.perform(post("/api/v1/agents/start")
                            .header("X-API-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("START → should reject request missing goalType")
        void shouldRejectMissingGoalType() throws Exception {
            StartAgentRequest request = StartAgentRequest.builder()
                    .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                    // goalType deliberately omitted
                    .parameters(Map.of("jiraKey", "SCRUM-15"))
                    .build();

            mockMvc.perform(post("/api/v1/agents/start")
                            .header("X-API-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. SELF HEALING AGENT
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SelfHealingAgent")
    class SelfHealingAgentTests {

        @Test
        @DisplayName("START → should trigger SELF_HEALING_TEST_FIXER with testId + error")
        void shouldStartSelfHealingAgent() throws Exception {
            AgentExecution healExecution = AgentExecution.builder()
                    .id(EXECUTION_ID)
                    .agentType(AgentType.SELF_HEALING_TEST_FIXER)
                    .status(AgentStatus.RUNNING)
                    .goal(Map.of("goalType", "FIX_BROKEN_TEST"))
                    .currentIteration(0)
                    .maxIterations(25)
                    .startedAt(Instant.now())
                    .triggeredByName(USER_NAME)
                    .build();

            when(orchestrator.createAndStartAgent(
                    eq(AgentType.SELF_HEALING_TEST_FIXER),
                    any(), any(), any(), any()
            )).thenReturn(healExecution);

            StartAgentRequest request = StartAgentRequest.builder()
                    .agentType(AgentType.SELF_HEALING_TEST_FIXER)
                    .goalType("FIX_BROKEN_TEST")
                    .parameters(Map.of(
                            // Paste the real testId from your tests table here:
                            "testId", "replace-with-real-test-uuid",
                            // Paste the exact Playwright error from logs here:
                            "errorMessage", "Step execution failed: Timeout 30000ms exceeded.\n" +
                                    "  ...waiting for locator('.inventory_item:nth-child(1).btn_inventory')"
                    ))
                    .maxIterations(25)
                    .maxAICost(3.0)
                    .build();

            mockMvc.perform(post("/api/v1/agents/start")
                            .header("X-API-Key", API_KEY)
                            .header("X-User-ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.agentType").value("SELF_HEALING_TEST_FIXER"))
                    .andExpect(jsonPath("$.status").value("RUNNING"));

            verify(orchestrator).createAndStartAgent(
                    eq(AgentType.SELF_HEALING_TEST_FIXER),
                    argThat(goal -> "FIX_BROKEN_TEST".equals(goal.getGoalType())
                            && goal.getParameters().containsKey("testId")
                            && goal.getParameters().containsKey("errorMessage")),
                    argThat(config -> config.getMaxIterations() == 25),
                    any(),
                    anyString()
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. FLAKY TEST AGENT
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FlakyTestAgent")
    class FlakyTestAgentTests {

        @Test
        @DisplayName("START → should trigger FLAKY_TEST_FIXER with testId")
        void shouldStartFlakyTestAgent() throws Exception {
            AgentExecution flakyExecution = AgentExecution.builder()
                    .id(EXECUTION_ID)
                    .agentType(AgentType.FLAKY_TEST_FIXER)
                    .status(AgentStatus.RUNNING)
                    .goal(Map.of("goalType", "FIX_FLAKY_TEST"))
                    .currentIteration(0)
                    .maxIterations(20)
                    .startedAt(Instant.now())
                    .build();

            when(orchestrator.createAndStartAgent(
                    eq(AgentType.FLAKY_TEST_FIXER),
                    any(), any(), any(), any()
            )).thenReturn(flakyExecution);

            StartAgentRequest request = StartAgentRequest.builder()
                    .agentType(AgentType.FLAKY_TEST_FIXER)
                    .goalType("FIX_FLAKY_TEST")
                    .parameters(Map.of(
                            "testId", "replace-with-real-test-uuid"
                    ))
                    .maxIterations(20)
                    .maxAICost(3.0)
                    .build();

            mockMvc.perform(post("/api/v1/agents/start")
                            .header("X-API-Key", API_KEY)
                            .header("X-User-ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.agentType").value("FLAKY_TEST_FIXER"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. EXECUTION STATUS & POLLING
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Execution Status & Polling")
    class ExecutionStatusTests {

        @Test
        @DisplayName("GET /{executionId} → should return RUNNING status")
        void shouldGetRunningStatus() throws Exception {
            when(executionService.getExecution(EXECUTION_ID)).thenReturn(mockExecution);

            mockMvc.perform(get("/api/v1/agents/{id}", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").value(EXECUTION_ID.toString()))
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.currentIteration").value(0))
                    .andExpect(jsonPath("$.maxIterations").value(20));
        }

        @Test
        @DisplayName("GET /{executionId} → should return SUCCEEDED with result")
        void shouldGetSucceededStatus() throws Exception {
            AgentExecution succeeded = AgentExecution.builder()
                    .id(EXECUTION_ID)
                    .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                    .status(AgentStatus.SUCCEEDED)
                    .goal(Map.of("goalType", "GENERATE_TEST"))
                    .currentIteration(7)
                    .maxIterations(20)
                    .startedAt(Instant.now().minusSeconds(120))
                    .completedAt(Instant.now())
                    .triggeredByName(USER_NAME)
                    .build();

            when(executionService.getExecution(EXECUTION_ID)).thenReturn(succeeded);

            mockMvc.perform(get("/api/v1/agents/{id}", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.currentIteration").value(7))
                    .andExpect(jsonPath("$.completedAt").exists());
        }

        @Test
        @DisplayName("GET /{executionId} → should return WAITING_FOR_APPROVAL")
        void shouldGetWaitingForApprovalStatus() throws Exception {
            AgentExecution waiting = AgentExecution.builder()
                    .id(EXECUTION_ID)
                    .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                    .status(AgentStatus.WAITING_FOR_APPROVAL)
                    .goal(Map.of("goalType", "GENERATE_TEST"))
                    .currentIteration(3)
                    .maxIterations(20)
                    .startedAt(Instant.now().minusSeconds(60))
                    .triggeredByName(USER_NAME)
                    .build();

            when(executionService.getExecution(EXECUTION_ID)).thenReturn(waiting);

            mockMvc.perform(get("/api/v1/agents/{id}", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WAITING_FOR_APPROVAL"))
                    .andExpect(jsonPath("$.currentIteration").value(3));
        }

        @Test
        @DisplayName("GET /{executionId} → should return FAILED with error message")
        void shouldGetFailedStatus() throws Exception {
            AgentExecution failed = AgentExecution.builder()
                    .id(EXECUTION_ID)
                    .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                    .status(AgentStatus.FAILED)
                    .goal(Map.of("goalType", "GENERATE_TEST"))
                    .currentIteration(5)
                    .maxIterations(20)
                    .startedAt(Instant.now().minusSeconds(90))
                    .completedAt(Instant.now())
                    .triggeredByName(USER_NAME)
                    .errorMessage("JIRA story SCRUM-15 not found — check jiraKey parameter")
                    .build();

            when(executionService.getExecution(EXECUTION_ID)).thenReturn(failed);

            mockMvc.perform(get("/api/v1/agents/{id}", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.errorMessage").value(
                            "JIRA story SCRUM-15 not found — check jiraKey parameter"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. ACTION HISTORY — step-by-step trace
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Action History")
    class ActionHistoryTests {

        @Test
        @DisplayName("GET /{executionId}/actions → should return ordered action steps")
        void shouldGetActionHistory() throws Exception {
            List<AgentActionHistory> actions = List.of(
                    buildAction(1, "FETCH_JIRA_STORY", true, null),
                    buildAction(2, "GENERATE_TEST_CODE", true, null),
                    buildAction(3, "WRITE_FILE", true, null),
                    buildAction(4, "REQUEST_APPROVAL", true, null),
                    buildAction(5, "CREATE_BRANCH", false,
                            "Git auth failed: check GIT_TOKEN env var")
            );

            when(executionService.getActions(EXECUTION_ID)).thenReturn(actions);

            MvcResult result = mockMvc.perform(get("/api/v1/agents/{id}/actions", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(5))
                    .andExpect(jsonPath("$[0].iteration").value(1))
                    .andExpect(jsonPath("$[0].actionType").value("FETCH_JIRA_STORY"))
                    .andExpect(jsonPath("$[0].success").value(true))
                    .andExpect(jsonPath("$[4].actionType").value("CREATE_BRANCH"))
                    .andExpect(jsonPath("$[4].success").value(false))
                    .andExpect(jsonPath("$[4].errorMessage").value(
                            "Git auth failed: check GIT_TOKEN env var"))
                    .andReturn();

            // Print the full action trace to console for debugging
            System.out.println("\n=== ACTION HISTORY TRACE ===");
            System.out.println(result.getResponse().getContentAsString());
            System.out.println("============================\n");
        }

        @Test
        @DisplayName("GET /{executionId}/actions → should return empty list for new execution")
        void shouldGetEmptyActionsForNewExecution() throws Exception {
            when(executionService.getActions(EXECUTION_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/agents/{id}/actions", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 6. STOP AGENT
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stop Agent")
    class StopAgentTests {

        @Test
        @DisplayName("POST /{executionId}/stop → should stop running agent")
        void shouldStopRunningAgent() throws Exception {
            when(orchestrator.stopAgent(EXECUTION_ID)).thenReturn(true);

            mockMvc.perform(post("/api/v1/agents/{id}/stop", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk());

            verify(orchestrator).stopAgent(EXECUTION_ID);
        }

        @Test
        @DisplayName("POST /{executionId}/stop → should return 404 if agent not running")
        void shouldReturn404IfAgentNotRunning() throws Exception {
            when(orchestrator.stopAgent(EXECUTION_ID)).thenReturn(false);

            mockMvc.perform(post("/api/v1/agents/{id}/stop", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 7. RUNNING AGENTS & TYPES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Running Agents & Types")
    class RunningAgentsTests {

        @Test
        @DisplayName("GET /running → should return list of active agents")
        void shouldGetRunningAgents() throws Exception {
            AgentExecution second = AgentExecution.builder()
                    .id(UUID.randomUUID())
                    .agentType(AgentType.SELF_HEALING_TEST_FIXER)
                    .status(AgentStatus.RUNNING)
                    .goal(Map.of("goalType", "FIX_BROKEN_TEST"))
                    .currentIteration(2)
                    .maxIterations(25)
                    .startedAt(Instant.now().minusSeconds(30))
                    .triggeredByName("system-auto-heal")
                    .build();

            when(orchestrator.getRunningAgents()).thenReturn(List.of(mockExecution, second));

            mockMvc.perform(get("/api/v1/agents/running")
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].agentType").value("PLAYWRIGHT_TEST_GENERATOR"))
                    .andExpect(jsonPath("$[1].agentType").value("SELF_HEALING_TEST_FIXER"));
        }

        @Test
        @DisplayName("GET /running → should return empty list when no agents active")
        void shouldGetEmptyRunningAgents() throws Exception {
            when(orchestrator.getRunningAgents()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/agents/running")
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("GET /types → should return all registered agent types")
        void shouldGetAvailableAgentTypes() throws Exception {
            when(orchestrator.getAvailableAgentTypes()).thenReturn(List.of(
                    AgentType.PLAYWRIGHT_TEST_GENERATOR,
                    AgentType.SELF_HEALING_TEST_FIXER,
                    AgentType.FLAKY_TEST_FIXER
            ));

            mockMvc.perform(get("/api/v1/agents/types")
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0]").value("PLAYWRIGHT_TEST_GENERATOR"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private AgentActionHistory buildAction(int iteration, String actionType,
                                           boolean success, String errorMessage) {
        AgentActionHistory action = new AgentActionHistory();
        action.setId(UUID.randomUUID());
        action.setIteration(iteration);
        action.setActionType(com.company.qa.model.enums.AgentActionType.valueOf(actionType));
        action.setSuccess(success);
        action.setErrorMessage(errorMessage);
        action.setTimestamp(Instant.now().minusSeconds((long) (5 - iteration) * 10));
        action.setDurationMs((int) (500L + (iteration * 100L)));
        return action;
    }

    // ═══════════════════════════════════════════════════════════════════════════
// 8. FRONTEND APPROVAL FLOW — full journey from pending → approve → execution
// ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Frontend Approval Flow")
    class FrontendApprovalFlowTests {

        private static final UUID APPROVAL_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
        private static final UUID REVIEWER_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");

        /**
         * STEP 1 — Frontend polls GET /api/v1/approvals/pending to show the approval queue.
         * Agent has just completed GENERATE_TEST + WRITE_FILE + REQUEST_APPROVAL steps
         * and is now WAITING_FOR_APPROVAL.
         */
        @Test
        @DisplayName("STEP 1 — Frontend polls pending approvals after agent creates request")
        void step1_frontendPollsPendingApprovals() throws Exception {
            ApprovalRequestDTO pending = ApprovalRequestDTO.builder()
                    .id(APPROVAL_ID)
                    .requestType(com.company.qa.model.enums.ApprovalRequestType.TEST_GENERATION)
                    .status(ApprovalStatus.PENDING_APPROVAL)
                    .testName("SCRUM_15_UI_Test")
                    .testFramework("PLAYWRIGHT")
                    .testLanguage("Java")
                    .requestedByName("agent")
                    .requestedByEmail("agent@agent.system")
                    .generatedContent("// AI-generated Playwright test content here")
                    .createdAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(604800)) // 7 days
                    .build();

            when(approvalRequestService.getPendingApprovalRequests())
                    .thenReturn(List.of(pending));

            MvcResult result = mockMvc.perform(get("/api/v1/approvals/pending")
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(APPROVAL_ID.toString()))
                    .andExpect(jsonPath("$.data[0].status").value("PENDING_APPROVAL"))
                    .andExpect(jsonPath("$.data[0].testName").value("SCRUM_15_UI_Test"))
                    .andExpect(jsonPath("$.data[0].testFramework").value("PLAYWRIGHT"))
                    .andReturn();

            System.out.println("\n=== STEP 1: PENDING QUEUE ===");
            System.out.println(result.getResponse().getContentAsString());
            System.out.println("Approval ID to use in next steps: " + APPROVAL_ID);
            System.out.println("=============================\n");
        }

        /**
         * STEP 2 — Frontend clicks into the approval to review generated test content.
         * GET /api/v1/approvals/{id}
         */
        @Test
        @DisplayName("STEP 2 — Frontend opens approval detail to review generated test")
        void step2_frontendOpensApprovalDetail() throws Exception {
            ApprovalRequestDTO detail = ApprovalRequestDTO.builder()
                    .id(APPROVAL_ID)
                    .requestType(com.company.qa.model.enums.ApprovalRequestType.TEST_GENERATION)
                    .status(ApprovalStatus.PENDING_APPROVAL)
                    .testName("SCRUM_15_UI_Test")
                    .testFramework("PLAYWRIGHT")
                    .testLanguage("Java")
                    .requestedByName("agent")
                    .requestedByEmail("agent@agent.system")
                    // Full generated test content visible for review
                    .generatedContent("""
                        @Test
                        public void testAddToCart() {
                            page.navigate("https://www.saucedemo.com");
                            page.fill("[data-test='username']", "standard_user");
                            page.fill("[data-test='password']", "secret_sauce");
                            page.click("[data-test='login-button']");
                            page.click(".inventory_item:nth-child(1) .btn_inventory");
                            assertThat(page.locator(".shopping_cart_badge")).hasText("1");
                        }
                        """)
                    .createdAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(604800))
                    .build();

            when(approvalRequestService.getApprovalRequest(APPROVAL_ID)).thenReturn(detail);

            mockMvc.perform(get("/api/v1/approvals/{id}", APPROVAL_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(APPROVAL_ID.toString()))
                    .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                    .andExpect(jsonPath("$.data.generatedContent").exists())
                    .andExpect(jsonPath("$.data.generatedContent").isNotEmpty());
        }

        /**
         * STEP 3 — Reviewer clicks Approve in the frontend.
         * POST /api/v1/approvals/{id}/approve
         *
         * This is the critical step. After this:
         *   → ApprovalRequestService.approveRequest() runs
         *   → promoteToTestsTable() saves test to tests table
         *   → syncDraftFile() renders Java to drafts/
         *   → publishTestApprovedEvent() fires (Fix 6 from our plan)
         *   → TestApprovedEventListener picks it up AFTER_COMMIT
         *   → testExecutionService.startExecution() runs in background
         *   → On failure: autoTriggerHealingIfNeeded() triggers SelfHealingAgent
         */
        @Test
        @DisplayName("STEP 3 — Reviewer approves from frontend → service called with approved=true")
        void step3_reviewerApprovesFromFrontend() throws Exception {
            ApprovalRequestDTO approved = ApprovalRequestDTO.builder()
                    .id(APPROVAL_ID)
                    .requestType(com.company.qa.model.enums.ApprovalRequestType.TEST_GENERATION)
                    .status(ApprovalStatus.APPROVED)
                    .testName("SCRUM_15_UI_Test")
                    .testFramework("PLAYWRIGHT")
                    .requestedByName("agent")
                    .reviewedByName("harshit.singh")
                    .reviewedAt(Instant.now())
                    .approvalDecisionNotes("Test looks good, approved for CI")
                    .build();

            when(approvalRequestService.approveRequest(eq(APPROVAL_ID), any(ApprovalDecisionDTO.class)))
                    .thenReturn(approved);

            ApprovalDecisionDTO decision = ApprovalDecisionDTO.builder()
                    .reviewerId(REVIEWER_ID)
                    .notes("Test looks good, approved for CI")
                    .skipGitCommit(false) // agent handles Git itself
                    .build();

            MvcResult result = mockMvc.perform(
                            post("/api/v1/approvals/{id}/approve", APPROVAL_ID)
                                    .header("X-API-Key", API_KEY)
                                    .header("X-User-Id", REVIEWER_ID.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(decision)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"))
                    .andExpect(jsonPath("$.data.reviewedAt").exists())
                    .andExpect(jsonPath("$.data.testName").value("SCRUM_15_UI_Test"))
                    .andReturn();

            // Verify approveRequest was called with approved=true set by controller
            verify(approvalRequestService).approveRequest(
                    eq(APPROVAL_ID),
                    argThat(d -> Boolean.TRUE.equals(d.getApproved())   // controller sets this
                            && REVIEWER_ID.equals(d.getReviewerId()))
            );

            System.out.println("\n=== STEP 3: APPROVAL RESPONSE ===");
            System.out.println(result.getResponse().getContentAsString());
            System.out.println("After this: TestApprovedEvent fires → execution starts in background");
            System.out.println("================================\n");
        }

        /**
         * STEP 3b — Reviewer rejects instead.
         * POST /api/v1/approvals/{id}/reject
         * Agent should NOT continue — no execution triggered.
         */
        @Test
        @DisplayName("STEP 3b — Reviewer rejects from frontend → status REJECTED, no execution")
        void step3b_reviewerRejectsFromFrontend() throws Exception {
            ApprovalRequestDTO rejected = ApprovalRequestDTO.builder()
                    .id(APPROVAL_ID)
                    .status(ApprovalStatus.REJECTED)
                    .testName("SCRUM_15_UI_Test")
                    .reviewedByName("harshit.singh")
                    .reviewedAt(Instant.now())
                    .rejectionReason("Test logic is incorrect, regenerate")
                    .build();

            when(approvalRequestService.rejectRequest(eq(APPROVAL_ID), any(ApprovalDecisionDTO.class)))
                    .thenReturn(rejected);

            ApprovalDecisionDTO decision = ApprovalDecisionDTO.builder()
                    .reviewerId(REVIEWER_ID)
                    .notes("Test logic is incorrect, regenerate")
                    .build();

            mockMvc.perform(post("/api/v1/approvals/{id}/reject", APPROVAL_ID)
                            .header("X-API-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(decision)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"))
                    .andExpect(jsonPath("$.data.rejectionReason").value("Test logic is incorrect, regenerate"));

            // Verify approveRequest was NOT called — rejection takes different path
            verify(approvalRequestService, never()).approveRequest(any(), any());
        }

        /**
         * STEP 4 — Frontend polls agent status after approval to see if it continued.
         * Agent should resume from WAITING_FOR_APPROVAL → RUNNING → SUCCEEDED.
         * GET /api/v1/agents/{executionId}
         */
        @Test
        @DisplayName("STEP 4 — Frontend polls agent — resumed after approval (RUNNING)")
        void step4_frontendPollsAgentResumedAfterApproval() throws Exception {
            // Agent was waiting, now resumed after approval — back to RUNNING
            AgentExecution resumed = AgentExecution.builder()
                    .id(EXECUTION_ID)
                    .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                    .status(AgentStatus.RUNNING)
                    .goal(Map.of("goalType", "GENERATE_TEST",
                            "parameters", Map.of("jiraKey", "SCRUM-15")))
                    .currentIteration(5)  // was at 3 when waiting, now at 5
                    .maxIterations(20)
                    .startedAt(Instant.now().minusSeconds(300))
                    .triggeredByName(USER_NAME)
                    .build();

            when(executionService.getExecution(EXECUTION_ID)).thenReturn(resumed);

            mockMvc.perform(get("/api/v1/agents/{id}", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.currentIteration").value(5));
            // currentIteration > 3 confirms agent resumed past the approval wait point
        }

        /**
         * STEP 5 — Agent completes. Frontend sees SUCCEEDED + full action trace.
         * GET /api/v1/agents/{executionId}/actions
         *
         * This is the full happy-path action trace for PlaywrightTestGeneratorAgent:
         * FETCH_JIRA → GENERATE_TEST_CODE → WRITE_FILE → REQUEST_APPROVAL
         * → [human approves] → CREATE_BRANCH → COMMIT_CHANGES → CREATE_PULL_REQUEST
         */
        @Test
        @DisplayName("STEP 5 — Full action trace after successful approval and completion")
        void step5_fullActionTraceAfterApproval() throws Exception {
            List<AgentActionHistory> fullTrace = List.of(
                    buildAction(1, "FETCH_JIRA_STORY",    true,  null),
                    buildAction(2, "GENERATE_TEST_CODE",  true,  null),
                    buildAction(3, "WRITE_FILE",           true,  null),
                    buildAction(4, "REQUEST_APPROVAL",     true,  null),
                    // ↑ agent paused here waiting for human — approval came from frontend
                    buildAction(5, "CREATE_BRANCH",        true,  null),
                    buildAction(6, "COMMIT_CHANGES",       true,  null),
                    buildAction(7, "CREATE_PULL_REQUEST",  true,  null)
            );

            when(executionService.getActions(EXECUTION_ID)).thenReturn(fullTrace);

            MvcResult result = mockMvc.perform(get("/api/v1/agents/{id}/actions", EXECUTION_ID)
                            .header("X-API-Key", API_KEY))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(7))
                    // Pre-approval steps
                    .andExpect(jsonPath("$[0].actionType").value("FETCH_JIRA_STORY"))
                    .andExpect(jsonPath("$[0].success").value(true))
                    .andExpect(jsonPath("$[3].actionType").value("REQUEST_APPROVAL"))
                    .andExpect(jsonPath("$[3].success").value(true))
                    // Post-approval steps (these only ran AFTER human approved in frontend)
                    .andExpect(jsonPath("$[4].actionType").value("CREATE_BRANCH"))
                    .andExpect(jsonPath("$[5].actionType").value("COMMIT_CHANGES"))
                    .andExpect(jsonPath("$[6].actionType").value("CREATE_PULL_REQUEST"))
                    .andExpect(jsonPath("$[6].success").value(true))
                    .andReturn();

            System.out.println("\n=== STEP 5: FULL AGENT TRACE ===");
            System.out.println("Total steps: 7");
            System.out.println("Approval gate was between step 4 and step 5");
            System.out.println(result.getResponse().getContentAsString());
            System.out.println("================================\n");
        }

        /**
         * EDGE CASE — What if the test already existed when approved?
         * Log shows: "Test 'SCRUM_15_UI_Test' already exists in tests table — skipping promotion"
         * The approval should still succeed and event should still publish.
         */
        @Test
        @DisplayName("EDGE CASE — Approve when test already exists in DB (skips promotion)")
        void edgeCase_approveWhenTestAlreadyExistsInDb() throws Exception {
            // Same response shape — status=APPROVED regardless of whether promotion ran
            ApprovalRequestDTO approved = ApprovalRequestDTO.builder()
                    .id(APPROVAL_ID)
                    .status(ApprovalStatus.APPROVED)
                    .testName("SCRUM_15_UI_Test")
                    .reviewedAt(Instant.now())
                    .build();

            when(approvalRequestService.approveRequest(eq(APPROVAL_ID), any()))
                    .thenReturn(approved);

            mockMvc.perform(post("/api/v1/approvals/{id}/approve", APPROVAL_ID)
                            .header("X-API-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    ApprovalDecisionDTO.builder()
                                            .reviewerId(REVIEWER_ID)
                                            .notes("Re-approving after locator fix")
                                            .build())))
                    .andDo(print())
                    // Should still return 200 APPROVED — existing test in DB is not an error
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));
        }
    }


}