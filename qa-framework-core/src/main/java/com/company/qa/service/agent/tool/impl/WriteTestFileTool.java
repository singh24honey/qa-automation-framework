package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
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

    private static final String AI_DRAFT_DIR = "src/test/java/AiDraft/";

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
        return "Writes test code to file in AiDraft directory. " +
                "File will be committed to git after approval. " +
                "Use this after generating test code.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String testCode = (String) parameters.get("testCode");
        String testClassName = (String) parameters.get("testClassName");

        try {
            // Ensure AiDraft directory exists
            Path draftDir = Paths.get(AI_DRAFT_DIR);
            if (!Files.exists(draftDir)) {
                Files.createDirectories(draftDir);
            }

            // Write file
            Path testFilePath = draftDir.resolve(testClassName + ".java");
            Files.writeString(testFilePath, testCode);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("filePath", testFilePath.toString());
            result.put("absolutePath", testFilePath.toAbsolutePath().toString());
            result.put("fileSize", testCode.length());

            log.info("✅ Wrote test file: {}", testFilePath);

            return result;

        } catch (IOException e) {
            log.error("❌ Failed to write test file: {}", testClassName, e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return parameters != null &&
                parameters.containsKey("testCode") &&
                parameters.containsKey("testClassName") &&
                parameters.get("testCode") instanceof String &&
                parameters.get("testClassName") instanceof String;
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("testCode", "string (required) - Complete test code");
        schema.put("testClassName", "string (required) - Test class name (without .java extension)");
        return schema;
    }
}