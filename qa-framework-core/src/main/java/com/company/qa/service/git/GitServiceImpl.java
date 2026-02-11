package com.company.qa.service.git;

import com.company.qa.config.GitProperties;
import com.company.qa.exception.GitOperationException;
import com.company.qa.model.dto.GitOperationRequest;
import com.company.qa.model.dto.GitOperationResult;
import com.company.qa.model.dto.PullRequestInfo;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.ApprovalRequest;
import com.company.qa.model.entity.GitCommitHistory;
import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.model.enums.GitOperationStatus;
import com.company.qa.model.enums.GitOperationType;
import com.company.qa.repository.AIGeneratedTestRepository;
import com.company.qa.repository.ApprovalRequestRepository;
import com.company.qa.repository.GitCommitHistoryRepository;
import com.company.qa.repository.GitConfigurationRepository;
import com.company.qa.service.git.provider.GitHubProviderService;
import com.company.qa.service.secrets.SecretsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of GitService using JGit
 * Handles all Git operations with comprehensive error handling and auditing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitServiceImpl implements GitService {

    private final GitConfigurationRepository gitConfigurationRepository;
    private final GitCommitHistoryRepository gitCommitHistoryRepository;
    private final AIGeneratedTestRepository aiGeneratedTestRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final GitProperties gitProperties;
    private final GitProviderServiceFactory gitProviderServiceFactory;
    private final SecretsService secretsService;


    @Override
    @Transactional
    public GitOperationResult createFeatureBranch(String storyKey, UUID configId) {
        log.info("Creating feature branch for story: {}", storyKey);

        GitConfiguration config = getConfiguration(configId);
        String branchName = config.getBranchName(storyKey);

        // Create commit history record
        GitCommitHistory history = GitCommitHistory.builder()
                .gitConfiguration(config)
                .branchName(branchName)
                .operationType(GitOperationType.BRANCH_CREATE)
                .operationStatus(GitOperationStatus.PENDING)
                .commitMessage("Create branch for " + storyKey)
                .filesCommitted(List.of())
                .createdBy("SYSTEM")
                .build();

        history = gitCommitHistoryRepository.save(history);

        try {
            // Check if branch already exists
            if (branchExists(branchName, configId)) {
                log.warn("Branch {} already exists, skipping creation", branchName);
                history.markSuccess(null);
                gitCommitHistoryRepository.save(history);

                return GitOperationResult.builder()
                        .commitHistoryId(history.getId())
                        .operationType(GitOperationType.BRANCH_CREATE)
                        .status(GitOperationStatus.SUCCESS)
                        .branchName(branchName)
                        .timestamp(Instant.now())
                        .build();
            }

            // Create branch via Git provider service
            GitProviderService provider = gitProviderServiceFactory.getProvider(config);
            provider.createBranch(config, branchName, config.getDefaultTargetBranch());

            history.markSuccess(null);
            gitCommitHistoryRepository.save(history);

            log.info("Successfully created branch: {}", branchName);

            return GitOperationResult.builder()
                    .commitHistoryId(history.getId())
                    .operationType(GitOperationType.BRANCH_CREATE)
                    .status(GitOperationStatus.SUCCESS)
                    .branchName(branchName)
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to create branch: {}", branchName, e);
            history.markFailed(e.getMessage());
            gitCommitHistoryRepository.save(history);

            return GitOperationResult.failure(
                    GitOperationType.BRANCH_CREATE,
                    "Failed to create branch: " + e.getMessage()
            );
        }
    }

    @Override
    @Transactional
    public GitOperationResult commitFiles(GitOperationRequest request, UUID configId) {
        log.info("Committing files to branch: {}", request.getBranchName());

        GitConfiguration config = getConfiguration(configId);
        AIGeneratedTest aiTest = getAIGeneratedTest(request.getAiGeneratedTestId());

        // Create commit history record
        GitCommitHistory history = GitCommitHistory.builder()
                .aiGeneratedTest(aiTest)
                .gitConfiguration(config)
                .branchName(request.getBranchName())
                .operationType(GitOperationType.COMMIT)
                .operationStatus(GitOperationStatus.PENDING)
                .commitMessage(request.getCommitMessage())
                .filesCommitted(request.getFilePaths())
                .totalFilesCount(request.getFilePaths().size())
                .createdBy("SYSTEM")
                .build();

        if (request.getApprovalRequestId() != null) {
            approvalRequestRepository.findById(request.getApprovalRequestId())
                    .ifPresent(history::setApprovalRequest);
        }

        history = gitCommitHistoryRepository.save(history);

        Git git = null;
        File localRepo = null;

        try {
            // Clone repository to local workspace
            localRepo = cloneRepository(config);
            git = Git.open(localRepo);

            // Checkout the branch
            git.checkout()
                    .setName(request.getBranchName())
                    .call();

            // Copy files from draft folder to repository
            int linesAdded = 0;

            for (String filePath : request.getFilePaths()) {
                File sourceFile = new File(filePath);
                if (!sourceFile.exists()) {
                    throw new GitOperationException("Source file not found: " + filePath);
                }

                // Determine target path in repository
                String relativePath = getRelativePath(sourceFile, request.getStoryKey());
                File targetFile = new File(localRepo, relativePath);

                // Create parent directories
                targetFile.getParentFile().mkdirs();

                // Copy file
                Files.copy(sourceFile.toPath(), targetFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // Add to Git
                git.add().addFilepattern(relativePath).call();

                // Count lines
                linesAdded += Files.readAllLines(sourceFile.toPath()).size();
            }

            // Commit changes
            PersonIdent committer = new PersonIdent(
                    config.getCommitterName(),
                    config.getCommitterEmail()
            );

            git.commit()
                    .setMessage(request.getCommitMessage())
                    .setAuthor(committer)
                    .setCommitter(committer)
                    .call();

            // Push to remote
            CredentialsProvider credentials = getCredentialsProvider(config);
            git.push()
                    .setCredentialsProvider(credentials)
                    .call();

            // Get commit SHA
            String commitSha = git.getRepository().resolve("HEAD").getName();

            // Update history
            history.setTotalLinesAdded(linesAdded);
            history.markSuccess(commitSha);
            gitCommitHistoryRepository.save(history);

            // Update AIGeneratedTest

            aiTest.setGitBranch(request.getBranchName());
            aiTest.setGitCommitSha(commitSha);  // ✅ commitSha is already a String
            aiTest.setGitCommittedAt(LocalDateTime.from(Instant.now()));
            aiTest.setStatus(AIGeneratedTest.TestGenerationStatus.COMMITTED);  // ✅ Use enum
            aiGeneratedTestRepository.save(aiTest);

            log.info("Successfully committed {} files to branch: {}",
                    request.getFilePaths().size(), request.getBranchName());

            return GitOperationResult.builder()
                    .commitHistoryId(history.getId())
                    .operationType(GitOperationType.COMMIT)
                    .status(GitOperationStatus.SUCCESS)
                    .branchName(request.getBranchName())
                    .commitSha(commitSha)
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to commit files to branch: {}", request.getBranchName(), e);
            history.markFailed(e.getMessage());
            gitCommitHistoryRepository.save(history);

            return GitOperationResult.failure(
                    GitOperationType.COMMIT,
                    "Failed to commit files: " + e.getMessage()
            );
        } finally {
            // Cleanup
            if (git != null) {
                git.close();
            }
            if (localRepo != null && gitProperties.isCleanupAfterOperation()) {
                cleanupLocalRepository(localRepo);
            }
        }
    }

    @Override
    @Transactional
    public PullRequestInfo createPullRequest(GitOperationRequest request, UUID configId) {
        log.info("Creating pull request for branch: {}", request.getBranchName());

        GitConfiguration config = getConfiguration(configId);
        GitProviderService provider = gitProviderServiceFactory.getProvider(config);

        try {
            // Create PR via provider service
            PullRequestInfo prInfo = provider.createPullRequest(
                    config,
                    request.getBranchName(),
                    config.getDefaultTargetBranch(),
                    request.getPrTitle(),
                    request.getPrDescription(),
                    request.getPrReviewers() != null ? request.getPrReviewers() : config.getPrReviewerUsernames(),
                    request.getPrLabels() != null ? request.getPrLabels() : config.getPrLabels()
            );

            // Update commit history with PR details
            gitCommitHistoryRepository.findByBranchNameOrderByCreatedAtDesc(request.getBranchName())
                    .stream()
                    .findFirst()
                    .ifPresent(history -> {
                        history.setPrNumber(prInfo.getNumber());
                        history.setPrUrl(prInfo.getUrl());
                        history.setPrStatus(prInfo.getStatus());
                        history.setPrCreatedAt(prInfo.getCreatedAt());
                        gitCommitHistoryRepository.save(history);
                    });

            // Update AIGeneratedTest
            if (request.getAiGeneratedTestId() != null) {
                AIGeneratedTest aiTest = getAIGeneratedTest(request.getAiGeneratedTestId());
                aiTest.setGitPrUrl(prInfo.getUrl());
                aiGeneratedTestRepository.save(aiTest);
            }

            log.info("Successfully created PR #{}: {}", prInfo.getNumber(), prInfo.getUrl());
            return prInfo;

        } catch (Exception e) {
            log.error("Failed to create pull request for branch: {}", request.getBranchName(), e);
            throw new GitOperationException("Failed to create pull request: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public GitOperationResult executeCompleteWorkflow(GitOperationRequest request, UUID configId) {
        log.info("Executing complete Git workflow for story: {}", request.getStoryKey());

        try {
            GitConfiguration config = getConfiguration(configId);

            // Step 1: Create branch
            String branchName = config.getBranchName(request.getStoryKey());
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

            // Step 3: Create PR (if configured)
            if (config.getAutoCreatePr()) {
                PullRequestInfo prInfo = createPullRequest(request, configId);
                commitResult.setPrNumber(prInfo.getNumber());
                commitResult.setPrUrl(prInfo.getUrl());
            }

            log.info("Successfully completed Git workflow for story: {}", request.getStoryKey());
            return commitResult;

        } catch (Exception e) {
            log.error("Failed to execute complete Git workflow", e);
            return GitOperationResult.failure(
                    GitOperationType.COMMIT,
                    "Workflow failed: " + e.getMessage()
            );
        }
    }

    @Override
    public boolean validateConfiguration(UUID configId) {
        try {
            GitConfiguration config = getConfiguration(configId);
            GitProviderService provider = gitProviderServiceFactory.getProvider(config);

            boolean isValid = provider.validateAccess(config);

            // Update configuration validation status
            config.setIsValidated(isValid);
            config.setLastValidationAt(Instant.now());
            config.setValidationError(isValid ? null : "Validation failed");
            gitConfigurationRepository.save(config);

            return isValid;

        } catch (Exception e) {
            log.error("Failed to validate configuration", e);

            GitConfiguration config = getConfiguration(configId);
            config.setIsValidated(false);
            config.setLastValidationAt(Instant.now());
            config.setValidationError(e.getMessage());
            gitConfigurationRepository.save(config);

            return false;
        }
    }

    @Override
    public GitConfiguration getDefaultConfiguration() {
        return gitConfigurationRepository.findByIsActiveFalse()
                .stream()
                .findFirst()
                .orElseThrow(() -> new GitOperationException("No active Git configuration found"));
    }

    @Override
    public boolean branchExists(String branchName, UUID configId) {
        GitConfiguration config = getConfiguration(configId);
        GitProviderService provider = gitProviderServiceFactory.getProvider(config);
        return provider.branchExists(config, branchName);
    }

    @Override
    public boolean deleteBranch(String branchName, UUID configId) {
        try {
            GitConfiguration config = getConfiguration(configId);
            GitProviderService provider = gitProviderServiceFactory.getProvider(config);
            provider.deleteBranch(config, branchName);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete branch: {}", branchName, e);
            return false;
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get Git configuration by ID or return default
     */
    private GitConfiguration getConfiguration(UUID configId) {
        if (configId == null) {
            return getDefaultConfiguration();
        }
        return gitConfigurationRepository.findById(configId)
                .orElseGet(this::getDefaultConfiguration);
    }

    /**
     * Get AI Generated Test by ID
     */
    private AIGeneratedTest getAIGeneratedTest(UUID id) {
        return aiGeneratedTestRepository.findById(id)
                .orElseThrow(() -> new GitOperationException("AI Generated Test not found: " + id));
    }

    /**
     * Clone repository to local workspace
     */
    private File cloneRepository(GitConfiguration config) throws GitAPIException, IOException {
        if (!gitProperties.isEnabled()) {
            throw new GitOperationException("Git operations are disabled");
        }

        // Create workspace directory
        Path workspacePath = Paths.get(gitProperties.getWorkingDirectory());
        Files.createDirectories(workspacePath);

        // Create unique directory for this clone
        String repoName = extractRepoName(config.getRepositoryUrl());
        File localRepo = new File(workspacePath.toFile(),
                repoName + "-" + UUID.randomUUID().toString().substring(0, 8));

        log.info("Cloning repository to: {}", localRepo.getAbsolutePath());

        CredentialsProvider credentials = getCredentialsProvider(config);

        Git.cloneRepository()
                .setURI(config.getRepositoryUrl())
                .setDirectory(localRepo)
                .setCredentialsProvider(credentials)
                .setTimeout(gitProperties.getOperationTimeout())
                .call();

        return localRepo;
    }

    /**
     * Get credentials provider for Git operations
     */
    private CredentialsProvider getCredentialsProvider(GitConfiguration config) {
        String token = secretsService.getSecret(config.getAuthTokenSecretKey());
        // GitHub expects token as username, empty password
        return new UsernamePasswordCredentialsProvider(token, "");
    }

    /**
     * Extract repository name from URL
     */
    private String extractRepoName(String repoUrl) {
        String url = repoUrl.endsWith(".git") ? repoUrl.substring(0, repoUrl.length() - 4) : repoUrl;
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Get relative path for file in repository
     * Maps draft files to proper repository structure
     */
    private String getRelativePath(File sourceFile, String storyKey) {
        String fileName = sourceFile.getName();

        if (fileName.endsWith(".feature")) {
            // Cucumber features: src/test/resources/features/ai-generated/{story-key}/
            return "src/test/resources/features/ai-generated/" + storyKey + "/" + fileName;
        } else if (fileName.endsWith("Steps.java")) {
            // Step definitions: src/test/java/com/company/qa/steps/ai/
            return "src/test/java/com/company/qa/steps/ai/" + fileName;
        } else if (fileName.endsWith("Page.java")) {
            // Page objects: src/test/java/com/company/qa/pages/ai/
            return "src/test/java/com/company/qa/pages/ai/" + fileName;
        } else {
            // Default location for other files
            return "src/test/resources/ai-generated/" + storyKey + "/" + fileName;
        }
    }

    /**
     * Cleanup local repository directory
     */
    private void cleanupLocalRepository(File localRepo) {
        try {
            FileUtils.deleteDirectory(localRepo);
            log.debug("Cleaned up local repository: {}", localRepo.getAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to cleanup local repository: {}", localRepo.getAbsolutePath(), e);
        }
    }
}