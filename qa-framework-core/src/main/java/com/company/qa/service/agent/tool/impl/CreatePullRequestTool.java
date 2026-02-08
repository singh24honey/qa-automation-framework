package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.dto.GitOperationRequest;
import com.company.qa.model.dto.PullRequestInfo;
import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.git.GitService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool for creating GitHub pull requests.
 *
 * Uses GitService.createPullRequest() which:
 * - Creates PR against base branch (configured in GitConfiguration)
 * - Sets title and description
 * - Returns PR number and URL
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CreatePullRequestTool implements AgentTool {

    private final GitService gitService;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.CREATE_PULL_REQUEST;
    }

    @Override
    public String getName() {
        return "GitHub PR Creator";
    }

    @Override
    public String getDescription() {
        return "Creates a GitHub pull request for review. " +
                "Use after committing changes.";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String storyKey = (String) parameters.get("storyKey");
        String branchName = (String) parameters.get("branchName");
        String title = (String) parameters.get("title");
        String description = (String) parameters.get("description");
        List<String> reviewers = (List<String>) parameters.get("reviewers");
        List<String> labels = (List<String>) parameters.get("labels");

        try {
            // Get default Git configuration
            GitConfiguration config = gitService.getDefaultConfiguration();

            if (config == null) {
                log.error("No default Git configuration found");
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", "No default Git configuration found. Please configure Git settings.");
                return result;
            }

            log.debug("Creating pull request for branch: {} (story: {})", branchName, storyKey);

            // Build GitOperationRequest
            GitOperationRequest request = GitOperationRequest.builder()
                    .storyKey(storyKey)
                    .branchName(branchName)
                    .prTitle(title)
                    .prDescription(description)
                    .prReviewers(reviewers)
                    .prLabels(labels)
                    .build();

            // Create pull request
            PullRequestInfo prInfo = gitService.createPullRequest(request, config.getId());

            // Check if PR was created (PullRequestInfo should not be null)
            if (prInfo == null) {
                log.error("Failed to create pull request for branch: {}", branchName);

                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", "Pull request creation returned null");
                return result;
            }

            // Build success result
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("pullRequestUrl", prInfo.getUrl());
            result.put("pullRequestNumber", prInfo.getNumber());
            result.put("title", title);
            result.put("branchName", branchName);
            result.put("storyKey", storyKey);

            log.info("✅ Created pull request #{}: {} ({})",
                    prInfo.getNumber(), title, prInfo.getUrl());

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to create pull request for branch: {}", branchName, e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return parameters != null &&
                parameters.containsKey("storyKey") &&
                parameters.containsKey("branchName") &&
                parameters.containsKey("title") &&
                parameters.containsKey("description");
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("storyKey", "string (required) - JIRA story key (e.g., 'PROJ-123')");
        schema.put("branchName", "string (required) - Source branch name");
        schema.put("title", "string (required) - PR title");
        schema.put("description", "string (required) - PR description");
        schema.put("reviewers", "array (optional) - List of reviewer usernames");
        schema.put("labels", "array (optional) - List of PR labels");
        return schema;
    }
}