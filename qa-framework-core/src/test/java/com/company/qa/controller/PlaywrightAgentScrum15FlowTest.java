package com.company.qa.controller;

import com.company.qa.config.JpaAuditingConfig;
import com.company.qa.config.SecurityConfig;
import com.company.qa.model.agent.*;
import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.dto.ApprovalDecisionDTO;
import com.company.qa.model.dto.ApprovalRequestDTO;
import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import com.company.qa.model.enums.ApprovalStatus;
import com.company.qa.security.ApiKeyAuthenticationFilter;
import com.company.qa.service.ApiKeyService;
import com.company.qa.service.agent.AgentExecutionService;
import com.company.qa.service.agent.AgentOrchestrator;
import com.company.qa.service.approval.ApprovalRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end flow: trigger PlaywrightTestGeneratorAgent for SCRUM-15 → agent completes → reviewer approves.
 *
 * Step 1 — POST /api/v1/agents/start          → agent kicks off
 * Step 2 — GET  /api/v1/agents/{executionId}  → poll until WAITING_FOR_APPROVAL
 * Step 3 — POST /api/v1/approvals/{id}/approve → reviewer approves from frontend
 * Step 4 — GET  /api/v1/agents/{executionId}  → agent resumes and SUCCEEDS
 */
@WebMvcTest(
        controllers = {AgentController.class, ApprovalRequestController.class},
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, ApiKeyAuthenticationFilter.class,
                        JpaAuditingConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class PlaywrightAgentScrum15FlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AgentOrchestrator orchestrator;
    @MockBean AgentExecutionService executionService;
    @MockBean ApprovalRequestService approvalRequestService;
    @MockBean ApiKeyService apiKeyService;

    private static final UUID EXECUTION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID APPROVAL_ID  = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID REVIEWER_ID  = UUID.fromString("cccccccc-dddd-eeee-ffff-aaaaaaaaaaaa");

    @Test
    void scrum15_triggerAgent_waitForApproval_approveAndComplete() throws Exception {

        // ── STEP 1: Trigger PlaywrightTestGeneratorAgent for SCRUM-15 ──────────
        AgentExecution started = AgentExecution.builder()
                .id(EXECUTION_ID)
                .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                .status(AgentStatus.RUNNING)
                .goal(Map.of("goalType", "GENERATE_TEST",
                        "parameters", Map.of("jiraKey", "SCRUM-15", "framework", "PLAYWRIGHT")))
                .currentIteration(0)
                .maxIterations(20)
                .startedAt(Instant.now())
                .triggeredByName("debug-user")
                .build();

        when(orchestrator.createAndStartAgent(
                eq(AgentType.PLAYWRIGHT_TEST_GENERATOR), any(), any(), any(), any()
        )).thenReturn(started);

        MvcResult startResult = mockMvc.perform(post("/api/v1/agents/start")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                StartAgentRequest.builder()
                                        .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                                        .goalType("GENERATE_TEST")
                                        .parameters(Map.of("jiraKey", "SCRUM-15", "framework", "PLAYWRIGHT"))
                                        .maxIterations(20)
                                        .maxAICost(5.0)
                                        .build())))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionId").value(EXECUTION_ID.toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andReturn();

        System.out.println("\n[STEP 1] Agent started: " + startResult.getResponse().getContentAsString());

        // ── STEP 2: Poll — agent reached approval gate ──────────────────────────
        AgentExecution waitingForApproval = AgentExecution.builder()
                .id(EXECUTION_ID)
                .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                .status(AgentStatus.WAITING_FOR_APPROVAL)
                .goal(Map.of("goalType", "GENERATE_TEST"))
                .currentIteration(4)  // completed: FETCH_JIRA → GENERATE → WRITE_FILE → REQUEST_APPROVAL
                .maxIterations(20)
                .startedAt(Instant.now().minusSeconds(45))
                .triggeredByName("debug-user")
                .build();

        when(executionService.getExecution(EXECUTION_ID)).thenReturn(waitingForApproval);

        MvcResult pollResult = mockMvc.perform(get("/api/v1/agents/{id}", EXECUTION_ID)
                        .header("X-API-Key", "test-key"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_APPROVAL"))
                .andExpect(jsonPath("$.currentIteration").value(4))
                .andReturn();

        System.out.println("\n[STEP 2] Agent waiting for approval: " + pollResult.getResponse().getContentAsString());

        // ── STEP 3: Reviewer approves from frontend ─────────────────────────────
        ApprovalRequestDTO approved = ApprovalRequestDTO.builder()
                .id(APPROVAL_ID)
                .status(ApprovalStatus.APPROVED)
                .testName("SCRUM_15_UI_Test")
                .testFramework("PLAYWRIGHT")
                .requestedByName("agent")
                .reviewedByName("debug-user")
                .reviewedAt(Instant.now())
                .approvalDecisionNotes("Looks good, approved")
                .build();

        when(approvalRequestService.approveRequest(eq(APPROVAL_ID), any()))
                .thenReturn(approved);

        MvcResult approvalResult = mockMvc.perform(post("/api/v1/approvals/{id}/approve", APPROVAL_ID)
                        .header("X-API-Key", "test-key")
                        .header("X-User-Id", REVIEWER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ApprovalDecisionDTO.builder()
                                        .reviewerId(REVIEWER_ID)
                                        .reviewerName("debug-user")
                                        .reviewerEmail("debug@company.com")
                                        .notes("Looks good, approved")
                                        .skipGitCommit(false)
                                        .build())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.testName").value("SCRUM_15_UI_Test"))
                .andExpect(jsonPath("$.data.reviewedAt").exists())
                .andReturn();

        System.out.println("\n[STEP 3] Approved: " + approvalResult.getResponse().getContentAsString());
        System.out.println("         → After this: TestApprovedEvent fires → execution starts in background");

        // ── STEP 4: Poll again — agent resumed and completed ────────────────────
        AgentExecution succeeded = AgentExecution.builder()
                .id(EXECUTION_ID)
                .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                .status(AgentStatus.SUCCEEDED)
                .goal(Map.of("goalType", "GENERATE_TEST"))
                .currentIteration(7)  // all 7 steps done: +CREATE_BRANCH, COMMIT, CREATE_PR
                .maxIterations(20)
                .startedAt(Instant.now().minusSeconds(120))
                .completedAt(Instant.now())
                .triggeredByName("debug-user")
                .build();

        when(executionService.getExecution(EXECUTION_ID)).thenReturn(succeeded);

        MvcResult doneResult = mockMvc.perform(get("/api/v1/agents/{id}", EXECUTION_ID)
                        .header("X-API-Key", "test-key"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.currentIteration").value(7))
                .andExpect(jsonPath("$.completedAt").exists())
                .andReturn();

        System.out.println("\n[STEP 4] Agent completed: " + doneResult.getResponse().getContentAsString());
        System.out.println("\n✅ Full flow done: SCRUM-15 → generated → approved → PR created\n");
    }
}