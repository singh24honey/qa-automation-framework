package com.company.qa.service.agent;

import com.company.qa.model.agent.*;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.enums.AgentType;
import com.company.qa.service.agent.AgentMemoryService;
import com.company.qa.service.agent.AgentOrchestrator;
import com.company.qa.service.agent.BaseAgent;
import com.company.qa.service.ai.AIBudgetService;
import com.company.qa.service.ai.AIGatewayService;
import com.company.qa.service.approval.ApprovalRequestService;
import com.company.qa.service.audit.AuditLogService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Playwright Test Generator Agent.
 *
 * Goal: Generate Playwright test from JIRA story
 *
 * Strategy:
 * 1. Fetch JIRA story
 * 2. Generate test code using AI
 * 3. Write test file to AiDraft folder
 * 4. Create approval request
 * 5. Wait for approval
 * 6. Create Git branch
 * 7. Commit changes
 * 8. Create pull request
 *
 * Success Criteria:
 * - Test code generated
 * - File written successfully
 * - Pull request created
 */
@Slf4j
@Component
public class PlaywrightTestGeneratorAgent extends BaseAgent {

    private final AgentOrchestrator orchestrator;

    public PlaywrightTestGeneratorAgent(
            AIGatewayService aiGateway,
            AuditLogService auditService,
            ApprovalRequestService approvalService,
            AIBudgetService budgetService,
            AgentMemoryService memoryService,
            AgentConfig config,
            AgentOrchestrator orchestrator) {

        super(aiGateway, auditService, approvalService, budgetService, memoryService, config);
        this.orchestrator = orchestrator;
    }

    /**
     * Register this agent with orchestrator at startup.
     */
    @PostConstruct
    public void register() {
        orchestrator.registerAgent(AgentType.PLAYWRIGHT_TEST_GENERATOR, this);
        log.info("🤖 Registered PlaywrightTestGeneratorAgent");
    }

    @Override
    protected AgentType getAgentType() {
        return AgentType.PLAYWRIGHT_TEST_GENERATOR;
    }

    /**
     * Plan next action based on current context.
     *
     * This is the agent's intelligence - decides what to do next.
     */
    @Override
    protected AgentPlan plan(AgentContext context) {
        // Check what we've done so far
        boolean hasFetchedJira = hasCompletedAction(context, AgentActionType.FETCH_JIRA_STORY);
        boolean hasGeneratedCode = hasCompletedAction(context, AgentActionType.GENERATE_TEST_CODE);
        boolean hasWrittenFile = hasCompletedAction(context, AgentActionType.WRITE_FILE);
        boolean hasCreatedApproval = hasCompletedAction(context, AgentActionType.REQUEST_APPROVAL);
        boolean hasCreatedBranch = hasCompletedAction(context, AgentActionType.CREATE_BRANCH);
        boolean hasCommitted = hasCompletedAction(context, AgentActionType.COMMIT_CHANGES);
        boolean hasCreatedPR = hasCompletedAction(context, AgentActionType.CREATE_PULL_REQUEST);

        // Decision tree for next action
        if (!hasFetchedJira) {
            return planFetchJiraStory(context);
        } else if (!hasGeneratedCode) {
            return planGenerateTestCode(context);
        } else if (!hasWrittenFile) {
            return planWriteFile(context);
        } else if (!hasCreatedApproval) {
            return planCreateApproval(context);
        } else if (!hasCreatedBranch) {
            return planCreateBranch(context);
        } else if (!hasCommitted) {
            return planCommitChanges(context);
        } else if (!hasCreatedPR) {
            return planCreatePullRequest(context);
        }

        // Should not reach here if goal check works correctly
        return AgentPlan.builder()
                .nextAction(AgentActionType.COMPLETE)
                .confidence(1.0)
                .reasoning("All steps completed")
                .requiresApproval(false)
                .build();
    }

    /**
     * Execute a specific action using tools.
     */
    @Override
    protected ActionResult executeAction(
            AgentActionType actionType,
            Map<String, Object> parameters,
            AgentContext context) {

        try {
            // Execute using tool registry
            Map<String, Object> result = executeTool(actionType, parameters);

            boolean success = (boolean) result.getOrDefault("success", false);

            return ActionResult.builder()
                    .success(success)
                    .output(result)
                    .errorMessage((String) result.get("error"))
                    .aiCost((Double) result.get("aiCost"))
                    .build();

        } catch (Exception e) {
            log.error("Action execution failed: {}", actionType, e);

            return ActionResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Check if goal achieved.
     *
     * Success = Pull request created
     */
    @Override
    protected boolean isGoalAchieved(AgentContext context) {
        return hasCompletedAction(context, AgentActionType.CREATE_PULL_REQUEST);
    }

    // ========== PLANNING METHODS ==========

    private AgentPlan planFetchJiraStory(AgentContext context) {
        String jiraKey = (String) context.getGoal().getParameters().get("jiraKey");

        Map<String, Object> params = new HashMap<>();
        params.put("jiraKey", jiraKey);

        return AgentPlan.builder()
                .nextAction(AgentActionType.FETCH_JIRA_STORY)
                .actionParameters(params)
                .confidence(1.0)
                .reasoning("Need JIRA story to generate test")
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planGenerateTestCode(AgentContext context) {
        String jiraKey = (String) context.getGoal().getParameters().get("jiraKey");
        String framework = (String) context.getGoal().getParameters()
                .getOrDefault("framework", "PLAYWRIGHT");

        Map<String, Object> params = new HashMap<>();
        params.put("jiraKey", jiraKey);
        params.put("framework", framework);

        return AgentPlan.builder()
                .nextAction(AgentActionType.GENERATE_TEST_CODE)
                .actionParameters(params)
                .confidence(0.85)
                .reasoning("Generate test code using AI based on JIRA story")
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planWriteFile(AgentContext context) {
        // Get test code from previous action
        String testCode = context.getWorkProduct("testCode", String.class);
        String testClassName = context.getWorkProduct("testClassName", String.class);

        Map<String, Object> params = new HashMap<>();
        params.put("testCode", testCode);
        params.put("testClassName", testClassName);

        return AgentPlan.builder()
                .nextAction(AgentActionType.WRITE_FILE)
                .actionParameters(params)
                .confidence(1.0)
                .reasoning("Write generated test to file")
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateApproval(AgentContext context) {
        String testCode = context.getWorkProduct("testCode", String.class);
        String jiraKey = (String) context.getGoal().getParameters().get("jiraKey");

        Map<String, Object> params = new HashMap<>();
        params.put("testCode", testCode);
        params.put("jiraKey", jiraKey);
        params.put("requestedBy", "agent");

        return AgentPlan.builder()
                .nextAction(AgentActionType.REQUEST_APPROVAL)
                .actionParameters(params)
                .confidence(1.0)
                .reasoning("Request approval before committing to Git")
                .requiresApproval(false)  // Creating approval, not requiring it
                .build();
    }

    private AgentPlan planCreateBranch(AgentContext context) {
        String jiraKey = (String) context.getGoal().getParameters().get("jiraKey");
        String branchName = "feature/agent-" + jiraKey.toLowerCase() + "-test";

        Map<String, Object> params = new HashMap<>();
        params.put("branchName", branchName);

        return AgentPlan.builder()
                .nextAction(AgentActionType.CREATE_BRANCH)
                .actionParameters(params)
                .confidence(1.0)
                .reasoning("Create Git branch for changes")
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCommitChanges(AgentContext context) {
        String jiraKey = (String) context.getGoal().getParameters().get("jiraKey");
        String filePath = context.getWorkProduct("filePath", String.class);

        Map<String, Object> params = new HashMap<>();
        params.put("commitMessage", "feat: Add AI-generated test for " + jiraKey);
        params.put("filePaths", List.of(filePath));

        return AgentPlan.builder()
                .nextAction(AgentActionType.COMMIT_CHANGES)
                .actionParameters(params)
                .confidence(1.0)
                .reasoning("Commit test file to Git")
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreatePullRequest(AgentContext context) {
        String jiraKey = (String) context.getGoal().getParameters().get("jiraKey");
        String branchName = "feature/agent-" + jiraKey.toLowerCase() + "-test";

        Map<String, Object> params = new HashMap<>();
        params.put("branchName", branchName);
        params.put("title", "[AI-Generated] Test for " + jiraKey);
        params.put("description", "Automated test generated by AI agent for JIRA story " + jiraKey);

        return AgentPlan.builder()
                .nextAction(AgentActionType.CREATE_PULL_REQUEST)
                .actionParameters(params)
                .confidence(1.0)
                .reasoning("Create pull request for review")
                .requiresApproval(false)
                .build();
    }

    // ========== HELPER METHODS ==========

    /**
     * Check if action has been completed successfully.
     */
    private boolean hasCompletedAction(AgentContext context, AgentActionType actionType) {
        return context.getActionHistory().stream()
                .anyMatch(entry -> entry.getActionType() == actionType && entry.isSuccess());
    }
}