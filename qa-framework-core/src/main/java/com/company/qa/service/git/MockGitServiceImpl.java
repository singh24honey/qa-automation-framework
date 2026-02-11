package com.company.qa.service.git;

import com.company.qa.model.dto.GitOperationRequest;
import com.company.qa.model.dto.GitOperationResult;
import com.company.qa.model.dto.PullRequestInfo;
import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.model.enums.AuthType;
import com.company.qa.model.enums.GitOperationStatus;
import com.company.qa.model.enums.GitOperationType;
import com.company.qa.model.enums.RepositoryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Mock Git Service Implementation for Week 15 Development/Testing
 *
 * Implements the existing GitService interface but performs mock operations
 * instead of real Git interactions. This allows full E2E testing of the
 * agent workflow without external Git dependencies.
 *
 * Activation: spring.profiles.active=dev,mock-git
 *
 * Features:
 * - Simulates all Git operations with realistic delays
 * - Returns proper DTOs matching real GitService
 * - Logs all operations for debugging
 * - Thread-safe for concurrent agent executions
 */
@Slf4j
@Service("mockGitService")
@Profile({"dev", "mock-git"})
public class MockGitServiceImpl implements GitService {

    private static final String MOCK_REPO_URL = "https://github.com/mock/uat-test-runner";
    private static final String MOCK_BRANCH_PREFIX = "feature/";

    GitConfiguration mockConfig = new GitConfiguration();

    @Override
    public GitOperationResult createFeatureBranch(String storyKey, UUID configId) {
        log.info("🔀 MOCK GIT: Creating feature branch for story: {}", storyKey);
        log.debug("   Config ID: {}", configId);

        // Simulate Git operation delay
        simulateGitDelay(200);

        // Build branch name matching real GitService convention
        String branchName = MOCK_BRANCH_PREFIX + storyKey.toLowerCase();

        log.info("✅ MOCK GIT: Branch created successfully");
        log.info("   Branch: {}", branchName);

        // Return result matching real GitService
        return GitOperationResult.builder()
                .commitHistoryId(UUID.randomUUID()) // Mock commit history ID
                .operationType(GitOperationType.BRANCH_CREATE)
                .status(GitOperationStatus.SUCCESS)
                .branchName(branchName)
                .timestamp(Instant.now())
                .build();
    }

    @Override
    public GitOperationResult commitFiles(GitOperationRequest request, UUID configId) {
        log.info("💾 MOCK GIT: Committing files to branch: {}", request.getBranchName());
        log.debug("   Files: {}", request.getFilePaths().size());
        log.debug("   Commit message: {}", request.getCommitMessage());

        for (String filePath : request.getFilePaths()) {
            log.debug("     - {}", filePath);
        }

        // Simulate commit operation delay
        simulateGitDelay(300);

        // Generate mock commit SHA
        String mockCommitSha = "mock-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("✅ MOCK GIT: Commit successful");
        log.info("   Commit SHA: {}", mockCommitSha);
        log.info("   Files committed: {}", request.getFilePaths().size());

        // Return result matching real GitService
        return GitOperationResult.builder()
                .commitHistoryId(UUID.randomUUID())
                .operationType(GitOperationType.COMMIT)
                .status(GitOperationStatus.SUCCESS)
                .branchName(request.getBranchName())
                .commitSha(mockCommitSha)
              //  .filesCommitted(request.getFilePaths())
                //.linesAdded(estimateLinesAdded(request.getFilePaths().size()))
                .timestamp(Instant.now())
                .build();
    }

    @Override
    public PullRequestInfo createPullRequest(GitOperationRequest request, UUID configId) {
        log.info("📤 MOCK GIT: Creating pull request");
        log.debug("   Source: {} → Target: {}", request.getBranchName(), mockConfig.getDefaultTargetBranch());
        log.debug("   Title: {}", request.getPrTitle());

        // Simulate PR creation delay
        simulateGitDelay(500);

        // Generate mock PR details
        int mockPrNumber = (int)(Math.random() * 1000) + 1;
        String mockPrUrl = MOCK_REPO_URL + "/pull/" + mockPrNumber;

        log.info("✅ MOCK GIT: Pull request created successfully");
        log.info("   PR #: {}", mockPrNumber);
        log.info("   PR URL: {}", mockPrUrl);

        // Return PR info matching real GitService
        return PullRequestInfo.builder()
                .number(mockPrNumber)
                .url(mockPrUrl)
                .title(request.getPrTitle())
                .description(request.getPrDescription())
                .sourceBranch(request.getBranchName())
                .targetBranch(mockConfig.getDefaultTargetBranch())
                //.state("OPEN")
                .createdAt(Instant.now())
                .build();
    }

    @Override
    public GitOperationResult executeCompleteWorkflow(GitOperationRequest request, UUID configId) {
        log.info("🔄 MOCK GIT: Executing complete workflow for story: {}", request.getStoryKey());

        try {
            // Step 1: Create branch
            String branchName = MOCK_BRANCH_PREFIX + request.getStoryKey().toLowerCase();
            request.setBranchName(branchName);

            GitOperationResult branchResult = createFeatureBranch(request.getStoryKey(), configId);
            if (!branchResult.isSuccess()) {
                return branchResult;
            }

            // Step 2: Commit files
            GitOperationResult commitResult = commitFiles(request, configId);
            if (!commitResult.isSuccess()) {
                return commitResult;
            }

            // Step 3: Create PR
            PullRequestInfo prInfo = createPullRequest(request, configId);
            commitResult.setPrNumber(prInfo.getNumber());
            commitResult.setPrUrl(prInfo.getUrl());

            log.info("✅ MOCK GIT: Complete workflow executed successfully");
            return commitResult;

        } catch (Exception e) {
            log.error("❌ MOCK GIT: Workflow failed", e);
            return GitOperationResult.failure(
                    GitOperationType.COMMIT,
                    "Mock workflow failed: " + e.getMessage()
            );
        }
    }

    @Override
    public boolean validateConfiguration(UUID configId) {
        log.debug("🔍 MOCK GIT: Validating configuration: {}", configId);

        // Mock service is always "valid"
        simulateGitDelay(100);

        log.debug("   Validation: SUCCESS (mock always valid)");
        return true;
    }

    @Override
    public GitConfiguration getDefaultConfiguration() {
        log.debug("🔧 MOCK GIT: Getting default configuration");

        // Return mock configuration using @Builder
        GitConfiguration mockConfig = GitConfiguration.builder()
                .id(UUID.randomUUID())
                .name("Mock Git Config")
                .repositoryUrl(MOCK_REPO_URL)
                .repositoryType(RepositoryType.GITHUB)
                .defaultTargetBranch("main")
                .branchPrefix("feature")
                .authType(AuthType.TOKEN)
                .committerName("QA Framework Bot")
                .committerEmail("qa-bot@company.com")
                .autoCreatePr(true)
                .isActive(true)
                .isValidated(true)
                .createdBy("SYSTEM")
                .updatedBy("SYSTEM")
                .build();

        log.debug("   Mock config: {}", mockConfig.getName());
        return mockConfig;
    }

    @Override
    public boolean branchExists(String branchName, UUID configId) {
        log.debug("🔍 MOCK GIT: Checking if branch exists: {}", branchName);

        // For mock, assume branches starting with 'feature/' exist
        boolean exists = branchName.startsWith(MOCK_BRANCH_PREFIX);

        log.debug("   Branch exists: {}", exists);
        return exists;
    }

    @Override
    public boolean deleteBranch(String branchName, UUID configId) {
        log.info("🗑️ MOCK GIT: Deleting branch: {}", branchName);

        simulateGitDelay(150);

        log.info("✅ MOCK GIT: Branch deleted successfully");
        return true;
    }

    // Helper methods

    /**
     * Simulates Git operation delay for realistic logging and testing
     */
    private void simulateGitDelay(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Mock Git operation interrupted", e);
        }
    }

    /**
     * Estimates lines added based on number of files (for mock purposes)
     */
    private int estimateLinesAdded(int fileCount) {
        // Assume ~50 lines per test file on average
        return fileCount * 50;
    }
}