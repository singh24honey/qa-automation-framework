package com.company.qa.model.dto.response;

import com.company.qa.model.entity.AIGeneratedTest.TestGenerationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for test approval operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestApprovalResponse {

    private UUID testId;
    private String jiraStoryKey;
    private String testName;
    private TestGenerationStatus status;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComments;
    private String committedFolderPath;
    private boolean success;
    private String message;
}