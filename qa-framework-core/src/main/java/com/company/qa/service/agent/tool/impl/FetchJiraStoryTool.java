package com.company.qa.service.agent.tool.impl;

import com.company.qa.integration.jira.JiraStoryService;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool for fetching JIRA stories.
 *
 * Wraps JiraStoryService as an agent tool.
 *
 * Example usage by agent:
 * Input: {"jiraKey": "PROJ-123"}
 * Output: {"success": true, "summary": "...", "description": "...", ...}
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FetchJiraStoryTool implements AgentTool {

    private final JiraStoryService jiraStoryService;
    private final AgentToolRegistry toolRegistry;

    /**
     * Self-register with tool registry at startup.
     */
    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.FETCH_JIRA_STORY;
    }

    @Override
    public String getName() {
        return "JIRA Story Fetcher";
    }

    @Override
    public String getDescription() {
        return "Fetches a JIRA story by key. Returns full story details including " +
                "summary, description, acceptance criteria, status, priority, and story type. " +
                "Use this when you need to understand requirements before generating tests.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String jiraKey = (String) parameters.get("jiraKey");

        try {
            JiraStory story = jiraStoryService.getStoryByKey(jiraKey);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("jiraKey", story.getJiraKey());
            result.put("summary", story.getSummary());
            result.put("description", story.getDescription());
            result.put("acceptanceCriteria", story.getAcceptanceCriteria());
            result.put("status", story.getStatus());
            result.put("storyType", story.getStoryType());
            result.put("priority", story.getPriority());

            log.info("✅ Fetched JIRA story: {}", jiraKey);
            return result;

        } catch (Exception e) {
            log.error("❌ Failed to fetch JIRA story: {}", jiraKey, e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("jiraKey")) {
            return false;
        }

        Object jiraKey = parameters.get("jiraKey");
        return jiraKey instanceof String && !((String) jiraKey).isBlank();
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("jiraKey", "string (required) - JIRA story key (e.g., 'PROJ-123')");
        return schema;
    }
}