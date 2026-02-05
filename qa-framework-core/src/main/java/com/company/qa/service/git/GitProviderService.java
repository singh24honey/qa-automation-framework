package com.company.qa.service.git;

import com.company.qa.model.dto.PullRequestInfo;
import com.company.qa.model.entity.GitConfiguration;

import java.util.List;

/**
 * Interface for Git provider-specific operations
 * Abstracts GitHub, GitLab, and Bitbucket API differences
 */
public interface GitProviderService {

    /**
     * Create a branch in the remote repository
     *
     * @param config Git configuration
     * @param branchName Name of the branch to create
     * @param sourceBranch Source branch to create from (e.g., "main")
     */
    void createBranch(GitConfiguration config, String branchName, String sourceBranch);

    /**
     * Create a pull request
     *
     * @param config Git configuration
     * @param sourceBranch Source branch (the one with changes)
     * @param targetBranch Target branch (where to merge)
     * @param title PR title
     * @param description PR description/body
     * @param reviewers List of reviewer usernames
     * @param labels List of labels to apply
     * @return Pull request information
     */
    PullRequestInfo createPullRequest(
            GitConfiguration config,
            String sourceBranch,
            String targetBranch,
            String title,
            String description,
            List<String> reviewers,
            List<String> labels
    );

    /**
     * Check if a branch exists in the remote repository
     *
     * @param config Git configuration
     * @param branchName Branch name to check
     * @return true if branch exists
     */
    boolean branchExists(GitConfiguration config, String branchName);

    /**
     * Delete a branch from the remote repository
     *
     * @param config Git configuration
     * @param branchName Branch name to delete
     */
    void deleteBranch(GitConfiguration config, String branchName);

    /**
     * Validate repository access with current credentials
     *
     * @param config Git configuration
     * @return true if access is valid
     */
    boolean validateAccess(GitConfiguration config);

    /**
     * Get the repository type this provider supports
     *
     * @return Repository type (GITHUB, GITLAB, etc.)
     */
    com.company.qa.model.enums.RepositoryType getSupportedType();
}