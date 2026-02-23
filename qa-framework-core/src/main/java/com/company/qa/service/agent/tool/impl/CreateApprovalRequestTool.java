package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.dto.ApprovalRequestDTO;
import com.company.qa.model.dto.CreateApprovalRequestDTO;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.enums.ApprovalRequestType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.approval.ApprovalRequestService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tool for creating approval requests.
 *
 * Agents use this when they need human approval to proceed.
 * Creates approval request with test code for stakeholder review.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CreateApprovalRequestTool implements AgentTool {

    private final ApprovalRequestService approvalService;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.REQUEST_APPROVAL;
    }

    @Override
    public String getName() {
        return "Approval Request Creator";
    }

    @Override
    public String getDescription() {
        return "Creates an approval request for human review. " +
                "Use when action requires stakeholder approval. " +
                "Returns approval request ID for tracking.";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String testCode = (String) parameters.get("testCode");
        String jiraKey = (String) parameters.get("jiraKey");
        String testName = (String) parameters.get("testName");
        //String requestedBy = (String) parameters.getOrDefault("requestedBy", "agent-system");
        String testFramework = (String) parameters.getOrDefault("testFramework", "PLAYWRIGHT");
        String requestedByName = (String) parameters.getOrDefault("requestedBy", "agent-system");

        String requestTypeStr    = (String) parameters.get("requestType");

        // Optional: AI metadata if available
        Map<String, Object> aiMetadata = (Map<String, Object>) parameters.get("aiMetadata");
        String aiGeneratedTestIdStr = (String) parameters.get("aiGeneratedTestId");
        log.info("🔍 DEBUG CreateApprovalRequestTool: aiGeneratedTestId = {}", aiGeneratedTestIdStr); // TEMP


        // Resolve requestType — default to TEST_GENERATION for backward compatibility
        ApprovalRequestType requestType = ApprovalRequestType.TEST_GENERATION;
        if (requestTypeStr != null && !requestTypeStr.isBlank()) {
            try {
                requestType = ApprovalRequestType.valueOf(requestTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown requestType '{}' — defaulting to TEST_GENERATION", requestTypeStr);
            }
        }

        try {
            // Build CreateApprovalRequestDTO
            CreateApprovalRequestDTO createDto = CreateApprovalRequestDTO.builder()
                    .requestType(requestType)
                    .generatedContent(testCode)
                    .aiResponseMetadata(aiMetadata)
                    .testName(testName)
                    .testFramework(testFramework)
                    .testLanguage("Java")
                    .autoCommitOnApproval(false)    // ✅ ADD THIS — agent handles Git itself
                    .requestedByName(requestedByName)
                    .requestedByEmail(requestedByName + "@agent.system")  // Agent email
                    .autoExecuteOnApproval(false)  // Manual execution
                    .expirationDays(7)  // Default 7 days
                    .sanitizationApplied(true)  // Assume sanitization was done
                    .redactionCount(0)
                    .requestedById(UUID.randomUUID())
                    .aiGeneratedTestId(                                              // ✅ ADD THIS
                            aiGeneratedTestIdStr != null
                                    ? UUID.fromString(aiGeneratedTestIdStr)
                                    : null)
                    .build();

            log.debug("Creating approval request for test: {} (story: {})", testName, jiraKey);

            // Create approval request
            ApprovalRequestDTO approvalDto = approvalService.createApprovalRequest(createDto);

            // Build success result
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("approvalRequestId", approvalDto.getId());
            result.put("status", approvalDto.getStatus());
            result.put("requestType", approvalDto.getRequestType());
            result.put("testName", approvalDto.getTestName());
            result.put("createdAt", approvalDto.getCreatedAt());
            result.put("expiresAt", approvalDto.getExpiresAt());
            result.put("isPending", approvalDto.getIsPending());

            log.info("✅ Created approval request: {} for test: {}",
                    approvalDto.getId(), testName);

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to create approval request for test: {}", testName, e);

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
                parameters.containsKey("testCode") &&
                parameters.containsKey("jiraKey") &&
                parameters.containsKey("testName");
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("testCode", "string (required) - Generated test code for review");
        schema.put("jiraKey", "string (required) - Associated JIRA story key");
        schema.put("testName", "string (required) - Name of the test");
        schema.put("testFramework", "string (optional) - Test framework (default: PLAYWRIGHT)");
        schema.put("requestedBy", "string (optional) - User requesting approval (default: agent-system)");
        schema.put("aiMetadata", "map (optional) - AI response metadata (cost, tokens, etc.)");
        schema.put("aiGeneratedTestId", "string (optional) - UUID of AIGeneratedTest for test table promotion"); // ✅ NEW

        return schema;
    }
}