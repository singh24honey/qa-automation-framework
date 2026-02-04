package com.company.qa.model.entity;

import com.company.qa.model.enums.AuthType;
import com.company.qa.model.enums.RepositoryType;
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
 * Entity representing Git repository configuration
 * Stores connection details and credentials for automated Git operations
 */
@Entity
@Table(name = "git_configurations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitConfiguration {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "repository_url", nullable = false, length = 1000)
    private String repositoryUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "repository_type", nullable = false)
    private RepositoryType repositoryType;

    @Column(name = "default_target_branch", nullable = false)
    private String defaultTargetBranch = "main";

    @Column(name = "branch_prefix", nullable = false)
    private String branchPrefix = "AiDraft";

    // Authentication
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false)
    private AuthType authType;

    @Column(name = "auth_token_secret_key", length = 500)
    private String authTokenSecretKey; // AWS Secrets Manager key

    @Column(name = "ssh_key_path", length = 500)
    private String sshKeyPath;

    // Committer Details
    @Column(name = "committer_name", nullable = false)
    private String committerName;

    @Column(name = "committer_email", nullable = false)
    private String committerEmail;

    // PR Configuration
    @Column(name = "auto_create_pr")
    private Boolean autoCreatePr = true;

    @Column(name = "pr_reviewer_usernames")
    private List<String> prReviewerUsernames;

    @Column(name = "pr_labels")
    private List<String> prLabels;

    // Status
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_validated")
    private Boolean isValidated = false;

    @Column(name = "last_validation_at")
    private Instant lastValidationAt;

    @Column(name = "validation_error", columnDefinition = "TEXT")
    private String validationError;

    // Audit
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Get formatted branch name for a story key
     */
    public String getBranchName(String storyKey) {
        return branchPrefix + "/" + storyKey;
    }

    /**
     * Check if configuration is ready to use
     */
    public boolean isReadyToUse() {
        return isActive && isValidated && validationError == null;
    }
}