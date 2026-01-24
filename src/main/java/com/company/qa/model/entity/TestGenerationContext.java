package com.company.qa.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Links generated tests to the API context that was used
 */
@Entity
@Table(name = "test_generation_context")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestGenerationContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // References
    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_id")
    private ApiSpecification specification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id")
    private ApiEndpoint endpoint;

    // Context used for generation
    @Column(name = "prompt_with_context", columnDefinition = "TEXT")
    private String promptWithContext;

    @Column(name = "context_type", length = 50)
    private String contextType; // ENDPOINT, SCHEMA, FULL_SPEC

    // Schemas referenced
    @Column(name = "schemas_used", columnDefinition = "text[]")
    @Type(StringArrayType.class)
    private String[] schemasUsed;

    // Generation metadata
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "ai_cost", precision = 10, scale = 4)
    private BigDecimal aiCost;

    // Quality tracking
    @Column(name = "approved")
    private Boolean approved;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }

    /**
     * Mark as approved
     */
    public void markApproved(String notes) {
        this.approved = true;
        this.approvalNotes = notes;
    }

    /**
     * Mark as rejected
     */
    public void markRejected(String notes) {
        this.approved = false;
        this.approvalNotes = notes;
    }
}