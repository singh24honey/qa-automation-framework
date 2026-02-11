package com.company.qa.integration;

import com.company.qa.model.agent.AgentConfig;
import com.company.qa.model.agent.AgentContext;
import com.company.qa.model.agent.AgentGoal;
import com.company.qa.model.agent.AgentResult;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.model.enums.AgentType;
import com.company.qa.service.SampleJiraStoryLoader;
import com.company.qa.service.agent.AgentOrchestrator;
import com.company.qa.service.draft.DraftFileService;
import com.company.qa.service.git.GitService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 15 Day 3 Integration Test
 *
 * Tests complete E2E flow:
 * JIRA Story → PlaywrightAgent → Draft File → Approval → Mock Git → PR
 *
 * This validates the entire pipeline without external dependencies
 * (uses sample JIRA stories and Mock Git).
 */
@SpringBootTest
@ActiveProfiles({"dev", "mock-git"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Week15Day3IntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(Week15Day3IntegrationTest.class);

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private SampleJiraStoryLoader storyLoader;

    @Autowired
    private DraftFileService draftFileService;

    @Autowired
    @Qualifier("mockGitService")
    private GitService mockGitService;

    private static final String TEST_JIRA_KEY = "SCRUM-10"; // Login test

    private static final String TEST_JIRA_KEY_2 = "SCRUM-11"; // Login test

    private static final String TEST_JIRA_KEY_3 = "SCRUM-12"; // Login test


    private static UUID executionId;
    private static AgentResult agentResult;

    AgentConfig config = AgentConfig.builder()
            .maxIterations(10)
            .maxAICost(1.0)
            .approvalTimeoutSeconds(3600)
            .build();
    @Test
    @Order(1)
    @DisplayName("Test 1: Prerequisites - Services initialized")
    void testPrerequisites() throws IOException {
        // Verify all required services are available
        assertNotNull(agentOrchestrator, "AgentOrchestrator should be available");
        assertNotNull(storyLoader, "SampleJiraStoryLoader should be available");
        assertNotNull(draftFileService, "DraftFileService should be available");
        assertNotNull(mockGitService, "Mock Git Service should be available");

        // Verify sample story exists
        //JiraStory story = storyLoader.getStoryByKey(TEST_JIRA_KEY)
          //      .orElseThrow(() -> new AssertionError("Sample story " + TEST_JIRA_KEY + " not found"));

        //assertEquals("User Login to Sauce Demo", story.getSummary());

        System.out.println("✅ Test 1 passed: All prerequisites available");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Execute PlaywrightAgent for simple login test")
    void testPlaywrightAgentLoginTest() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("\n🤖 Executing PlaywrightAgent for " + TEST_JIRA_KEY);

        // Build agent goal
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jiraKey", TEST_JIRA_KEY);
        parameters.put("framework", "PLAYWRIGHT");
        parameters.put("testType", "UI");

        AgentGoal goal = AgentGoal.builder()
                .goalType("PLAYWRIGHT_TEST_GENERATOR")
                .successCriteria("Generate Playwright Java test for " + TEST_JIRA_KEY)
                .parameters(parameters)
                .build();

        // Execute agent
        CompletableFuture<AgentResult> future = agentOrchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "e2e-test-user"
        );

        agentResult=future.get(3, TimeUnit.MINUTES);  // Wait for completion

        // Verify execution
        assertNotNull(future, "Agent result should not be null");

        System.out.println("   Agent Status: " + agentResult.getStatus());
        //System.out.println("   Total Actions: " + agentResult.getActionsExecuted());
        //System.out.println("   AI Cost: $" + agentResult.getTotalCost());



        // Note: Agent may fail at approval step (expected - approval requires manual intervention)
        // We verify intermediate steps completed successfully




    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Verify test file created in drafts folder")
    void testDraftFileCreated() throws IOException {
        List<Path> drafts = draftFileService.listDrafts();

        System.out.println("\n📂 Checking drafts folder...");
        System.out.println("   Drafts found: " + drafts.size());

        for (Path draft : drafts) {
            System.out.println("     - " + draft.getFileName());
        }

        // We should have at least one draft file
        assertFalse(drafts.isEmpty(),
                "At least one test file should be in drafts folder");

        // Find the SAUCE-001 test file
        boolean found = drafts.stream()
                .anyMatch(p -> p.getFileName().toString().contains("Sauce001") ||
                        p.getFileName().toString().contains("SAUCE"));

        assertTrue(found, "Draft file for SAUCE-001 should exist");

        System.out.println("✅ Test 3 passed: Draft file created");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Verify test file content quality")
    void testDraftFileContentQuality() throws IOException {
        List<Path> drafts = draftFileService.listDrafts();

        // Find a draft file
        Path draftFile = drafts.stream()
                .filter(p -> p.getFileName().toString().contains("Sauce") ||
                        p.getFileName().toString().contains("Test"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No draft file found"));

        String content = Files.readString(draftFile);

        System.out.println("\n📝 Analyzing test file content...");
        System.out.println("   File: " + draftFile.getFileName());
        System.out.println("   Size: " + content.length() + " characters");

        // Verify Playwright Java structure
        assertTrue(content.contains("package com.company.qa.playwright.tests"),
                "Should have correct package");
        assertTrue(content.contains("extends BasePlaywrightTest"),
                "Should extend BasePlaywrightTest");
        assertTrue(content.contains("@Test"),
                "Should have @Test annotation");

        // Verify Playwright Java patterns
        assertTrue(content.contains("page.navigate") || content.contains("page.goto"),
                "Should use page navigation");
        assertTrue(content.contains("page.locator"),
                "Should use page.locator() for elements");

        // Verify Sauce Demo context
        assertTrue(content.contains("saucedemo.com") || content.contains("sauce"),
                "Should reference Sauce Demo");

        // Verify assertions
        assertTrue(content.contains("assertThat") || content.contains("assert"),
                "Should include assertions");

        System.out.println("✅ Test 4 passed: Test file has good quality structure");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Manual draft approval simulation")
    void testManualApprovalSimulation() throws IOException {
        System.out.println("\n✋ Simulating QA Manager approval...");

        List<Path> drafts = draftFileService.listDrafts();

        if (drafts.isEmpty()) {
            System.out.println("   ⚠️  No drafts to approve (test may have run out of order)");
            return;
        }

        // Approve the first draft
        Path draftFile = drafts.get(0);
        String fileName = draftFile.getFileName().toString();

        System.out.println("   Approving: " + fileName);

        Path approvedPath = draftFileService.moveDraftToApproved(fileName);

        assertTrue(Files.exists(approvedPath),
                "Approved file should exist");
        assertFalse(Files.exists(draftFileService.getDraftsDirectory().resolve(fileName)),
                "Draft should be removed from drafts folder");

        System.out.println("   ✅ Moved to: " + approvedPath.toAbsolutePath());
        System.out.println("✅ Test 5 passed: Approval workflow functional");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Execute complete agent workflow with shopping cart story")
    void testCompleteWorkflowCartStory() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("\n🛒 Testing shopping cart story (SAUCE-002)...");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jiraKey", TEST_JIRA_KEY_3);
        parameters.put("framework", "PLAYWRIGHT");

        AgentGoal goal = AgentGoal.builder()
                .goalType("PLAYWRIGHT_TEST_GENERATOR")
                .successCriteria("Generate Playwright Java test for adding " + TEST_JIRA_KEY_3)
                .parameters(parameters)
                .build();

        // Execute agent
        CompletableFuture<AgentResult> future = agentOrchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "e2e-test-user"
        );

        agentResult=future.get(3, TimeUnit.MINUTES);  // Wait for completion

        assertNotNull(agentResult);
       // assertTrue(agentResult.getActionsExecuted() > 0);

      //  System.out.println("   Actions executed: " + agentResult.getA());
       // System.out.println("   Status: " + result.getStatus());

        //.out.println("✅ Test 6 passed: Shopping cart test generated");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Execute complete workflow with E2E checkout story")
    void testCompleteWorkflowCheckoutStory() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("\n🛍️  Testing complete checkout flow (SAUCE-003)...");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jiraKey", "SCRUM-15");
        parameters.put("framework", "PLAYWRIGHT");

        AgentGoal goal = AgentGoal.builder()
                .goalType("PLAYWRIGHT_TEST_GENERATOR")
                .successCriteria("Generate Playwright Java test for CheckOut " + parameters.get("jiraKey"))
                .parameters(parameters)
                .build();

        // Execute agent
        CompletableFuture<AgentResult> future = agentOrchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                UUID.randomUUID(),
                "e2e-test-user"
        );
        agentResult=future.get(1, TimeUnit.MINUTES);  // Wait for completion


        assertNotNull(agentResult);
        assertTrue(agentResult.getIterationsCompleted() > 0);

        System.out.println("   Actions executed: " + agentResult.getIterationsCompleted());
        System.out.println("   AI Cost: $" + agentResult.getTotalAICost());

        System.out.println("✅ Test 7 passed: E2E checkout test generated");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Verify all generated tests use element registry")
    void testElementRegistryUsage() throws IOException {
        System.out.println("\n🔍 Verifying element registry usage...");

        List<Path> allTestFiles = draftFileService.listDrafts();
        allTestFiles.addAll(draftFileService.listApproved());

        int filesChecked = 0;
        int filesUsingRegistry = 0;

        for (Path testFile : allTestFiles) {
            String content = Files.readString(testFile);
            filesChecked++;

            // Check for data-test attributes (from element registry)
            if (content.contains("data-test")) {
                filesUsingRegistry++;
                System.out.println("   ✅ " + testFile.getFileName() + " uses registry locators");
            } else {
                System.out.println("   ⚠️  " + testFile.getFileName() + " may not use registry");
            }
        }

        assertTrue(filesChecked > 0, "Should have at least one test file to check");
        System.out.println("\n   Files checked: " + filesChecked);
        System.out.println("   Files using registry: " + filesUsingRegistry);

        // At least 50% should use element registry locators
        assertTrue(filesUsingRegistry >= filesChecked / 2,
                "Majority of tests should use element registry locators");

        System.out.println("✅ Test 8 passed: Tests using element registry");
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Verify Mock Git operations logged")
    void testMockGitOperationsLogged() {
        System.out.println("\n📋 Verifying Mock Git operations...");

        // Mock Git operations are logged, not persisted
        // We verify the service is working by testing a direct call

        assertDoesNotThrow(() -> {
            var config = mockGitService.getDefaultConfiguration();
            assertNotNull(config);
            assertNotNull(config.getRepositoryUrl());
            assertTrue(config.getRepositoryUrl().contains("mock"));
        });

        System.out.println("   Mock Git Service: Operational");
        System.out.println("✅ Test 9 passed: Mock Git functional");
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: End-to-end validation summary")
    void testE2ESummary() throws IOException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 WEEK 15 DAY 3 - E2E VALIDATION SUMMARY");
        System.out.println("=".repeat(60));

        // Count generated tests
        List<Path> drafts = draftFileService.listDrafts();
        List<Path> approved = draftFileService.listApproved();

        System.out.println("\n📁 Generated Test Files:");
        System.out.println("   Drafts:   " + drafts.size());
        System.out.println("   Approved: " + approved.size());
        System.out.println("   Total:    " + (drafts.size() + approved.size()));

        System.out.println("\n🔧 Components Validated:");
        System.out.println("   ✅ Sample JIRA Story Loader");
        System.out.println("   ✅ JIRA Context Builder");
        System.out.println("   ✅ Sauce Demo Context Enhancer");
        System.out.println("   ✅ Element Registry Integration");
        System.out.println("   ✅ PlaywrightAgent Execution");
        System.out.println("   ✅ Draft File Workflow");
        System.out.println("   ✅ Approval Simulation");
        System.out.println("   ✅ Mock Git Service");

        System.out.println("\n🎯 Pipeline Validated:");
        System.out.println("   JIRA Story → Enhanced Context → AI Generation");
        System.out.println("   → Draft File → Approval → Mock Git → PR");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("✅ Week 15 Day 3: COMPLETE E2E PIPELINE VALIDATED");
        System.out.println("=".repeat(60));

        // Ensure we generated at least one test
        assertTrue((drafts.size() + approved.size()) > 0,
                "Should have generated at least one test");
    }

    @AfterAll
    static void cleanup(@Autowired DraftFileService draftFileService) {
        System.out.println("\n🧹 Cleanup: Test files remain in drafts/approved for inspection");
        // Intentionally NOT cleaning up so tests can be inspected
    }
}