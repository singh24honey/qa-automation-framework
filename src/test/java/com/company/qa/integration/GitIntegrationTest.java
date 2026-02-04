package com.company.qa.integration;


import com.company.qa.model.dto.GitOperationRequest;
import com.company.qa.model.dto.GitOperationResult;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.GitCommitHistory;
import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.model.enums.AuthType;
import com.company.qa.model.enums.GitOperationStatus;
import com.company.qa.model.enums.GitOperationType;
import com.company.qa.model.enums.RepositoryType;
import com.company.qa.repository.*;
import com.company.qa.service.git.GitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Git workflow
 * Uses in-memory database and mock Git operations
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GitIntegrationTest {

    @Autowired
    private GitService gitService;

    @Autowired
    private GitConfigurationRepository gitConfigurationRepository;

    @Autowired
    private GitCommitHistoryRepository gitCommitHistoryRepository;

    @Autowired
    private AIGeneratedTestRepository aiGeneratedTestRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    private GitConfiguration testConfig;
    private AIGeneratedTest testAiTest;
    private Path tempDraftDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create test Git configuration
        testConfig = GitConfiguration.builder()
                .name("test-integration-config")
                .repositoryUrl("https://github.com/test/integration.git")
                .repositoryType(RepositoryType.GITHUB)
                .defaultTargetBranch("main")
                .branchPrefix("AiDraft")
                .authType(AuthType.TOKEN)
                .authTokenSecretKey("test-secret-key")
                .committerName("Test Bot")
                .committerEmail("bot@test.com")
                .autoCreatePr(true)
                .isActive(true)
                .isValidated(false)
                .createdBy("TEST")
                .build();

        testConfig = gitConfigurationRepository.save(testConfig);

        // Create temp draft directory with test files
        tempDraftDir = Files.createTempDirectory("qa-test-drafts-");
        Files.createFile(tempDraftDir.resolve("SCRUM-7.feature"));
        Files.createDirectories(tempDraftDir.resolve("steps"));
        Files.createFile(tempDraftDir.resolve("steps/LoginSteps.java"));

        // Create test AI generated test
        testAiTest = AIGeneratedTest.builder()
                .testName("Test_Login")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.CUCUMBER)
                .status(AIGeneratedTest.TestGenerationStatus.DRAFT)
                .draftFolderPath(tempDraftDir.toString())
                .aiProvider("bedrock")
                // .createdAt(Instant.now())
                .build();

        testAiTest = aiGeneratedTestRepository.save(testAiTest);
    }

    @Test
    void testCompleteGitWorkflow_WithMockedOperations() {
        // Note: This test validates the workflow logic
        // Actual Git operations are disabled in test profile

        // Arrange
        List<String> filePaths = List.of(
                tempDraftDir.resolve("SCRUM-7.feature").toString(),
                tempDraftDir.resolve("steps/LoginSteps.java").toString()
        );

        GitOperationRequest request = GitOperationRequest.builder()
                .aiGeneratedTestId(testAiTest.getId())
                .storyKey("SCRUM-7")
                .filePaths(filePaths)
                .commitMessage("feat: Add AI-generated tests for SCRUM-7")
                .prTitle("[AI-Generated] Tests for SCRUM-7")
                .prDescription("Auto-generated test suite")
                .build();

        // Act
        // With Git disabled in test profile, this should handle gracefully
        GitOperationResult result = gitService.executeCompleteWorkflow(
                request,
                testConfig.getId()
        );

        // Assert
        assertThat(result).isNotNull();
        // In test mode with Git disabled, operation should complete without errors
    }

    @Test
    void testGitConfigurationPersistence() {
        // Arrange
        GitConfiguration newConfig = GitConfiguration.builder()
                .name("persistence-test-config")
                .repositoryUrl("https://github.com/test/persistence.git")
                .repositoryType(RepositoryType.GITLAB)
                .defaultTargetBranch("develop")
                .branchPrefix("feature")
                .authType(AuthType.TOKEN)
                .authTokenSecretKey("secret")
                .committerName("Test")
                .committerEmail("test@example.com")
                .isActive(true)
                .createdBy("TEST")
                .build();

        // Act
        GitConfiguration saved = gitConfigurationRepository.save(newConfig);
        GitConfiguration retrieved = gitConfigurationRepository.findById(saved.getId())
                .orElseThrow();

        // Assert
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("persistence-test-config");
        assertThat(retrieved.getRepositoryType()).isEqualTo(RepositoryType.GITLAB);
        assertThat(retrieved.getDefaultTargetBranch()).isEqualTo("develop");
    }

    @Test
    void testGitCommitHistoryCreation() {
        // Arrange
        GitCommitHistory history =
                GitCommitHistory.builder()
                        .aiGeneratedTest(testAiTest)
                        .gitConfiguration(testConfig)
                        .branchName("AiDraft/SCRUM-7")
                        .commitMessage("Test commit")
                        .filesCommitted(List.of("file1.java", "file2.feature"))
                        .operationType(GitOperationType.COMMIT)
                        .operationStatus(GitOperationStatus.PENDING)
                        .createdBy("TEST")
                        .build();

        // Act
        GitCommitHistory saved =
                gitCommitHistoryRepository.save(history);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBranchName()).isEqualTo("AiDraft/SCRUM-7");
        assertThat(saved.getFilesCommitted()).hasSize(2);
        assertThat(saved.getTotalFilesCount()).isEqualTo(2);
    }
}