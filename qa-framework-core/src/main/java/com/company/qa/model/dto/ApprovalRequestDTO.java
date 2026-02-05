package com.company.qa.model.dto;

import com.company.qa.model.enums.ApprovalRequestType;
import com.company.qa.model.enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for approval request response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequestDTO {

    private UUID id;
    private ApprovalRequestType requestType;
    private ApprovalStatus status;

    // Generated content
    private String generatedContent;
    private Map<String, Object> aiResponseMetadata;

    // Test details
    private String testName;
    private String testFramework;
    private String testLanguage;
    private String targetUrl;

    // Requester
    private UUID requestedById;
    private String requestedByName;
    private String requestedByEmail;

    // Reviewer
    private UUID reviewedById;
    private String reviewedByName;
    private String reviewedByEmail;

    // Decision
    private String approvalDecisionNotes;
    private String rejectionReason;

    // Timestamps
    private Instant createdAt;
    private Instant expiresAt;
    private Instant reviewedAt;

    // Execution
    private Boolean autoExecuteOnApproval;
    private UUID executedTestId;
    private UUID executionId;

    // Sanitization
    private Boolean sanitizationApplied;
    private Integer redactionCount;

    // Computed fields
    private Boolean isPending;
    private Boolean isExpired;
    private Long daysUntilExpiration;

    // Git integration fields
    private Boolean autoCommitOnApproval;

    /**
     * Whether Git operation was triggered
     */
    private Boolean gitOperationTriggered;

    /**
     * Whether Git operation completed successfully
     */
    private Boolean gitOperationSuccess;

    /**
     * Git operation error message (if failed)
     */
    private String gitErrorMessage;

    /**
     * Reference to the AI generated test
     */
    private UUID aiGeneratedTestId;

    /**
     * Git branch name
     */
    private String gitBranch;

    /**
     * Git commit SHA
     */
    private String gitCommitSha;

    /**
     * Pull request URL
     */
    private String gitPrUrl;

    /**
     * Timestamp when Git commit was made
     */
    private Instant gitCommittedAt;

    // ====
}