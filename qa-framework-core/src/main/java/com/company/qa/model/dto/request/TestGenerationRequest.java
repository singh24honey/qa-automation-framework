package com.company.qa.model.dto.request;

import com.company.qa.model.entity.AIGeneratedTest.TestType;
import com.company.qa.model.entity.AIGeneratedTest.TestFramework;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request DTO for generating tests from JIRA stories.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestGenerationRequest {

    @NotBlank(message = "JIRA story key is required")
    private String jiraStoryKey;

    @NotNull(message = "Test type is required")
    private TestType testType; // UI, API, E2E

    @NotNull(message = "Test framework is required")
    @Builder.Default
    private TestFramework testFramework = TestFramework.CUCUMBER;

    // Optional: Override default AI provider
    private String aiProvider; // BEDROCK, OLLAMA, MOCK

    // Optional: Override default AI model
    private String aiModel; // amazon.nova-pro-v1:0, codellama, etc.

    // Optional: Additional context (e.g., existing page objects to reuse)
    @Builder.Default
    private Map<String, Object> additionalContext = Map.of();

    // Optional: Skip quality assessment (for testing)
    @Builder.Default
    private boolean skipQualityCheck = false;

    private String UserPrompt; // BEDROCK, OLLAMA, MOCK

}