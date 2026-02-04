package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for Git operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitOperationRequest {

    private UUID aiGeneratedTestId;
    private UUID approvalRequestId;
    private String storyKey;
    private String branchName;
    private List<String> filePaths;
    private String commitMessage;

    // PR details
    private String prTitle;
    private String prDescription;
    private List<String> prReviewers;
    private List<String> prLabels;
}