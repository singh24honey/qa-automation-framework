package com.company.qa.e2e;

import com.company.qa.model.agent.AgentConfig;
import com.company.qa.model.agent.AgentGoal;
import com.company.qa.model.agent.AgentResult;
import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.entity.ApprovalRequest;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.model.enums.*;
import com.company.qa.repository.AgentActionHistoryRepository;
import com.company.qa.repository.AgentExecutionRepository;
import com.company.qa.repository.ApprovalRequestRepository;
import com.company.qa.repository.JiraStoryRepository;
import com.company.qa.service.agent.AgentOrchestrator;
import com.company.qa.service.draft.DraftFileService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 15 Day 4 - Complete E2E Test for PlaywrightTestGeneratorAgent
 *
 * Tests the full pipeline from JIRA story to Pull Request:
 * 1. Fetch JIRA story (SCRUM-10)
 * 2. Generate Playwright test with AI
 * 3. Write file to drafts folder
 * 4. Create approval request
 * 5. [MANUAL] Approve via frontend UI
 * 6. Create Git branch (Mock)
 * 7. Commit changes (Mock)
 * 8. Create pull request (Mock)
 *
 * This validates the entire agent orchestration against a real JIRA story
 * using Mock Git service for safe testing.
 */
@SpringBootTest
@ActiveProfiles({"dev", "mock-git"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Week15Day4PlaywrightAgentE2ETest {

    private static final Logger log = LoggerFactory.getLogger(Week15Day4PlaywrightAgentE2ETest.class);

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private AgentExecutionRepository executionRepository;

    @Autowired
    private AgentActionHistoryRepository actionHistoryRepository;

    @Autowired
    private JiraStoryRepository jiraStoryRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private DraftFileService draftFileService;

    private static final String TEST_JIRA_KEY = "SCRUM-10";
    private static UUID executionId;
    private static AgentResult agentResult;
    private static UUID approvalRequestId;

    @BeforeAll
    static void setup() {
        log.info("=".repeat(80));
        log.info("WEEK 15 DAY 4 - PLAYWRIGHT AGENT E2E TEST");
        log.info("JIRA Story: {}", TEST_JIRA_KEY);
        log.info("Git Service: MOCK");
        log.info("Approval: FRONTEND UI (Manual)");
        log.info("=".repeat(80));
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Start Playwright Agent Execution")
    void step1_startAgentExecution() throws Exception {
        log.info("\n=== STEP 1: Starting Playwright Test Generator Agent ===");

        // Build goal - generate Playwright test for SCRUM-10
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jiraKey", TEST_JIRA_KEY);
        parameters.put("framework", "PLAYWRIGHT");
        parameters.put("testType", "UI");

        AgentGoal goal = AgentGoal.builder()
                .goalType("GENERATE_PLAYWRIGHT_TEST")
                .parameters(parameters)
                .successCriteria("Pull request created successfully for " + TEST_JIRA_KEY)
                .build();

        // Build config with appropriate timeouts for approval
        AgentConfig config = AgentConfig.builder()
                .maxIterations(15)
                .maxAICost(2.0)  // $2 budget
                .approvalTimeoutSeconds(300)  // 5 minutes for manual approval
                .build();

        log.info("Agent Goal: {}", goal.getGoalType());
        log.info("Parameters: {}", parameters);
        log.info("Config: Max Iterations={}, Max Cost=${}, Approval Timeout={}s",
                config.getMaxIterations(),
                config.getMaxAICost(),
                config.getApprovalTimeoutSeconds());

        // Start agent asynchronously
        CompletableFuture<AgentResult> future = orchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "week15-day4-test"
        );

        log.info("✅ Agent started asynchronously");

        // Wait for execution record to be created
        Thread.sleep(2000);

        // Get execution ID from database
        List<AgentExecution> executions = executionRepository
                .findByAgentTypeOrderByCreatedAtDesc(AgentType.PLAYWRIGHT_TEST_GENERATOR);

        assertThat(executions)
                .as("Should find at least one execution")
                .isNotEmpty();

        AgentExecution execution = executions.get(0);
        executionId = execution.getId();

        log.info("Execution ID: {}", executionId);
        log.info("Initial Status: {}", execution.getStatus());

        // Assertions
        assertThat(executionId).isNotNull();
        assertThat(execution.getAgentType()).isEqualTo(AgentType.PLAYWRIGHT_TEST_GENERATOR);

        // Note: Don't wait for completion yet - we need to approve first
        log.info("\n⏳ Agent is running... Will pause for approval in next step");
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Verify JIRA Story Fetched")
    void step2_verifyJiraStoryFetched() throws InterruptedException {
        log.info("\n=== STEP 2: Verifying JIRA Story Fetch ===");

        // Wait a bit for fetch action to complete
        Thread.sleep(3000);

        // Check action history for FETCH_JIRA_STORY
        var fetchAction = actionHistoryRepository
                .findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.FETCH_JIRA_STORY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("FETCH_JIRA_STORY action not found"));

        log.info("✅ FETCH_JIRA_STORY action found");
        log.info("   Success: {}", fetchAction.getSuccess());
        log.info("   Duration: {}ms", fetchAction.getDurationMs());

        assertThat(fetchAction.getSuccess()).isTrue();
        assertThat(fetchAction.getActionInput()).containsKey("jiraKey");
        assertThat(fetchAction.getActionOutput()).containsKey("summary");

        // Verify JIRA story in database
        JiraStory story = jiraStoryRepository.findByJiraKey(TEST_JIRA_KEY)
                .orElseThrow(() -> new AssertionError("JIRA story not found in database"));

        log.info("   Story Summary: {}", story.getSummary());
        log.info("   Story Type: {}", story.getStoryType());
        log.info("   Status: {}", story.getStatus());

        assertThat(story.getJiraKey()).isEqualTo(TEST_JIRA_KEY);
        assertThat(story.getSummary()).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Verify Test Code Generated with AI")
    void step3_verifyTestCodeGenerated() throws InterruptedException {
        log.info("\n=== STEP 3: Verifying Test Code Generation ===");

        // Wait for AI generation (this can take 10-30 seconds)
        Thread.sleep(15000);

        // Check action history for GENERATE_TEST_CODE
        var generateAction = actionHistoryRepository
                .findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.GENERATE_TEST_CODE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("GENERATE_TEST_CODE action not found"));

        log.info("✅ GENERATE_TEST_CODE action found");
        log.info("   Success: {}", generateAction.getSuccess());
        log.info("   AI Cost: ${}", generateAction.getAiCost());
        log.info("   Duration: {}ms", generateAction.getDurationMs());

        assertThat(generateAction.getSuccess()).isTrue();
        assertThat(generateAction.getActionOutput()).containsKey("testCode");
        assertThat(generateAction.getActionOutput()).containsKey("testClassName");
        assertThat(generateAction.getActionOutput()).containsKey("qualityScore");
        assertThat(generateAction.getAiCost()).isGreaterThan(BigDecimal.ZERO);

        String testCode = (String) generateAction.getActionOutput().get("testCode");
        String testClassName = (String) generateAction.getActionOutput().get("testClassName");
        Object qualityScore = generateAction.getActionOutput().get("qualityScore");

        log.info("   Test Class: {}", testClassName);
        log.info("   Code Length: {} characters", testCode.length());
        log.info("   Quality Score: {}", qualityScore);

        assertThat(testCode).isNotBlank();
        assertThat(testCode).contains("public class");
        assertThat(testCode).contains("Test");
        assertThat(testCode).containsIgnoringCase("playwright");
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Verify File Written to Drafts Folder")
    void step4_verifyFileWrittenToDrafts() throws InterruptedException, IOException {
        log.info("\n=== STEP 4: Verifying File Write to Drafts ===");

        // Wait for file write
        Thread.sleep(2000);

        // Check action history for WRITE_FILE
        var writeAction = actionHistoryRepository
                .findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.WRITE_FILE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("WRITE_FILE action not found"));

        log.info("✅ WRITE_FILE action found");
        log.info("   Success: {}", writeAction.getSuccess());

        assertThat(writeAction.getSuccess()).isTrue();
        assertThat(writeAction.getActionOutput()).containsKey("filePath");

        String filePath = (String) writeAction.getActionOutput().get("filePath");
        log.info("   File Path: {}", filePath);

        assertThat(filePath).contains("drafts");
        assertThat(filePath).endsWith(".java");

        // Verify file exists in drafts folder
        List<Path> drafts = draftFileService.listDrafts();
        log.info("   Total drafts: {}", drafts.size());

        boolean foundFile = drafts.stream()
                .anyMatch(p -> p.toString().contains("Scrum10") ||
                        p.toString().contains("SCRUM"));

        assertThat(foundFile)
                .as("Generated file should exist in drafts folder")
                .isTrue();

        // Log draft file names
        drafts.forEach(draft -> log.info("     - {}", draft.getFileName()));
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Verify Approval Request Created")
    void step5_verifyApprovalRequestCreated() throws InterruptedException {
        log.info("\n=== STEP 5: Verifying Approval Request ===");

        // Wait for approval request creation
        Thread.sleep(2000);

        // Check action history for REQUEST_APPROVAL
        var approvalAction = actionHistoryRepository
                .findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.REQUEST_APPROVAL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("REQUEST_APPROVAL action not found"));

        log.info("✅ REQUEST_APPROVAL action found");
        log.info("   Success: {}", approvalAction.getSuccess());

        assertThat(approvalAction.getActionOutput()).containsKey("approvalRequestId");

        approvalRequestId = UUID.fromString(
                (String) approvalAction.getActionOutput().get("approvalRequestId"));

        log.info("   Approval Request ID: {}", approvalRequestId);

        // Verify approval request in database
        ApprovalRequest approvalRequest = approvalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new AssertionError("Approval request not found in database"));

        log.info("   Status: {}", approvalRequest.getStatus());
        log.info("   Test Name: {}", approvalRequest.getTestName());
        //log.info("   Requested By: {}", approvalRequest.getRequestedBy());

        //assertThat(approvalRequest.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        //assertThat(approvalRequest.getJiraStoryKey()).isEqualTo(TEST_JIRA_KEY);

        log.info("\n" + "=".repeat(80));
        log.info("⏸️  AGENT PAUSED - WAITING FOR APPROVAL");
        log.info("=".repeat(80));
        log.info("");
        log.info("📋 MANUAL ACTION REQUIRED:");
        log.info("   1. Open frontend UI: http://localhost:3000/approvals");
        log.info("   2. Find approval request: {}", approvalRequestId);
        log.info("   3. Review the generated test code");
        log.info("   4. Click 'Approve' button");
        log.info("");
        log.info("⏱️  Waiting up to 5 minutes for approval...");
        log.info("   (Test will continue automatically after approval)");
        log.info("");
        log.info("=".repeat(80));
    }

    @Test
    @Order(6)
    @DisplayName("Step 6: Wait for Manual Approval (Frontend UI)")
    void step6_waitForManualApproval() throws InterruptedException {
        log.info("\n=== STEP 6: Waiting for Manual Approval ===");

        int maxWaitSeconds = 300;  // 5 minutes
        int checkIntervalSeconds = 5;
        int elapsed = 0;

        ApprovalRequest approvalRequest = null;

        while (elapsed < maxWaitSeconds) {
            Thread.sleep(checkIntervalSeconds * 1000);
            elapsed += checkIntervalSeconds;

            // Check approval status
            approvalRequest = approvalRequestRepository.findById(approvalRequestId)
                    .orElseThrow(() -> new AssertionError("Approval request disappeared"));

            if (approvalRequest.getStatus() == ApprovalStatus.APPROVED) {
                log.info("✅ APPROVAL RECEIVED!");
               // log.info("   Approved by: {}", approvalRequest.getApprovedBy());
               // log.info("   Approved at: {}", approvalRequest.getApprovedAt());
                log.info("   Wait time: {}s", elapsed);
                break;
            }

            if (approvalRequest.getStatus() == ApprovalStatus.REJECTED) {
                log.error("❌ APPROVAL REJECTED!");
                //log.error("   Rejected by: {}", approvalRequest.getRejectedBy());
                log.error("   Rejection reason: {}", approvalRequest.getRejectionReason());
                throw new AssertionError("Test was rejected by QA Manager");
            }

            if (elapsed % 30 == 0) {
                log.info("   Still waiting... ({}s elapsed)", elapsed);
            }
        }

        assertThat(approvalRequest)
                .as("Approval request should exist")
                .isNotNull();

        assertThat(approvalRequest.getStatus())
                .as("Test should be approved within 5 minutes")
                .isEqualTo(ApprovalStatus.APPROVED);

        log.info("\n✅ Approval received - Agent will continue with Git operations");
    }

    @Test
    @Order(7)
    @DisplayName("Step 7: Verify Git Branch Created (Mock)")
    void step7_verifyGitBranchCreated() throws InterruptedException {
        log.info("\n=== STEP 7: Verifying Git Branch Creation (Mock) ===");

        // Wait for branch creation
        Thread.sleep(5000);

        // Check action history for CREATE_BRANCH
        var branchAction = actionHistoryRepository
                .findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.CREATE_BRANCH)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CREATE_BRANCH action not found"));

        log.info("✅ CREATE_BRANCH action found");
        log.info("   Success: {}", branchAction.getSuccess());

        assertThat(branchAction.getSuccess()).isTrue();
        assertThat(branchAction.getActionOutput()).containsKey("branchName");

        String branchName = (String) branchAction.getActionOutput().get("branchName");
        log.info("   Branch Name: {}", branchName);

        assertThat(branchName).contains("feature/");
        assertThat(branchName.toLowerCase()).contains(TEST_JIRA_KEY.toLowerCase());

        log.info("   Note: Using Mock Git - no actual repository modified");
    }

    @Test
    @Order(8)
    @DisplayName("Step 8: Verify Changes Committed (Mock)")
    void step8_verifyChangesCommitted() throws InterruptedException {
        log.info("\n=== STEP 8: Verifying Git Commit (Mock) ===");

        // Wait for commit
        Thread.sleep(3000);

        // Check action history for COMMIT_CHANGES
        var commitAction = actionHistoryRepository
                .findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.COMMIT_CHANGES)
                .findFirst()
                .orElseThrow(() -> new AssertionError("COMMIT_CHANGES action not found"));

        log.info("✅ COMMIT_CHANGES action found");
        log.info("   Success: {}", commitAction.getSuccess());

        assertThat(commitAction.getSuccess()).isTrue();
        assertThat(commitAction.getActionOutput()).containsKey("commitSha");

        String commitSha = (String) commitAction.getActionOutput().get("commitSha");
        log.info("   Commit SHA: {}", commitSha);

        assertThat(commitSha).isNotBlank();
        assertThat(commitSha).startsWith("mock-");

        log.info("   Note: Using Mock Git - no actual commit created");
    }

    @Test
    @Order(9)
    @DisplayName("Step 9: Verify Pull Request Created (Mock)")
    void step9_verifyPullRequestCreated() throws InterruptedException {
        log.info("\n=== STEP 9: Verifying Pull Request (Mock) ===");

        // Wait for PR creation
        Thread.sleep(3000);

        // Check action history for CREATE_PULL_REQUEST
        var prAction = actionHistoryRepository
                .findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.CREATE_PULL_REQUEST)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CREATE_PULL_REQUEST action not found"));

        log.info("✅ CREATE_PULL_REQUEST action found");
        log.info("   Success: {}", prAction.getSuccess());

        assertThat(prAction.getSuccess()).isTrue();
        assertThat(prAction.getActionOutput()).containsKey("pullRequestUrl");

        String prUrl = (String) prAction.getActionOutput().get("pullRequestUrl");
        log.info("   Pull Request URL: {}", prUrl);

        assertThat(prUrl).isNotBlank();
        assertThat(prUrl).contains("github.com");
        assertThat(prUrl).contains("pull");

        log.info("   Note: Using Mock Git - no actual PR created");
    }

    @Test
    @Order(10)
    @DisplayName("Step 10: Verify Agent Execution Completed Successfully")
    void step10_verifyAgentExecutionComplete() {
        log.info("\n=== STEP 10: Verifying Agent Execution Status ===");

        // Reload execution from database
        AgentExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new AssertionError("Execution not found"));

        log.info("✅ Agent execution completed");
        log.info("   Status: {}", execution.getStatus());
        log.info("   Iterations: {}", execution.getCurrentIteration());
        log.info("   Total Actions: {}", execution.getTotalActions());
        log.info("   Total AI Cost: ${}", execution.getTotalAICost());

        if (execution.getCompletedAt() != null && execution.getStartedAt() != null) {
            long durationSeconds = java.time.Duration.between(
                    execution.getStartedAt(),
                    execution.getCompletedAt()
            ).getSeconds();
            log.info("   Duration: {}s", durationSeconds);
        }

        // Assertions
        assertThat(execution.getStatus()).isEqualTo(AgentStatus.SUCCEEDED);
        assertThat(execution.getCurrentIteration()).isLessThanOrEqualTo(15);
        assertThat(execution.getTotalActions()).isGreaterThanOrEqualTo(8);
        assertThat(execution.getTotalAICost()).isLessThan(BigDecimal.valueOf(2.0));
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    @Order(11)
    @DisplayName("Step 11: Verify Complete Pipeline - All 8 Actions")
    void step11_verifyCompletePipeline() {
        log.info("\n=== STEP 11: Complete Pipeline Validation ===");

        var actions = actionHistoryRepository
                .findByAgentExecutionIdOrderByIterationAsc(executionId);

        log.info("Total Actions Executed: {}", actions.size());

        // Verify all 8 expected actions executed
        assertThat(actions)
                .extracting("actionType")
                .contains(
                        AgentActionType.FETCH_JIRA_STORY,
                        AgentActionType.GENERATE_TEST_CODE,
                        AgentActionType.WRITE_FILE,
                        AgentActionType.REQUEST_APPROVAL,
                        AgentActionType.CREATE_BRANCH,
                        AgentActionType.COMMIT_CHANGES,
                        AgentActionType.CREATE_PULL_REQUEST
                );

        // Verify all actions succeeded
        long failedActions = actions.stream()
                .filter(a -> !a.getSuccess())
                .count();

        assertThat(failedActions)
                .as("All actions should succeed")
                .isEqualTo(0);

        // Calculate total AI cost
        double totalAICost = actions.stream()
                .filter(a -> a.getAiCost() != null)
                .mapToDouble(a -> a.getAiCost().doubleValue())
                .sum();

        log.info("\n📊 Pipeline Statistics:");
        log.info("   ✅ Actions Executed: {}", actions.size());
        log.info("   ✅ Actions Succeeded: {}", actions.size() - failedActions);
        log.info("   ❌ Actions Failed: {}", failedActions);
        log.info("   💰 Total AI Cost: ${}", String.format("%.4f", totalAICost));

        assertThat(totalAICost).isLessThan(2.0);

        log.info("\n" + "=".repeat(80));
        log.info("🎉 WEEK 15 DAY 4 - COMPLETE PIPELINE VALIDATED!");
        log.info("=".repeat(80));
        log.info("");
        log.info("✅ Full Agent Workflow Executed Successfully:");
        log.info("   JIRA ({}) → AI Generation → Draft File → Approval → Mock Git → PR",
                TEST_JIRA_KEY);
        log.info("");
        log.info("=".repeat(80));
    }

    @Test
    @Order(12)
    @DisplayName("Step 12: Verify Draft File Content Quality")
    void step12_verifyDraftFileQuality() throws IOException {
        log.info("\n=== STEP 12: Draft File Content Quality Check ===");

        List<Path> drafts = draftFileService.listDrafts();

        // Find the SCRUM-10 test file
        Path testFile = drafts.stream()
                .filter(p -> p.getFileName().toString().contains("Scrum10") ||
                        p.getFileName().toString().contains("SCRUM"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Generated test file not found"));

        String content = Files.readString(testFile);

        log.info("✅ Test file found: {}", testFile.getFileName());
        log.info("   File size: {} characters", content.length());

        // Verify Playwright Java structure
        assertThat(content)
                .as("Should have proper package declaration")
                .contains("package com.company.qa.playwright.tests");

        assertThat(content)
                .as("Should extend BasePlaywrightTest")
                .contains("extends BasePlaywrightTest");

        assertThat(content)
                .as("Should have @Test annotation")
                .contains("@Test");

        // Verify Playwright patterns
        assertThat(content)
                .as("Should use page navigation")
                .containsAnyOf("page.navigate", "page.goto");

        assertThat(content)
                .as("Should use page.locator for elements")
                .contains("page.locator");

        assertThat(content)
                .as("Should include assertions")
                .containsAnyOf("assertThat", "assert", "expect");

        log.info("   ✅ Package declaration correct");
        log.info("   ✅ Extends BasePlaywrightTest");
        log.info("   ✅ Contains @Test methods");
        log.info("   ✅ Uses Playwright API patterns");
        log.info("   ✅ Includes assertions");

        log.info("\n✅ Generated test has proper Playwright Java structure");
    }

    @AfterAll
    static void teardown() {
        log.info("\n" + "=".repeat(80));
        log.info("Week 15 Day 4 E2E Test Completed");
        log.info("=".repeat(80));
    }
}