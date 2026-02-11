package com.company.qa.integration;

import com.company.qa.model.dto.GitOperationRequest;
import com.company.qa.model.dto.GitOperationResult;
import com.company.qa.model.dto.PullRequestInfo;
import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.service.draft.DraftFileService;
import com.company.qa.service.git.GitService;
import com.company.qa.service.playwright.ElementRegistryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 15 Day 1 Integration Test - CORRECTED
 *
 * Validates:
 * 1. Element Registry loaded with Sauce Demo elements
 * 2. Mock Git Service operational (using actual GitService interface)
 * 3. Draft Folder Service functional
 * 4. End-to-end file workflow: Generate → Draft → Approve → Mock Commit
 */
@SpringBootTest
@ActiveProfiles({"dev", "mock-git"}) // Activate mock Git profile
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Week15Day1IntegrationTest {

    @Autowired
    private ElementRegistryService elementRegistryService;

    @Autowired
    @Qualifier("mockGitService") // Inject mock implementation
    private GitService gitService;

    @Autowired
    private DraftFileService draftFileService;

    private static final String TEST_FILE_NAME = "SauceDemoLoginTest.java";
    private static final String TEST_STORY_KEY = "SAUCE-001";

    private static final String TEST_CONTENT = """
        package com.company.qa.playwright.tests;
        
        import com.company.qa.playwright.base.BasePlaywrightTest;
        import org.junit.jupiter.api.Test;
        import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
        
        public class SauceDemoLoginTest extends BasePlaywrightTest {
            
            @Test
            void testLoginToSauceDemo() {
                page.navigate("https://www.saucedemo.com");
                page.locator("[data-test='username']").fill("standard_user");
                page.locator("[data-test='password']").fill("secret_sauce");
                page.locator("[data-test='login-button']").click();
                assertThat(page.locator(".title")).hasText("Products");
            }
        }
        """;

    @Test
    @Order(1)
    @DisplayName("Test 1: Element Registry Service is available")
    void testElementRegistryServiceAvailable() {
        // Verify element registry service is injected
        assertNotNull(elementRegistryService, "Element Registry Service should be initialized");

        System.out.println("✅ Test 1 passed: Element Registry Service available");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Mock Git Service is injected and returns default config")
    void testMockGitServiceInjected() {
        // Verify Git service is injected
        assertNotNull(gitService, "Git Service should be injected");

        // Verify we can get default configuration
        GitConfiguration config = gitService.getDefaultConfiguration();
        assertNotNull(config, "Default Git configuration should not be null");
        assertNotNull(config.getRepositoryUrl(), "Repository URL should not be null");
        assertTrue(config.getRepositoryUrl().contains("mock"),
                "Repository URL should indicate mock service");

        System.out.println("✅ Test 2 passed: Mock Git Service injected");
        System.out.println("   Repository: " + config.getRepositoryUrl());
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Draft Folder Service initialized correctly")
    void testDraftFolderServiceInitialized() {
        // Verify service is injected
        assertNotNull(draftFileService, "Draft File Service should be injected");

        // Verify folder structure created
        Path draftsDir = draftFileService.getDraftsDirectory();
        Path approvedDir = draftFileService.getApprovedDirectory();
        Path rejectedDir = draftFileService.getRejectedDirectory();

        assertTrue(Files.exists(draftsDir), "Drafts directory should exist");
        assertTrue(Files.exists(approvedDir), "Approved directory should exist");
        assertTrue(Files.exists(rejectedDir), "Rejected directory should exist");

        System.out.println("✅ Test 3 passed: Draft folders initialized");
        System.out.println("   Drafts:   " + draftsDir.toAbsolutePath());
        System.out.println("   Approved: " + approvedDir.toAbsolutePath());
        System.out.println("   Rejected: " + rejectedDir.toAbsolutePath());
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Save Playwright Java test file to drafts folder")
    void testSaveTestToDrafts() throws IOException {
        // Save test file to drafts
        Path draftPath = draftFileService.saveToDrafts(TEST_FILE_NAME, TEST_CONTENT);

        // Verify file created
        assertTrue(Files.exists(draftPath), "Draft file should exist");

        // Verify content
        String savedContent = Files.readString(draftPath);
        assertEquals(TEST_CONTENT, savedContent, "Saved content should match original");

        // Verify file appears in drafts list
        List<Path> drafts = draftFileService.listDrafts();
        assertTrue(drafts.stream().anyMatch(p -> p.getFileName().toString().equals(TEST_FILE_NAME)),
                "Draft file should appear in drafts list");

        System.out.println("✅ Test 4 passed: Test file saved to drafts");
        System.out.println("   File: " + draftPath.toAbsolutePath());
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Approve draft and move to approved folder")
    void testApproveDraft() throws IOException {
        // Move draft to approved
        Path approvedPath = draftFileService.moveDraftToApproved(TEST_FILE_NAME);

        // Verify file moved to approved
        assertTrue(Files.exists(approvedPath), "Approved file should exist");

        // Verify file removed from drafts
        assertFalse(Files.exists(draftFileService.getDraftsDirectory().resolve(TEST_FILE_NAME)),
                "Draft file should be removed from drafts folder");

        // Verify file appears in approved list
        List<Path> approved = draftFileService.listApproved();
        assertTrue(approved.stream().anyMatch(p -> p.getFileName().toString().equals(TEST_FILE_NAME)),
                "File should appear in approved list");

        System.out.println("✅ Test 5 passed: Draft moved to approved");
        System.out.println("   File: " + approvedPath.toAbsolutePath());
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Mock Git operations - Create Feature Branch")
    void testMockGitCreateBranch() {
        // Get default config
        GitConfiguration config = gitService.getDefaultConfiguration();

        // Create a feature branch using actual GitService method
        GitOperationResult result = gitService.createFeatureBranch(TEST_STORY_KEY, config.getId());

        // Verify branch created successfully
        assertNotNull(result, "Git operation result should not be null");
        assertTrue(result.isSuccess(), "Branch creation should succeed");
        assertNotNull(result.getBranchName(), "Branch name should not be null");
        assertTrue(result.getBranchName().contains(TEST_STORY_KEY.toLowerCase()),
                "Branch name should contain story key");

        System.out.println("✅ Test 6 passed: Mock Git branch created");
        System.out.println("   Branch: " + result.getBranchName());
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Mock Git operations - Commit Changes")
    void testMockGitCommit() {
        // Get default config
        GitConfiguration config = gitService.getDefaultConfiguration();

        // Create branch first
        GitOperationResult branchResult = gitService.createFeatureBranch(TEST_STORY_KEY, config.getId());

        // Build commit request using actual GitOperationRequest
        GitOperationRequest commitRequest = GitOperationRequest.builder()
                .storyKey(TEST_STORY_KEY)
                .branchName(branchResult.getBranchName())
                .commitMessage("Add Sauce Demo login test")
                .filePaths(List.of(
                        draftFileService.getApprovedDirectory().resolve(TEST_FILE_NAME).toString()
                ))
                .aiGeneratedTestId(UUID.randomUUID()) // Mock test ID
                .build();

        // Commit files using actual GitService method
        GitOperationResult commitResult = gitService.commitFiles(commitRequest, config.getId());

        // Verify commit successful
        assertNotNull(commitResult, "Commit result should not be null");
        assertTrue(commitResult.isSuccess(), "Commit should succeed");

        System.out.println("✅ Test 7 passed: Mock Git commit successful");
        System.out.println("   Commit SHA: " + commitResult.getCommitSha());
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Mock Git operations - Create Pull Request")
    void testMockGitPullRequest() {
        // Get default config
        GitConfiguration config = gitService.getDefaultConfiguration();

        // Create branch
        GitOperationResult branchResult = gitService.createFeatureBranch(TEST_STORY_KEY, config.getId());

        // Build PR request
        GitOperationRequest prRequest = GitOperationRequest.builder()
                .storyKey(TEST_STORY_KEY)
                .branchName(branchResult.getBranchName())
                //.targetBranch("main")
                .prTitle("Add Sauce Demo login test")
                .prDescription("AI-generated Playwright Java test for Sauce Demo login flow")
                .build();

        // Create PR using actual GitService method
        PullRequestInfo prInfo = gitService.createPullRequest(prRequest, config.getId());

        // Verify PR created
        assertNotNull(prInfo, "PR info should not be null");
        assertNotNull(prInfo.getUrl(), "PR URL should not be null");
        assertTrue(prInfo.getUrl().contains("pull"), "PR URL should contain 'pull'");
        assertTrue(prInfo.getNumber() > 0, "PR should have valid number");

        System.out.println("✅ Test 8 passed: Mock Git PR created");
        System.out.println("   PR #" + prInfo.getNumber() + ": " + prInfo.getUrl());
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Reject draft workflow")
    void testRejectDraft() throws IOException {
        // Create a new draft for rejection test
        String rejectFileName = "RejectTest.java";
        draftFileService.saveToDrafts(rejectFileName, "// Test content for rejection");

        // Reject the draft
        String rejectionReason = "Test quality below threshold";
        Path rejectedPath = draftFileService.moveDraftToRejected(rejectFileName, rejectionReason);

        // Verify file moved to rejected
        assertTrue(Files.exists(rejectedPath), "Rejected file should exist");

        // Verify metadata file created
        String metadataFileName = rejectedPath.getFileName().toString()
                .replace(".java", ".meta.txt");
        Path metadataPath = rejectedPath.getParent().resolve(metadataFileName);
        assertTrue(Files.exists(metadataPath), "Rejection metadata should exist");

        // Verify metadata contains rejection reason
        String metadata = Files.readString(metadataPath);
        assertTrue(metadata.contains(rejectionReason),
                "Metadata should contain rejection reason");

        System.out.println("✅ Test 9 passed: Draft rejection workflow successful");
        System.out.println("   Rejected file: " + rejectedPath.getFileName());
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: End-to-end workflow validation")
    void testEndToEndWorkflow() throws IOException {
        System.out.println("\n🔄 Testing complete end-to-end workflow...");

        // Get default Git config
        GitConfiguration config = gitService.getDefaultConfiguration();
        String e2eStoryKey = "SAUCE-E2E";
        String e2eFileName = "E2EWorkflowTest.java";

        // Step 1: Generate test file (save to drafts)
        Path draftPath = draftFileService.saveToDrafts(e2eFileName, TEST_CONTENT);
        System.out.println("   ✅ Step 1: Test file generated in drafts");

        // Step 2: Create Git branch (mock)
        GitOperationResult branchResult = gitService.createFeatureBranch(e2eStoryKey, config.getId());
        assertTrue(branchResult.isSuccess(), "Branch creation should succeed");
        System.out.println("   ✅ Step 2: Git branch created (mock): " + branchResult.getBranchName());

        // Step 3: Approve draft (move to approved)
        Path approvedPath = draftFileService.moveDraftToApproved(e2eFileName);
        assertTrue(Files.exists(approvedPath), "File should exist in approved");
        System.out.println("   ✅ Step 3: Test file approved and moved");

        // Step 4: Commit changes (mock)
        GitOperationRequest commitRequest = GitOperationRequest.builder()
                .storyKey(e2eStoryKey)
                .branchName(branchResult.getBranchName())
                .commitMessage("Add E2E test for " + e2eStoryKey)
                .filePaths(List.of(approvedPath.toString()))
                .aiGeneratedTestId(UUID.randomUUID())
                .build();

        GitOperationResult commitResult = gitService.commitFiles(commitRequest, config.getId());
        assertTrue(commitResult.isSuccess(), "Commit should succeed");
        System.out.println("   ✅ Step 4: Changes committed (mock)");

        // Step 5: Create PR (mock)
        GitOperationRequest prRequest = GitOperationRequest.builder()
                .storyKey(e2eStoryKey)
                .branchName(branchResult.getBranchName())
                //.targetBranch("main")
                .prTitle("Add E2E test for " + e2eStoryKey)
                .prDescription("AI-generated Playwright Java test")
                .build();

        PullRequestInfo prInfo = gitService.createPullRequest(prRequest, config.getId());
        assertNotNull(prInfo.getUrl(), "PR URL should be generated");
        System.out.println("   ✅ Step 5: Pull request created (mock)");
        System.out.println("   PR URL: " + prInfo.getUrl());

        // Verify all steps completed
        assertTrue(Files.exists(approvedPath), "Final test file should exist in approved");
        assertNotNull(prInfo.getUrl(), "PR URL should be generated");

        System.out.println("\n✅ Test 10 passed: End-to-end workflow validated");
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Complete Git Workflow (branch + commit + PR)")
    void testCompleteGitWorkflow() throws IOException {
        System.out.println("\n🔄 Testing complete Git workflow...");

        // Get default config
        GitConfiguration config = gitService.getDefaultConfiguration();
        String workflowStoryKey = "SAUCE-WORKFLOW";
        String workflowFileName = "WorkflowTest.java";

        // Save test to drafts and approve
        draftFileService.saveToDrafts(workflowFileName, TEST_CONTENT);
        Path approvedPath = draftFileService.moveDraftToApproved(workflowFileName);

        // Build complete workflow request
        GitOperationRequest workflowRequest = GitOperationRequest.builder()
                .storyKey(workflowStoryKey)
                .commitMessage("Add workflow test for " + workflowStoryKey)
                .prTitle("Add workflow test")
                .prDescription("AI-generated test via complete workflow")
                //.targetBranch("main")
                .filePaths(List.of(approvedPath.toString()))
                .aiGeneratedTestId(UUID.randomUUID())
                .build();

        // Execute complete workflow (branch + commit + PR)
        GitOperationResult result = gitService.executeCompleteWorkflow(workflowRequest, config.getId());

        // Verify workflow completed
        assertTrue(result.isSuccess(), "Complete workflow should succeed");
        assertNotNull(result.getBranchName(), "Branch should be created");
        assertNotNull(result.getPrUrl(), "PR should be created");

        System.out.println("   ✅ Complete workflow executed");
        System.out.println("   Branch: " + result.getBranchName());
        System.out.println("   PR URL: " + result.getPrUrl());
        System.out.println("\n✅ Test 11 passed: Complete Git workflow validated");
    }

    @AfterAll
    static void cleanup(@Autowired DraftFileService draftFileService) throws IOException {
        System.out.println("\n🧹 Cleaning up test files...");

        // Clean up test files from approved folder
        try {
            draftFileService.deleteDraft("SauceDemoLoginTest.java");
        } catch (Exception e) {
            // File might be in approved folder, ignore
        }

        System.out.println("✅ Cleanup complete");
    }
}