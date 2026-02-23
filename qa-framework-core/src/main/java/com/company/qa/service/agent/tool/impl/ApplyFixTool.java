package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.repository.TestRepository;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent tool to apply fixes to test content.
 *
 * Modifies Test.content field in-place with backup capability.
 *
 * Input parameters:
 * - testId: UUID of test to modify
 * - fixedTestCode: New test code from GenerateFixTool
 *
 * Output:
 * - success: true/false
 * - testId: UUID of modified test
 * - previousContent: Backup of old content (for rollback)
 * - newContent: New content applied
 * - error: Error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyFixTool implements AgentTool {

    private final TestRepository testRepository;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.MODIFY_FILE;
    }

    @Override
    public String getName() {
        return "Test Fix Applicator";
    }

    @Override
    public String getDescription() {
        return "Applies a fix to a test by modifying its DB content only. " +
                "Returns backup of previous content for rollback if fix doesn't work. " +
                "Use after generating fix with GenerateFixTool. " +
                "File sync happens in promoteToTestsTable() after human approval.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("💾 Applying fix to test: {}", parameters.get("testId"));

        try {
            String testIdStr    = (String) parameters.get("testId");
            String fixedTestCode = (String) parameters.get("fixedTestCode");

            UUID testId = UUID.fromString(testIdStr);

            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new RuntimeException("Test not found: " + testId));

            String previousContent = test.getContent();
            log.info("Backing up original content for test: {}", test.getName());
            log.debug("Previous content length: {} chars", previousContent.length());

            if (fixedTestCode == null || fixedTestCode.isBlank()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", "fixedTestCode is null or blank");
                return result;
            }

            test.setContent(fixedTestCode);
            test = testRepository.save(test);

            log.info("✅ Fix applied to test: {} (ID: {})", test.getName(), testId);
            log.debug("New content length: {} chars", fixedTestCode.length());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("testId", testId.toString());
            result.put("testName", test.getName());
            result.put("previousContent", previousContent);
            result.put("newContent", fixedTestCode);
            result.put("contentLength", fixedTestCode.length());
            return result;

        } catch (Exception e) {
            log.error("❌ Failed to apply fix: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        if (parameters == null) return false;
        if (!parameters.containsKey("testId") || !parameters.containsKey("fixedTestCode")) return false;
        try {
            UUID.fromString((String) parameters.get("testId"));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("testId",        "string (required) - UUID of test to modify");
        schema.put("fixedTestCode", "string (required) - New test code content (INTENT_V1 JSON)");
        return schema;
    }
}