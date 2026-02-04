package com.company.qa.model.dto;

import com.company.qa.model.enums.GitOperationStatus;
import com.company.qa.model.enums.GitOperationType;
import com.company.qa.model.enums.PullRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for Git commit history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitCommitHistoryDTO {

    private UUID id;
    private UUID aiGeneratedTestId;
    private UUID gitConfigurationId;
    private UUID approvalRequestId;

    private String branchName;
    private String commitSha;
    private String commitMessage;
    private List<String> filesCommitted;

    private Integer prNumber;
    private String prUrl;
    private PullRequestStatus prStatus;

    private GitOperationStatus operationStatus;
    private GitOperationType operationType;
    private String errorMessage;
    private Integer retryCount;

    private Integer totalLinesAdded;
    private Integer totalLinesDeleted;
    private Integer totalFilesCount;

    private Instant createdAt;
    private Instant updatedAt;
}