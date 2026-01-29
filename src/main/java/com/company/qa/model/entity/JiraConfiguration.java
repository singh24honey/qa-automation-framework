package com.company.qa.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JIRA configuration entity - represents a connection to JIRA Cloud.
 * Supports multiple JIRA instances/projects.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jira_configurations")
public class JiraConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank
    @Column(name = "config_name", unique = true, nullable = false, length = 100)
    private String configName;

    @NotBlank
    @Pattern(regexp = "^https://.*", message = "JIRA URL must start with https://")
    @Column(name = "jira_url", nullable = false, length = 500)
    private String jiraUrl;

    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9]{1,19}$",
            message = "Project key must be 2-20 uppercase letters/numbers")
    @Column(name = "project_key", nullable = false, length = 20)
    private String projectKey;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Min(1)
    @Max(1000)
    @Column(name = "max_requests_per_minute", nullable = false)
    private Integer maxRequestsPerMinute = 60;

    @NotBlank
    @Column(name = "secret_arn", nullable = false, length = 500)
    private String secretArn;

    @NotBlank
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}