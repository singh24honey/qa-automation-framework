package com.company.qa.service.git;



import com.company.qa.model.dto.GitOperationRequest;
import com.company.qa.model.dto.GitOperationResult;
import com.company.qa.model.dto.PullRequestInfo;
import com.company.qa.model.entity.GitConfiguration;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * Service for Git operations
 * Handles branch creation, file commits, and pull request management
 */
public interface GitService {

    /**
     * Create a feature branch for a story
     *
     * @param storyKey JIRA story key (e.g., "SCRUM-7")
     * @param configId Git configuration to use
     * @return Operation result with branch name
     */
    GitOperationResult createFeatureBranch(String storyKey, UUID configId);

    /**
     * Commit files to a branch
     *
     * @param request Git operation request with files and commit details
     * @param configId Git configuration to use
     * @return Operation result with commit SHA
     */
    GitOperationResult commitFiles(GitOperationRequest request, UUID configId);

    /**
     * Create a pull request
     *
     * @param request Git operation request with PR details
     * @param configId Git configuration to use
     * @return Pull request information
     */
    PullRequestInfo createPullRequest(GitOperationRequest request, UUID configId);

    /**
     * Execute complete Git workflow (branch + commit + PR)
     * This is the main entry point for the approval workflow
     *
     * @param request Git operation request
     * @param configId Git configuration to use
     * @return Operation result
     */
    GitOperationResult executeCompleteWorkflow(GitOperationRequest request, UUID configId);

    /**
     * Validate Git configuration by testing connectivity
     *
     * @param configId Git configuration to validate
     * @return true if validation successful
     */
    boolean validateConfiguration(UUID configId);

    /**
     * Get the default Git configuration
     *
     * @return Default Git configuration
     */
    GitConfiguration getDefaultConfiguration();

    /**
     * Check if a branch exists in the remote repository
     *
     * @param branchName Branch name to check
     * @param configId Git configuration to use
     * @return true if branch exists
     */
    boolean branchExists(String branchName, UUID configId);

    /**
     * Delete a branch (cleanup operation)
     *
     * @param branchName Branch name to delete
     * @param configId Git configuration to use
     * @return true if deletion successful
     */
    boolean deleteBranch(String branchName, UUID configId);
}