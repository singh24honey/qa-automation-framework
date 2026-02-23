package com.company.qa.e2e;

import com.company.qa.model.agent.*;
import com.company.qa.model.dto.ApprovalDecisionDTO;
import com.company.qa.model.dto.ApprovalRequestDTO;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import com.company.qa.service.agent.AgentExecutionService;
import com.company.qa.service.agent.AgentOrchestrator;
import com.company.qa.service.approval.ApprovalRequestService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Integration Test — PlaywrightTestGeneratorAgent for SCRUM-15.
 *
 * DESIGNED FOR INTELLIJ DEBUG:
 *   Right-click class or method → Debug
 *   Put breakpoints anywhere in:
 *     - PlaywrightTestGeneratorAgent.plan()
 *     - FetchJiraStoryTool.execute()
 *     - GenerateTestCodeTool.execute()
 *     - CreateApprovalRequestTool.execute()
 *     - ApprovalRequestService.approveRequest()
 *     - TestApprovedEventListener.onTestApproved()
 *
 * PREREQUISITES:
 *   - Spring Boot app DB (PostgreSQL) is reachable
 *   - Redis is running
 *   - application-dev.yml has JIRA credentials configured
 *   - At least one API key exists in the api_keys table
 *
 * FLOW UNDER TEST:
 *   Step 1 → Agent starts → FETCH_JIRA_STORY (SCRUM-15)
 *   Step 2 → GENERATE_TEST_CODE (AI builds Playwright test)
 *   Step 3 → WRITE_FILE (draft saved)
 *   Step 4 → REQUEST_APPROVAL → agent pauses (WAITING_FOR_APPROVAL)
 *   Step 5 → Human approves via ApprovalRequestService
 *   Step 6 → CREATE_BRANCH → COMMIT_CHANGES → CREATE_PULL_REQUEST
 *   Step 7 → Agent reaches SUCCEEDED
 */
@SpringBootTest
@ActiveProfiles({"dev","mock-git"})
@DisplayName("E2E: PlaywrightTestGeneratorAgent — SCRUM-15 → Approve → Complete")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlaywrightAgentScrum15E2ETest {

    // ── Debug identity ─────────────────────────────────────────────────────────
    private static final UUID  DEBUG_USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DEBUG_USER_NAME = "intellij-debug";

    // ── Shared state across steps ──────────────────────────────────────────────
    /** Captured in Step 1, used in Steps 2–4 to poll status and get actions. */
    private static UUID executionId;

    /** Captured in Step 2 when agent reaches WAITING_FOR_APPROVAL. */
    private static UUID approvalRequestId;

    // ── Real Spring beans (no mocks) ───────────────────────────────────────────
    @Autowired private AgentOrchestrator     orchestrator;
    @Autowired private AgentExecutionService executionService;
    @Autowired private ApprovalRequestService approvalRequestService;

    // =========================================================================
    // STEP 1 — Trigger PlaywrightTestGeneratorAgent for SCRUM-15
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Step 1: Start agent for SCRUM-15 → verify it moves to RUNNING")
    void step1_startAgentForScrum15() throws Exception {
        printBanner("STEP 1", "Trigger PlaywrightTestGeneratorAgent for SCRUM-15");

        // ── Build goal ────────────────────────────────────────────────────────
        // PUT BREAKPOINT HERE → then step into orchestrator.startAgent()
        AgentGoal goal = AgentGoal.builder()
                .goalType("GENERATE_TEST")
                .parameters(Map.of(
                        "jiraKey",   "SCRUM-12",       // ← change story key here
                        "framework", "PLAYWRIGHT"
                ))
                .triggeredByUserId(DEBUG_USER_ID)
                .build();

        AgentConfig config = AgentConfig.builder()
                .maxIterations(20)
                .maxAICost(5.0)
                .approvalTimeoutSeconds(3600)
                .build();

        // ── Start agent (async — returns immediately) ─────────────────────────
        // BREAKPOINT → AgentOrchestrator.createAndStartAgent()
        //           → PlaywrightTestGeneratorAgent.execute()
        //           → FetchJiraStoryTool.execute()
        var execution = orchestrator.createAndStartAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal, config,
                DEBUG_USER_ID, DEBUG_USER_NAME
        );

        executionId = execution.getId();

        System.out.printf("%n→ Agent started%n");
        System.out.printf("  executionId : %s%n", executionId);
        System.out.printf("  status      : %s%n", execution.getStatus());
        System.out.printf("  iterations  : %d / %d%n%n",
                execution.getCurrentIteration(), execution.getMaxIterations());

        // Agent record was created in DB
        assertThat(executionId).isNotNull();
        assertThat(execution.getAgentType()).isEqualTo(AgentType.PLAYWRIGHT_TEST_GENERATOR);
        assertThat(execution.getStatus()).isIn(AgentStatus.RUNNING, AgentStatus.WAITING_FOR_APPROVAL);

        System.out.println("✅ Step 1 complete — agent is running");
        System.out.printf("   Copy this executionId for Steps 2–4: %s%n%n", executionId);
    }

    // =========================================================================
    // STEP 2 — Poll until agent reaches WAITING_FOR_APPROVAL
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Step 2: Poll agent until WAITING_FOR_APPROVAL (approval gate reached)")
    void step2_pollUntilWaitingForApproval() throws Exception {
        printBanner("STEP 2", "Poll → Wait for Approval Gate");
        assertThat(executionId).as("executionId must be set by Step 1").isNotNull();

        // ── Poll every 3s up to 5 minutes ─────────────────────────────────────
        // PUT BREAKPOINT HERE → step into executionService.getExecution() to
        // inspect the execution entity state at each iteration
        AgentStatus finalStatus = null;
        int maxWaitSeconds = 300;
        int pollIntervalMs  = 3000;
        int elapsed         = 0;

        System.out.println("→ Polling agent status (every 3s, max 5 min)...");

        while (elapsed < maxWaitSeconds * 1000) {
            var execution = executionService.getExecution(executionId);
            finalStatus = execution.getStatus();

            System.out.printf("  [%3ds] status=%s  iteration=%d%n",
                    elapsed / 1000, finalStatus, execution.getCurrentIteration());

            if (finalStatus == AgentStatus.WAITING_FOR_APPROVAL) {
                System.out.println("\n→ Agent paused at approval gate ✅");
                break;
            }

            if (finalStatus == AgentStatus.SUCCEEDED
                    || finalStatus == AgentStatus.FAILED
                    || finalStatus == AgentStatus.STOPPED
                    || finalStatus == AgentStatus.TIMEOUT) {
                System.out.printf("→ Agent finished early with status: %s%n", finalStatus);
                break;
            }

            Thread.sleep(pollIntervalMs);
            elapsed += pollIntervalMs;
        }

        System.out.printf("%nFinal status: %s%n", finalStatus);

        // Agent should reach approval gate (or succeed if auto-approve is on)
        assertThat(finalStatus)
                .as("Expected WAITING_FOR_APPROVAL or SUCCEEDED, got: %s", finalStatus)
                .isIn(AgentStatus.WAITING_FOR_APPROVAL, AgentStatus.SUCCEEDED);

        System.out.println("✅ Step 2 complete — approval gate reached");
    }

    // =========================================================================
    // STEP 3 — Find the pending approval created by the agent
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Step 3: Fetch pending approval request created by agent")
    void step3_fetchPendingApproval() {
        printBanner("STEP 3", "Fetch Pending Approval Request");
        assertThat(executionId).as("executionId must be set by Step 1").isNotNull();

        // ── Fetch pending approvals ────────────────────────────────────────────
        // BREAKPOINT HERE → step into getPendingApprovalRequests()
        // Inspect the ApprovalRequest entity to see what agent wrote:
        //   - generatedContent  → the AI-generated test code
        //   - testName          → test class name
        //   - requestType       → TEST_GENERATION
        List<ApprovalRequestDTO> pending = approvalRequestService.getPendingApprovalRequests();

        System.out.printf("%n→ Total pending approvals: %d%n", pending.size());
        pending.forEach(a -> System.out.printf(
                "  id=%-38s  test=%-30s  type=%s%n",
                a.getId(), a.getTestName(), a.getRequestType()));

        assertThat(pending)
                .as("No pending approvals found — did agent reach REQUEST_APPROVAL step?")
                .isNotEmpty();

        // Take the most recent one (agent just created it)
        ApprovalRequestDTO latest = pending.get(pending.size()-1);
        approvalRequestId = latest.getId();

        System.out.printf("%n→ Captured approvalRequestId: %s%n", approvalRequestId);
        System.out.printf("  testName         : %s%n", latest.getTestName());
        System.out.printf("  status           : %s%n", latest.getStatus());
        System.out.printf("  generatedContent :%n%s%n",
                latest.getGeneratedContent() != null
                        ? latest.getGeneratedContent().substring(0,
                        Math.min(300, latest.getGeneratedContent().length())) + "..."
                        : "(empty)");

        System.out.println("\n✅ Step 3 complete — approval request found");
    }

    // =========================================================================
    // STEP 4 — Reviewer approves from "frontend" (direct service call)
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Step 4: Approve request → triggers TestApprovedEvent → execution fires")
    void step4_approveRequest() {
        printBanner("STEP 4", "Reviewer Approves — Event Fires");
        assertThat(approvalRequestId).as("approvalRequestId must be set by Step 3").isNotNull();

        ApprovalDecisionDTO decision = ApprovalDecisionDTO.builder()
                .approved(true)
                .reviewerId(DEBUG_USER_ID)
                .reviewerName(DEBUG_USER_NAME)
                .reviewerEmail("debug@company.com")
                .notes("Approved via IntelliJ debug test")
                .skipGitCommit(false)
                .build();

        // PUT BREAKPOINT HERE → step into approveRequest()
        //
        // Critical path to watch inside approveRequest():
        //   1. promoteToTestsTable()           → saves test to tests table
        //   2. syncDraftFile()                  → renders .java file to drafts/
        //   3. publishTestApprovedEvent()       → publishes TestApprovedEvent
        //      └─ TestApprovedEventListener    → fires AFTER_COMMIT in background
        //         └─ testExecutionService.startExecution()
        //            └─ self.executeAsyncInternal() → Playwright runs
        //               └─ autoTriggerHealingIfNeeded() if fails ≥ 2x
        ApprovalRequestDTO result = approvalRequestService.approveRequest(approvalRequestId, decision);

        System.out.printf("%n→ Approval result:%n");
        System.out.printf("  id          : %s%n", result.getId());
        System.out.printf("  status      : %s%n", result.getStatus());
        System.out.printf("  reviewedAt  : %s%n", result.getReviewedAt());
        System.out.printf("  reviewedBy  : %s%n", result.getReviewedByName());

        assertThat(result.getStatus().name()).isEqualTo("APPROVED");
        assertThat(result.getReviewedAt()).isNotNull();

        System.out.println("\n✅ Step 4 complete — approved, TestApprovedEvent fired");
        System.out.println("   Background: TestApprovedEventListener will start test execution");
    }

    // =========================================================================
    // STEP 5 — Poll agent until SUCCEEDED after approval
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("Step 5: Agent resumes after approval → completes with SUCCEEDED")
    void step5_pollAgentToCompletion() throws Exception {
        printBanner("STEP 5", "Agent Resumes → SUCCEEDED");
        assertThat(executionId).as("executionId must be set by Step 1").isNotNull();

        // ── Poll every 3s up to 5 more minutes ────────────────────────────────
        // BREAKPOINT HERE → inspect execution state while agent runs
        // CREATE_BRANCH → COMMIT_CHANGES → CREATE_PULL_REQUEST
        AgentStatus finalStatus = null;
        int maxWaitSeconds = 300;
        int pollIntervalMs  = 3000;
        int elapsed         = 0;

        System.out.println("→ Polling post-approval (every 3s, max 5 min)...");

        while (elapsed < maxWaitSeconds * 1000) {
            var execution = executionService.getExecution(executionId);
            finalStatus = execution.getStatus();

            System.out.printf("  [%3ds] status=%s  iteration=%d%n",
                    elapsed / 1000, finalStatus, execution.getCurrentIteration());

            if (finalStatus == AgentStatus.SUCCEEDED
                    || finalStatus == AgentStatus.FAILED
                    || finalStatus == AgentStatus.STOPPED
                    || finalStatus == AgentStatus.TIMEOUT
                    || finalStatus == AgentStatus.BUDGET_EXCEEDED) {
                break;
            }

            Thread.sleep(pollIntervalMs);
            elapsed += pollIntervalMs;
        }

        System.out.printf("%nFinal status: %s%n%n", finalStatus);

        assertThat(finalStatus)
                .as("Agent did not complete successfully — got: %s", finalStatus)
                .isEqualTo(AgentStatus.SUCCEEDED);

        System.out.println("✅ Step 5 complete — agent SUCCEEDED");
    }

    // =========================================================================
    // STEP 6 — Print full action trace for inspection
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Step 6: Print full action history trace")
    void step6_printFullActionTrace() {
        printBanner("STEP 6", "Full Action History");
        assertThat(executionId).as("executionId must be set by Step 1").isNotNull();

        // BREAKPOINT HERE → inspect each AgentActionHistory entity:
        //   actionType, actionInput, actionOutput, success, durationMs, aiCost
        var actions = executionService.getActions(executionId);

        System.out.printf("Total actions: %d%n%n", actions.size());
        System.out.printf("%-5s %-35s %-8s %-10s %s%n",
                "Iter", "Action", "Success", "DurationMs", "Error");
        System.out.println("-".repeat(80));

        actions.forEach(a -> System.out.printf("%-5d %-35s %-8s %-10s %s%n",
                a.getIteration(),
                a.getActionType(),
                a.getSuccess(),
                a.getDurationMs(),
                a.getErrorMessage() != null ? a.getErrorMessage() : ""));

        assertThat(actions).isNotEmpty();

        // Verify the expected 7-step happy path
        var actionTypes = actions.stream()
                .map(a -> a.getActionType().name())
                .toList();

        System.out.println("\nAction sequence: " + actionTypes);

        assertThat(actionTypes).contains(
                "FETCH_JIRA_STORY",
                "GENERATE_TEST_CODE",
                "WRITE_FILE",
                "REQUEST_APPROVAL",
                "CREATE_BRANCH",
                "COMMIT_CHANGES",
                "CREATE_PULL_REQUEST"
        );

        System.out.println("\n✅ Step 6 complete — all 7 agent steps confirmed");
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║  🎉 SCRUM-15 FULL FLOW E2E COMPLETE 🎉      ║");
        System.out.println("║  JIRA → Generate → Approve → PR Created      ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void printBanner(String step, String subtitle) {
        System.out.printf("%n╔══════════════════════════════════════════════╗%n");
        System.out.printf("║  %-44s║%n", step);
        System.out.printf("║  %-44s║%n", subtitle);
        System.out.printf("╚══════════════════════════════════════════════╝%n");
    }
}