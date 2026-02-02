package com.company.qa.model.dto.response;


import com.company.qa.model.entity.AIGeneratedTest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for test generation results.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestGenerationResponse {

    private UUID testId;
    private String jiraStoryKey;
    private String testName;
    private AIGeneratedTest.TestType testType;
    private AIGeneratedTest.TestGenerationStatus status;

    // Generated content
    private Map<String, Object> testCode;

    // Quality metrics
    private BigDecimal qualityScore;
    private AIGeneratedTest.ConfidenceLevel confidenceLevel;
    private List<AIGeneratedTest.QualityConcern> qualityConcerns;

    // AI metadata
    private String aiProvider;
    private String aiModel;
    private Integer promptTokens;
    private Integer completionTokens;
    private BigDecimal totalCostUsd;

    // File paths
    private String draftFolderPath;

    // Timestamps
    private LocalDateTime generatedAt;

    // Success indicators
    private boolean success;
    private String message;

    public static TestGenerationResponse fromEntity(AIGeneratedTest test) {
        return TestGenerationResponse.builder()
                .testId(test.getId())
                .jiraStoryKey(test.getJiraStoryKey())
                .testName(test.getTestName())
                .testType(test.getTestType())
                .status(test.getStatus())
                .testCode(test.getTestCodeJson())
                .qualityScore(test.getQualityScore())
                .confidenceLevel(test.getConfidenceLevel())
                .qualityConcerns(test.getQualityConcerns())
                .aiProvider(test.getAiProvider())
                .aiModel(test.getAiModel())
                .promptTokens(test.getPromptTokens())
                .completionTokens(test.getCompletionTokens())
                .totalCostUsd(test.getTotalCostUsd())
                .draftFolderPath(test.getDraftFolderPath())
                .generatedAt(test.getGeneratedAt())
                .success(true)
                .message("Test generation successful")
                .build();
    }
}

