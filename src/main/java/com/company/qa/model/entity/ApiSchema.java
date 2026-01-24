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
 * Reusable schema components from OpenAPI specifications
 */
@Entity
@Table(name = "api_schemas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to parent spec
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_id", nullable = false)
    private ApiSpecification specification;

    // Schema identification
    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    @Column(name = "schema_type", length = 50)
    private String schemaType; // object, array, string, etc.

    // Schema definition
    @Column(name = "schema_definition", nullable = false, columnDefinition = "TEXT")
    private String schemaDefinition;

    // Metadata
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_enum")
    private Boolean isEnum;

    @Column(name = "enum_values", columnDefinition = "text[]")
    @Type(StringArrayType.class)
    private String[] enumValues;

    // Usage tracking
    @Column(name = "used_in_requests")
    private Integer usedInRequests;

    @Column(name = "used_in_responses")
    private Integer usedInResponses;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (isEnum == null) {
            isEnum = false;
        }
        if (usedInRequests == null) {
            usedInRequests = 0;
        }
        if (usedInResponses == null) {
            usedInResponses = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Increment usage counters
     */
    public void incrementRequestUsage() {
        this.usedInRequests++;
    }

    public void incrementResponseUsage() {
        this.usedInResponses++;
    }
}