package com.company.qa.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores uploaded OpenAPI/Swagger specifications
 */
@Entity
@Table(name = "api_specifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSpecification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identification
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // OpenAPI metadata
    @Column(name = "openapi_version", nullable = false, length = 20)
    private String openapiVersion;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    // Content
    @Column(name = "spec_content", nullable = false, columnDefinition = "TEXT")
    private String specContent;

    @Column(name = "spec_format", nullable = false, length = 10)
    private String specFormat; // JSON or YAML

    // Metadata
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "is_active")
    private Boolean isActive;

    // Stats
    @Column(name = "endpoint_count")
    private Integer endpointCount;

    @Column(name = "schema_count")
    private Integer schemaCount;

    // Timestamps
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Relationships
    @OneToMany(mappedBy = "specification", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApiEndpoint> endpoints = new ArrayList<>();

    @OneToMany(mappedBy = "specification", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApiSchema> schemas = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (endpointCount == null) {
            endpointCount = 0;
        }
        if (schemaCount == null) {
            schemaCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Helper method to update counts
     */
    public void updateCounts() {
        this.endpointCount = this.endpoints != null ? this.endpoints.size() : 0;
        this.schemaCount = this.schemas != null ? this.schemas.size() : 0;
    }
}