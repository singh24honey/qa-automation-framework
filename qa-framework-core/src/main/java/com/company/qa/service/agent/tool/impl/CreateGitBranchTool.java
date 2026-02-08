package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.dto.GitOperationResult;
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
import java.util.Map;

/**
 * Tool for creating Git feature branches.
 *
 * Uses GitService.createFeatureBranch() which:
 * - Creates branch with naming convention: feature/{storyKey}
 * - Uses configured Git repository
 * - Tracks operation in database
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CreateGitBranchTool implements AgentTool {

    private final GitService gitService;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.CREATE_BRANCH;
    }

    @Override
    public String getName() {
        return "Git Branch Creator";
    }

    @Override
    public String getDescription() {
        return "Creates a new Git feature branch for the story. " +
                "Branch name format: feature/{storyKey}. " +
                "Use this before committing changes.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String storyKey = (String) parameters.get("storyKey");

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

            log.debug("Creating feature branch for story: {} using config: {}",
                    storyKey, config.getRepositoryUrl());

            // Create feature branch
            GitOperationResult gitResult = gitService.createFeatureBranch(storyKey, config.getId());

            // Check result
            if (!gitResult.isSuccess()) {
                log.error("Failed to create branch for {}: {}", storyKey, gitResult.getErrorMessage());

                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", gitResult.getErrorMessage());
                result.put("operationType", gitResult.getOperationType());
                result.put("status", gitResult.getStatus());
                return result;
            }

            // Build success result
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("branchName", gitResult.getBranchName());
            result.put("storyKey", storyKey);
            result.put("operationType", gitResult.getOperationType());
            result.put("commitHistoryId", gitResult.getCommitHistoryId());
            result.put("message", "Branch created successfully");

            log.info("✅ Created Git branch: {} for story: {}", gitResult.getBranchName(), storyKey);

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to create Git branch for story: {}", storyKey, e);

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
                parameters.get("storyKey") instanceof String;
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("storyKey", "string (required) - JIRA story key (e.g., 'PROJ-123')");
        return schema;
    }
}