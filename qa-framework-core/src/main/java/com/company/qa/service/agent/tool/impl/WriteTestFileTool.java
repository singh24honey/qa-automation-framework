package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.draft.DraftFileService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool for writing test files to disk.
 *
 * Creates test file in AiDraft directory for review.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WriteTestFileTool implements AgentTool {

    private final AgentToolRegistry toolRegistry;
    private final DraftFileService draftFileService;


    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.WRITE_FILE;
    }

    @Override
    public String getName() {
        return "Test File Writer";
    }

    @Override
    public String getDescription() {
        return "Writes  test code to UAT submodule drafts folder. " +
                "File will be in DRAFT status awaiting QA Manager approval. " +
                "Use this after generating Playwright test code.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String testCode = (String) parameters.get("testCode");
        String testClassName = (String) parameters.get("testClassName");
        String fileName = (String) parameters.get("fileName");

        if (fileName == null && parameters.containsKey("testClassName")) {
             testClassName = (String) parameters.get("testClassName");
            fileName = testClassName + ".java";
            log.debug("Generated fileName from testClassName: {}", fileName);
        }
        try {
            // Check if UAT submodule is accessible
            if (!draftFileService.isSubmoduleAccessible()) {
                log.error("UAT submodule is not accessible");
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", "UAT submodule is not accessible. Check configuration.");
                return result;
            }

            // Save to drafts folder
            Path draftPath = draftFileService.saveToDrafts(fileName, testCode);

            // Build success result
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("filePath", draftPath.toString());
            result.put("absolutePath", draftPath.toAbsolutePath().toString());
            result.put("fileName", fileName);
            result.put("fileSize", testCode.length());
            result.put("status", "DRAFT");
            result.put("location", "playwright-tests/drafts/");

            log.info("✅ Wrote Playwright test to drafts: {}", fileName);
            log.debug("   Path: {}", draftPath.toAbsolutePath());

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to write Playwright test file: {}", fileName, e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        boolean hasTestCode = parameters != null &&
                parameters.containsKey("testCode") &&
                parameters.get("testCode") instanceof String;

        // fileName is preferred, but testClassName is accepted for backward compatibility
        boolean hasFileName = parameters.containsKey("fileName") &&
                parameters.get("fileName") instanceof String;
        boolean hasTestClassName = parameters.containsKey("testClassName") &&
                parameters.get("testClassName") instanceof String;

        return hasTestCode && (hasFileName || hasTestClassName);
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("testCode", "string (required) - Complete test code");
        schema.put("fileName", "string (required) - Test file name with .java extension (e.g., 'JIRA-123_login.java')");
        schema.put("testClassName", "string (required) - Test class name (without .java extension)");
        return schema;
    }
}