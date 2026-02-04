package com.company.qa.model.entity;

import com.company.qa.model.enums.GitOperationStatus;
import com.company.qa.model.enums.GitOperationType;
import com.company.qa.model.enums.PullRequestStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing Git commit history
 * Tracks all Git operations performed by the framework
 */
@Entity
@Table(name = "git_commit_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitCommitHistory {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Links
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_generated_test_id", nullable = false)
    private AIGeneratedTest aiGeneratedTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "git_configuration_id", nullable = false)
    private GitConfiguration gitConfiguration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id")
    private ApprovalRequest approvalRequest;

    // Git Details
    @Column(name = "branch_name", nullable = false)
    private String branchName;

    @Column(name = "commit_sha")
    private String commitSha;

    @Column(name = "commit_message", nullable = false, columnDefinition = "TEXT")
    private String commitMessage;

    @Column(name = "files_committed", nullable = false)
    private List<String> filesCommitted;

    // PR Details
    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(name = "pr_url", length = 1000)
    private String prUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "pr_status")
    private PullRequestStatus prStatus;

    @Column(name = "pr_created_at")
    private Instant prCreatedAt;

    @Column(name = "pr_merged_at")
    private Instant prMergedAt;

    // Operation Status
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_status", nullable = false)
    private GitOperationStatus operationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private GitOperationType operationType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    // Metadata
    @Column(name = "total_lines_added")
    private Integer totalLinesAdded = 0;

    @Column(name = "total_lines_deleted")
    private Integer totalLinesDeleted = 0;

    @Column(name = "total_files_count")
    private Integer totalFilesCount = 0;

    // Audit
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (totalFilesCount == null && filesCommitted != null) {
            totalFilesCount = filesCommitted.size();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Mark operation as successful
     */
    /**
     * Mark operation as successful
     */
    public void markSuccess(String commitSha) {
        this.operationStatus = GitOperationStatus.SUCCESS;
        this.commitSha = commitSha;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark operation as failed
     */
    public void markFailed(String errorMessage) {
        this.operationStatus = GitOperationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount = (this.retryCount != null ? this.retryCount : 0) + 1;
        this.updatedAt = Instant.now();
    }
}