package com.company.qa.e2e;

import com.company.qa.model.agent.AgentConfig;
import com.company.qa.model.agent.AgentGoal;
import com.company.qa.model.agent.AgentResult;

import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import com.company.qa.repository.AgentActionHistoryRepository;
import com.company.qa.repository.AgentExecutionRepository;
import com.company.qa.repository.JiraStoryRepository;
import com.company.qa.service.agent.AgentOrchestrator;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End test for PlaywrightTestGeneratorAgent.
 *
 * Tests the complete autonomous agent workflow:
 * 1. Fetch JIRA story
 * 2. Generate test code with AI
 * 3. Write file to AiDraft folder
 * 4. Create approval request
 * 5. Create Git branch
 * 6. Commit changes
 * 7. Create pull request
 *
 * This validates the entire agent orchestration, tool execution,
 * and state management.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlaywrightTestGeneratorAgentE2ETest {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightTestGeneratorAgentE2ETest.class);

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private AgentExecutionRepository executionRepository;

    @Autowired
    private AgentActionHistoryRepository actionHistoryRepository;

    @Autowired
    private JiraStoryRepository jiraStoryRepository;

    private static final String TEST_JIRA_KEY = "SCRUM-9";
    private static UUID executionId;
    private static AgentResult agentResult;

    @BeforeAll
    static void setup() {
        log.info("=".repeat(80));
        log.info("STARTING PLAYWRIGHT TEST GENERATOR AGENT E2E TEST");
        log.info("=".repeat(80));
    }

    @Test
    @Order(1)
    @DisplayName("E2E Step 1: Start Agent Execution")
    void step1_startAgentExecution() throws Exception {
        log.info("=== STEP 1: Starting Playwright Test Generator Agent ===");

        // Build goal
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jiraKey", TEST_JIRA_KEY);
        parameters.put("framework", "PLAYWRIGHT");

        AgentGoal goal = AgentGoal.builder()
                .goalType("GENERATE_TEST")
                .parameters(parameters)
                .successCriteria("Pull request created successfully")
                .build();

        // Build config
        AgentConfig config = AgentConfig.builder()
                .maxIterations(10)
                .maxAICost(1.0)
                .approvalTimeoutSeconds(3600)
                .build();

        log.info("Goal: {}", goal);
        log.info("Config: Max Iterations={}, Max Cost=${}",
                config.getMaxIterations(), config.getMaxAICost());

        // Start agent asynchronously
        CompletableFuture<AgentResult> future = orchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "e2e-test-user"
        );

        log.info("Agent started asynchronously...");

        // Get execution ID from database
        Thread.sleep(2000); // Allow time for execution record creation

        // ✅ Get the ACTUAL execution ID using corrected method
        List<AgentExecution> execution = executionRepository
                .findByAgentTypeOrderByCreatedAtDesc(AgentType.PLAYWRIGHT_TEST_GENERATOR);

        assertThat(execution)
                .isNotEmpty()
                .as("Should find at least one execution for PLAYWRIGHT_TEST_GENERATOR");

        this.executionId = execution.get(0).getId();
        // Assertions
        assertThat(executionId).isNotNull();
        assertThat(execution.get(0).getStatus()).isIn(AgentStatus.RUNNING, AgentStatus.WAITING_FOR_APPROVAL);
        assertThat(execution.get(0).getAgentType()).isEqualTo(AgentType.PLAYWRIGHT_TEST_GENERATOR);
        assertThat(execution.get(0).getCurrentIteration()).isGreaterThanOrEqualTo(0);

        // Wait for agent to complete (max 5 minutes)
        log.info("Waiting for agent to complete (timeout: 5 minutes)...");
        agentResult = future.get(3, TimeUnit.MINUTES);

        log.info("Agent completed with status: {}", agentResult.getStatus());
    }

    @Test
    @Order(2)
    @DisplayName("E2E Step 2: Verify JIRA Story Fetched")
    void step2_verifyJiraStoryFetched() {
        log.info("=== STEP 2: Verifying JIRA Story Fetch ===");

        // Check action history for FETCH_JIRA_STORY
        var fetchAction = actionHistoryRepository.findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.FETCH_JIRA_STORY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("FETCH_JIRA_STORY action not found"));

        log.info("SUCCESS: FETCH_JIRA_STORY action found");
        log.info("  - Success: {}", fetchAction.getSuccess());
        log.info("  - Duration: {}ms", fetchAction.getDurationMs());

        assertThat(fetchAction.getSuccess()).isTrue();
        assertThat(fetchAction.getActionInput()).containsKey("jiraKey");
        assertThat(fetchAction.getActionOutput()).containsKey("summary");

        // Verify JIRA story in database
        JiraStory story = jiraStoryRepository.findByJiraKey(TEST_JIRA_KEY)
                .orElseThrow(() -> new AssertionError("JIRA story not found in database"));

        log.info("  - Story Summary: {}", story.getSummary());
        log.info("  - Story Type: {}", story.getStoryType());

        assertThat(story.getJiraKey()).isEqualTo(TEST_JIRA_KEY);
        assertThat(story.getSummary()).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("E2E Step 3: Verify Test Code Generated")
    void step3_verifyTestCodeGenerated() {
        log.info("=== STEP 3: Verifying Test Code Generation ===");

        // Check action history for GENERATE_TEST_CODE
        var generateAction = actionHistoryRepository.findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.GENERATE_TEST_CODE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("GENERATE_TEST_CODE action not found"));

        log.info("SUCCESS: GENERATE_TEST_CODE action found");
        log.info("  - Success: {}", generateAction.getSuccess());
        log.info("  - AI Cost: ${}", generateAction.getAiCost());
        log.info("  - Duration: {}ms", generateAction.getDurationMs());

        assertThat(generateAction.getSuccess()).isTrue();
        assertThat(generateAction.getActionOutput()).containsKey("testCode");
        assertThat(generateAction.getActionOutput()).containsKey("testClassName");
        assertThat(generateAction.getActionOutput()).containsKey("qualityScore");
        assertThat(generateAction.getAiCost()).isGreaterThan(BigDecimal.valueOf(0.0));

        String testCode = (String) generateAction.getActionOutput().get("testCode");
        log.info("  - Test Code Length: {} chars", testCode.length());

        assertThat(testCode).isNotBlank();
        assertThat(testCode).contains("public class");
        assertThat(testCode).contains("Test");
    }

    @Test
    @Order(4)
    @DisplayName("E2E Step 4: Verify File Written")
    void step4_verifyFileWritten() {
        log.info("=== STEP 4: Verifying File Write ===");

        // Check action history for WRITE_FILE
        var writeAction = actionHistoryRepository.findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.WRITE_FILE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("WRITE_FILE action not found"));

        log.info("SUCCESS: WRITE_FILE action found");
        log.info("  - Success: {}", writeAction.getSuccess());

        assertThat(writeAction.getSuccess()).isTrue();
        assertThat(writeAction.getActionOutput()).containsKey("filePath");

        String filePath = (String) writeAction.getActionOutput().get("filePath");
        log.info("  - File Path: {}", filePath);

        assertThat(filePath).contains("AiDraft");
        assertThat(filePath).endsWith(".java");
    }

    @Test
    @Order(5)
    @DisplayName("E2E Step 5: Verify Approval Request Created")
    void step5_verifyApprovalRequestCreated() {
        log.info("=== STEP 5: Verifying Approval Request ===");

        // Check action history for REQUEST_APPROVAL
        var approvalAction = actionHistoryRepository.findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.REQUEST_APPROVAL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("REQUEST_APPROVAL action not found"));

        log.info("SUCCESS: REQUEST_APPROVAL action found");
        log.info("  - Success: {}", approvalAction.getSuccess());

//        assertThat(approvalAction.getSuccess()).isTrue();
        assertThat(approvalAction.getActionOutput()).containsKey("approvalRequestId");
        assertThat(approvalAction.getActionOutput()).containsKey("status");

        UUID approvalRequestId = UUID.fromString(
                (String) approvalAction.getActionOutput().get("approvalRequestId"));

        log.info("  - Approval Request ID: {}", approvalRequestId);

        assertThat(approvalRequestId).isNotNull();
    }

    @Test
    @Order(6)
    @DisplayName("E2E Step 6: Verify Git Branch Created")
    void step6_verifyGitBranchCreated() {
        log.info("=== STEP 6: Verifying Git Branch Creation ===");

        // Check action history for CREATE_BRANCH
        var branchAction = actionHistoryRepository.findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.CREATE_BRANCH)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CREATE_BRANCH action not found"));

        log.info("SUCCESS: CREATE_BRANCH action found");
        log.info("  - Success: {}", branchAction.getSuccess());

        assertThat(branchAction.getSuccess()).isTrue();
        assertThat(branchAction.getActionOutput()).containsKey("branchName");

        String branchName = (String) branchAction.getActionOutput().get("branchName");
        log.info("  - Branch Name: {}", branchName);

        assertThat(branchName).contains("feature/");
        assertThat(branchName).containsIgnoringCase(TEST_JIRA_KEY);
    }

    @Test
    @Order(7)
    @DisplayName("E2E Step 7: Verify Changes Committed")
    void step7_verifyChangesCommitted() {
        log.info("=== STEP 7: Verifying Git Commit ===");

        // Check action history for COMMIT_CHANGES
        var commitAction = actionHistoryRepository.findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.COMMIT_CHANGES)
                .findFirst()
                .orElseThrow(() -> new AssertionError("COMMIT_CHANGES action not found"));

        log.info("SUCCESS: COMMIT_CHANGES action found");
        log.info("  - Success: {}", commitAction.getSuccess());

        assertThat(commitAction.getSuccess()).isTrue();
        assertThat(commitAction.getActionOutput()).containsKey("commitSha");

        String commitSha = (String) commitAction.getActionOutput().get("commitSha");
        log.info("  - Commit SHA: {}", commitSha);

        assertThat(commitSha).isNotBlank();
    }

    @Test
    @Order(8)
    @DisplayName("E2E Step 8: Verify Pull Request Created")
    void step8_verifyPullRequestCreated() {
        log.info("=== STEP 8: Verifying Pull Request ===");

        // Check action history for CREATE_PULL_REQUEST
        var prAction = actionHistoryRepository.findByAgentExecutionIdOrderByIterationAsc(executionId)
                .stream()
                .filter(action -> action.getActionType() == AgentActionType.CREATE_PULL_REQUEST)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CREATE_PULL_REQUEST action not found"));

        log.info("SUCCESS: CREATE_PULL_REQUEST action found");
        log.info("  - Success: {}", prAction.getSuccess());

        assertThat(prAction.getSuccess()).isTrue();
        assertThat(prAction.getActionOutput()).containsKey("pullRequestUrl");

        String prUrl = (String) prAction.getActionOutput().get("pullRequestUrl");
        log.info("  - Pull Request URL: {}", prUrl);

        assertThat(prUrl).isNotBlank();
        assertThat(prUrl).contains("github.com");
    }

    @Test
    @Order(9)
    @DisplayName("E2E Step 9: Verify Agent Execution Complete")
    void step9_verifyAgentExecutionComplete() {
        log.info("=== STEP 9: Verifying Agent Execution Status ===");

        // Reload execution from database
        AgentExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new AssertionError("Execution not found"));

        log.info("SUCCESS: Agent execution completed");
        log.info("  - Status: {}", execution.getStatus());
        log.info("  - Iterations Completed: {}", execution.getCurrentIteration());
        log.info("  - Total Actions: {}", execution.getTotalActions());
        log.info("  - Total AI Cost: ${}", execution.getTotalAICost());
        log.info("  - Duration: {}s",
                java.time.Duration.between(execution.getStartedAt(), execution.getCompletedAt()).getSeconds());

        // Assertions
        assertThat(execution.getStatus()).isEqualTo(AgentStatus.SUCCEEDED);
        assertThat(execution.getCurrentIteration()).isLessThanOrEqualTo(20);
        assertThat(execution.getTotalActions()).isGreaterThanOrEqualTo(7); // All 7 actions executed
        assertThat(execution.getTotalAICost()).isLessThan(BigDecimal.valueOf(5.0)); // Within budget
        assertThat(execution.getCompletedAt()).isNotNull();

        // Verify agent result
        assertThat(agentResult).isNotNull();
        assertThat(agentResult.getStatus()).isEqualTo(AgentStatus.SUCCEEDED);
        assertThat(agentResult.getOutputs()).containsKey("pullRequestUrl");
    }

    @Test
    @Order(10)
    @DisplayName("E2E Step 10: Verify Complete Pipeline Integration")
    void step10_verifyCompletePipeline() {
        log.info("=== STEP 10: Pipeline Integration Summary ===");

        var actions = actionHistoryRepository.findByAgentExecutionIdOrderByIterationAsc(executionId);

        log.info("SUCCESS: Complete agent workflow executed!");
        log.info("  Total Actions: {}", actions.size());

        // Verify all expected actions executed
        assertThat(actions).extracting("actionType")
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
        assertThat(actions).allMatch(action -> action.getSuccess(),
                "All actions should succeed");

        // Calculate total cost
        double totalCost = actions.stream()
                .filter(a -> a.getAiCost() != null)
                .mapToDouble(a -> a.getAiCost().doubleValue())
                .sum();


        log.info("  Total AI Cost: ${}", totalCost);
        assertThat(totalCost).isLessThan(5.0);

        log.info("=".repeat(80));
        log.info("E2E TEST PASSED - Autonomous agent works end-to-end! ✅");
        log.info("=".repeat(80));
    }

    @AfterAll
    static void teardown() {
        log.info("E2E test completed successfully");
    }
}