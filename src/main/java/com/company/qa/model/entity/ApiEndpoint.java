package com.company.qa.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;

import java.time.Instant;

/**
 * API endpoints extracted from OpenAPI specifications
 */
@Entity
@Table(name = "api_endpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to parent spec
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_id", nullable = false)
    private ApiSpecification specification;

    // Endpoint definition
    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "method", nullable = false, length = 10)
    private String method; // GET, POST, PUT, DELETE, PATCH

    @Column(name = "operation_id")
    private String operationId;

    // Documentation
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tags", columnDefinition = "text[]")
    @Type(StringArrayType.class)
    private String[] tags;

    // Request/Response schemas
    @Column(name = "request_schema", columnDefinition = "TEXT")
    private String requestSchema;

    @Column(name = "response_schema", columnDefinition = "TEXT")
    private String responseSchema;

    // Parameters
    @Column(name = "path_parameters", columnDefinition = "TEXT")
    private String pathParameters;

    @Column(name = "query_parameters", columnDefinition = "TEXT")
    private String queryParameters;

    @Column(name = "header_parameters", columnDefinition = "TEXT")
    private String headerParameters;

    // Examples
    @Column(name = "request_examples", columnDefinition = "TEXT")
    private String requestExamples;

    @Column(name = "response_examples", columnDefinition = "TEXT")
    private String responseExamples;

    // Security
    @Column(name = "security_requirements", columnDefinition = "TEXT")
    private String securityRequirements;

    // Metadata
    @Column(name = "is_deprecated")
    private Boolean isDeprecated;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (isDeprecated == null) {
            isDeprecated = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Get unique identifier for this endpoint
     */
    public String getUniqueIdentifier() {
        return method + " " + path;
    }
}