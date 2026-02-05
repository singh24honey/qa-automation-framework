package com.company.qa.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log for all JIRA API calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jira_api_audit_log",
        indexes = {
                @Index(name = "idx_jira_audit_config_created",
                        columnList = "config_id, created_at"),
                @Index(name = "idx_jira_audit_request_id",
                        columnList = "request_id")
        })
public class JiraApiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private JiraConfiguration config;

    @NotBlank
    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @NotBlank
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "rate_limited", nullable = false)
    private Boolean rateLimited = false;

    @Column(name = "retried", nullable = false)
    private Boolean retried = false;

    @NotBlank
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}