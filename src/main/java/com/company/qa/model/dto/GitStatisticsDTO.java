package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Git operation statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitStatisticsDTO {

    private Long totalOperations;
    private Long successfulOperations;
    private Long failedOperations;
    private Long pendingOperations;
    private Double successRate;

    private Long totalBranchesCreated;
    private Long totalCommits;
    private Long totalPullRequests;

    private Integer totalFilesCommitted;
    private Long totalLinesAdded;
    private Long totalLinesDeleted;
}