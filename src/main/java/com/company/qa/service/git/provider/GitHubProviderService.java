package com.company.qa.service.git.provider;

import com.company.qa.exception.GitOperationException;
import com.company.qa.model.dto.PullRequestInfo;
import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.model.enums.PullRequestStatus;
import com.company.qa.model.enums.RepositoryType;
import com.company.qa.service.git.GitProviderService;
import com.company.qa.service.secrets.SecretsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * GitHub implementation of GitProviderService
 * Uses GitHub REST API via org.kohsuke.github library
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubProviderService implements GitProviderService {

    private final SecretsService secretsService;

    @Override
    public void createBranch(GitConfiguration config, String branchName, String sourceBranch) {
        log.info("Creating GitHub branch: {} from {}", branchName, sourceBranch);

        try {
            GitHub github = connect(config);
            GHRepository repo = getRepository(github, config);

            // Get the SHA of the source branch
            GHBranch sourceGHBranch = repo.getBranch(sourceBranch);
            String sourceSha = sourceGHBranch.getSHA1();

            // Create new branch reference
            String refName = "refs/heads/" + branchName;
            repo.createRef(refName, sourceSha);

            log.info("Successfully created GitHub branch: {}", branchName);

        } catch (IOException e) {
            log.error("Failed to create GitHub branch: {}", branchName, e);
            throw new GitOperationException("Failed to create GitHub branch: " + e.getMessage(), e);
        }
    }

    @Override
    public PullRequestInfo createPullRequest(
            GitConfiguration config,
            String sourceBranch,
            String targetBranch,
            String title,
            String description,
            List<String> reviewers,
            List<String> labels) {

        log.info("Creating GitHub PR: {} -> {}", sourceBranch, targetBranch);

        try {
            GitHub github = connect(config);
            GHRepository repo = getRepository(github, config);

            // Create pull request
            GHPullRequest pr = repo.createPullRequest(
                    title,
                    sourceBranch,
                    targetBranch,
                    description
            );

            // Add reviewers if specified
            if (reviewers != null && !reviewers.isEmpty()) {
                try {
                    pr.requestReviewers(
                            reviewers.stream()
                                    .map(username -> {
                                        try {
                                            return github.getUser(username);
                                        } catch (IOException e) {
                                            log.warn("Failed to find reviewer: {}", username, e);
                                            return null;
                                        }
                                    })
                                    .filter(java.util.Objects::nonNull)
                                    .toList()
                    );
                } catch (IOException e) {
                    log.warn("Failed to add reviewers to PR", e);
                }
            }

            // Add labels if specified
            if (labels != null && !labels.isEmpty()) {
                try {
                    pr.addLabels(labels.toArray(new String[0]));
                } catch (IOException e) {
                    log.warn("Failed to add labels to PR", e);
                }
            }

            log.info("Successfully created GitHub PR #{}: {}", pr.getNumber(), pr.getHtmlUrl());

            return PullRequestInfo.builder()
                    .number(pr.getNumber())
                    .url(pr.getHtmlUrl().toString())
                    .title(pr.getTitle())
                    .description(pr.getBody())
                    .status(mapPRStatus(pr.getState()))
                    .sourceBranch(sourceBranch)
                    .targetBranch(targetBranch)
                    .reviewers(reviewers)
                    .labels(labels)
                    .createdAt(pr.getCreatedAt().toInstant())
                    .mergedAt(pr.getMergedAt() != null ? pr.getMergedAt().toInstant() : null)
                    .build();

        } catch (IOException e) {
            log.error("Failed to create GitHub PR", e);
            throw new GitOperationException("Failed to create GitHub PR: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean branchExists(GitConfiguration config, String branchName) {
        log.debug("Checking if GitHub branch exists: {}", branchName);

        try {
            GitHub github = connect(config);
            GHRepository repo = getRepository(github, config);

            GHBranch branch = repo.getBranch(branchName);
            return branch != null;

        } catch (IOException e) {
            // Branch doesn't exist
            return false;
        }
    }

    @Override
    public void deleteBranch(GitConfiguration config, String branchName) {
        log.info("Deleting GitHub branch: {}", branchName);

        try {
            GitHub github = connect(config);
            GHRepository repo = getRepository(github, config);

            String refName = "heads/" + branchName;
            GHRef ref = repo.getRef(refName);
            ref.delete();

            log.info("Successfully deleted GitHub branch: {}", branchName);

        } catch (IOException e) {
            log.error("Failed to delete GitHub branch: {}", branchName, e);
            throw new GitOperationException("Failed to delete GitHub branch: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean validateAccess(GitConfiguration config) {
        log.info("Validating GitHub access for: {}", config.getRepositoryUrl());

        try {
            GitHub github = connect(config);
            GHRepository repo = getRepository(github, config);

            // Try to access repository metadata
            repo.getName();

            // Check if we have push access
            GHPermissionType permission = repo.getPermission(github.getMyself());
            boolean hasAccess = permission == GHPermissionType.ADMIN ||
                    permission == GHPermissionType.WRITE;

            log.info("GitHub access validation: {} (permission: {})",
                    hasAccess ? "SUCCESS" : "FAILED", permission);

            return hasAccess;

        } catch (IOException e) {
            log.error("GitHub access validation failed", e);
            return false;
        }
    }

    @Override
    public RepositoryType getSupportedType() {
        return null;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Connect to GitHub API
     */
    private GitHub connect(GitConfiguration config) throws IOException {
        String token = secretsService.getSecret(config.getAuthTokenSecretKey());

        return new GitHubBuilder()
                .withOAuthToken(token)
                .build();
    }

    /**
     * Get repository from configuration
     */
    private GHRepository getRepository(GitHub github, GitConfiguration config) throws IOException {
        String repoFullName = extractRepoFullName(config.getRepositoryUrl());

        return github.getRepository(repoFullName);
    }

    /**
     * Extract owner/repo from GitHub URL
     * Examples:
     *   https://github.com/owner/repo.git -> owner/repo
     *   https://github.com/owner/repo -> owner/repo
     */
    private String extractRepoFullName(String repoUrl) {
        String url = repoUrl.replace("https://github.com/", "")
                .replace("http://github.com/", "")
                .replace(".git", "");

        return url;
    }

    /**
     * Map GitHub PR state to our PullRequestStatus enum
     */
    private PullRequestStatus mapPRStatus(GHIssueState state) {
        return switch (state) {
            case OPEN -> PullRequestStatus.OPEN;
            case CLOSED -> PullRequestStatus.CLOSED;
            default -> PullRequestStatus.OPEN;
        };
    }
}