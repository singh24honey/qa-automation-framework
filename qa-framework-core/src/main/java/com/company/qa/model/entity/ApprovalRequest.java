package com.company.qa.model.entity;

import com.company.qa.model.enums.ApprovalRequestType;
import com.company.qa.model.enums.ApprovalStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for Human-in-the-Loop approval requests.
 *
 * Stores AI-generated content that requires human review before execution.
 */
@Entity
@Table(name = "approval_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 50)
    private ApprovalRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ApprovalStatus status;

    @Column(name = "generated_content", nullable = false, columnDefinition = "TEXT")
    private String generatedContent;

    @Type(JsonBinaryType.class)
    @Column(name = "ai_response_metadata", columnDefinition = "TEXT")
    private Map<String, Object> aiResponseMetadata;

    // Test details
    @Column(name = "test_name")
    private String testName;

    @Column(name = "test_framework", length = 50)
    private String testFramework;

    @Column(name = "test_language", length = 50)
    private String testLanguage;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    // Requester details
    @Column(name = "requested_by_id", nullable = false)
    private UUID requestedById;

    @Column(name = "requested_by_name", length = 100)
    private String requestedByName;

    @Column(name = "requested_by_email")
    private String requestedByEmail;

    // Reviewer details
    @Column(name = "reviewed_by_id")
    private UUID reviewedById;

    @Column(name = "reviewed_by_name", length = 100)
    private String reviewedByName;

    @Column(name = "reviewed_by_email")
    private String reviewedByEmail;

    // Decision details
    @Column(name = "approval_decision_notes", columnDefinition = "TEXT")
    private String approvalDecisionNotes;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // Execution
    @Column(name = "auto_execute_on_approval")
    private Boolean autoExecuteOnApproval;

    @Column(name = "executed_test_id")
    private UUID executedTestId;

    @Column(name = "execution_id")
    private UUID executionId;

    // Audit
    @Version
    private Long version;

    // Sanitization
    @Column(name = "sanitization_applied")
    private Boolean sanitizationApplied;

    @Column(name = "redaction_count")
    private Integer redactionCount;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plus(java.time.Duration.ofDays(7));
        }
        if (status == null) {
            status = ApprovalStatus.PENDING_APPROVAL;
        }
        if (autoExecuteOnApproval == null) {
            autoExecuteOnApproval = false;
        }
        if (sanitizationApplied == null) {
            sanitizationApplied = false;
        }
        if (redactionCount == null) {
            redactionCount = 0;
        }

        // ✅ ADD: Git field defaults
        if (autoCommitOnApproval == null) {
            autoCommitOnApproval = true;  // Default: auto-commit enabled
        }
        if (gitOperationTriggered == null) {
            gitOperationTriggered = false;
        }
    }
    /**
     * Check if request is pending approval.
     */
    public boolean isPending() {
        return status == ApprovalStatus.PENDING_APPROVAL;
    }

    /**
     * Check if request is approved.
     */
    public boolean isApproved() {
        return status == ApprovalStatus.APPROVED;
    }

    /**
     * Check if request is rejected.
     */
    public boolean isRejected() {
        return status == ApprovalStatus.REJECTED;
    }

    /**
     * Check if request is expired.
     */
    public boolean isExpired() {
        return status == ApprovalStatus.EXPIRED ||
                (expiresAt != null && Instant.now().isAfter(expiresAt));
    }

    /**
     * Whether to trigger Git commit automatically upon approval
     * If false, Git commit must be triggered manually
     */
    @Column(name = "auto_commit_on_approval")
    private Boolean autoCommitOnApproval;

    /**
     * Whether Git operation was triggered
     */
    @Column(name = "git_operation_triggered")
    private Boolean gitOperationTriggered;

    /**
     * Whether Git operation completed successfully
     */
    @Column(name = "git_operation_success")
    private Boolean gitOperationSuccess;

    /**
     * Git operation error message (if failed)
     */
    @Column(name = "git_error_message", length = 2000)
    private String gitErrorMessage;

    /**
     * Reference to the AI generated test
     */
    @Column(name = "ai_generated_test_id")
    private UUID aiGeneratedTestId;

    /**
     * Git branch name (populated after successful commit)
     */
    @Column(name = "git_branch")
    private String gitBranch;

    /**
     * Git commit SHA (populated after successful commit)
     */
    @Column(name = "git_commit_sha")
    private String gitCommitSha;

    /**
     * Pull request URL (populated after PR creation)
     */
    @Column(name = "git_pr_url", length = 1000)
    private String gitPrUrl;

    /**
     * Timestamp when Git commit was made
     */
    @Column(name = "git_committed_at")
    private Instant gitCommittedAt;
}