package com.company.qa.model.dto;

import com.company.qa.model.enums.GitOperationStatus;
import com.company.qa.model.enums.GitOperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Result DTO for Git operations
 * Contains operation status, metadata, and error information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitOperationResult {

    private UUID commitHistoryId;
    private GitOperationType operationType;
    private GitOperationStatus status;
    private String branchName;
    private String commitSha;
    private String errorMessage;
    private Instant timestamp;

    // PR details
    private Integer prNumber;
    private String prUrl;

    /**
     * Check if operation was successful
     */
    public boolean isSuccess() {
        return status == GitOperationStatus.SUCCESS;
    }

    /**
     * Check if operation failed
     */
    public boolean isFailed() {
        return status == GitOperationStatus.FAILED;
    }

    /**
     * Check if operation is pending
     */
    public boolean isPending() {
        return status == GitOperationStatus.PENDING;
    }

    /**
     * Factory method for successful operation
     */
    public static GitOperationResult success(
            GitOperationType type,
            String branchName,
            String commitSha) {
        return GitOperationResult.builder()
                .operationType(type)
                .status(GitOperationStatus.SUCCESS)
                .branchName(branchName)
                .commitSha(commitSha)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Factory method for failed operation
     */
    public static GitOperationResult failure(
            GitOperationType type,
            String errorMessage) {
        return GitOperationResult.builder()
                .operationType(type)
                .status(GitOperationStatus.FAILED)
                .errorMessage(errorMessage)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Factory method for pending operation
     */
    public static GitOperationResult pending(
            GitOperationType type,
            String branchName) {
        return GitOperationResult.builder()
                .operationType(type)
                .status(GitOperationStatus.PENDING)
                .branchName(branchName)
                .timestamp(Instant.now())
                .build();
    }
}