package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.dto.GitOperationRequest;
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
import java.util.List;
import java.util.Map;

/**
 * Tool for committing changes to Git.
 *
 * Uses GitService.commitFiles() which:
 * - Commits specified files to branch
 * - Tracks operation in database
 * - Returns commit SHA
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommitChangesTool implements AgentTool {

    private final GitService gitService;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.COMMIT_CHANGES;
    }

    @Override
    public String getName() {
        return "Git Commit Tool";
    }

    @Override
    public String getDescription() {
        return "Commits changes to Git with descriptive message. " +
                "Use after writing test files and creating branch.";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String storyKey = (String) parameters.get("storyKey");
        String branchName = (String) parameters.get("branchName");
        String commitMessage = (String) parameters.get("commitMessage");
        List<String> filePaths = (List<String>) parameters.get("filePaths");

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

            log.debug("Committing {} files to branch: {} for story: {}",
                    filePaths.size(), branchName, storyKey);

            // Build GitOperationRequest
            GitOperationRequest request = GitOperationRequest.builder()
                    .storyKey(storyKey)
                    .branchName(branchName)
                    .filePaths(filePaths)
                    .commitMessage(commitMessage)
                    .build();

            // Commit files
            GitOperationResult gitResult = gitService.commitFiles(request, config.getId());

            // Check result
            if (!gitResult.isSuccess()) {
                log.error("Failed to commit files for {}: {}", storyKey, gitResult.getErrorMessage());

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
            result.put("commitSha", gitResult.getCommitSha());
            result.put("branchName", gitResult.getBranchName());
            result.put("commitMessage", commitMessage);
            result.put("filesCommitted", filePaths.size());
            result.put("commitHistoryId", gitResult.getCommitHistoryId());
            result.put("operationType", gitResult.getOperationType());

            log.info("✅ Committed {} files to branch: {} (SHA: {})",
                    filePaths.size(), branchName, gitResult.getCommitSha());

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to commit changes for story: {}", storyKey, e);

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
                parameters.containsKey("commitMessage") &&
                parameters.containsKey("filePaths");
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("storyKey", "string (required) - JIRA story key (e.g., 'PROJ-123')");
        schema.put("branchName", "string (required) - Git branch name");
        schema.put("commitMessage", "string (required) - Commit message");
        schema.put("filePaths", "array (required) - List of file paths to commit");
        return schema;
    }
}