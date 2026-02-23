package com.company.qa.model.entity;

import com.company.qa.model.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent storage of failure patterns
 */
@Entity
@Table(name = "test_failure_patterns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestFailurePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_name", nullable = false, length = 500)
    private String testName;

    @Column(name = "pattern_type", nullable = false, length = 50)
    private String patternType;

    @Column(name = "error_signature", nullable = false, length = 200)
    private String errorSignature;

    @Column(name = "occurrences", nullable = false)
    @Builder.Default
    private Integer occurrences = 1;

    @Column(name = "first_detected_at", nullable = false)
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private Instant lastDetectedAt;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private Boolean isResolved = false;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "affected_browsers", columnDefinition = "TEXT")
    @Convert(converter = StringListConverter.class)
    @Builder.Default
    private List<String> affectedBrowsers = new ArrayList<>();

    @Column(name = "impact_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal impactScore = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (firstDetectedAt == null) {
            firstDetectedAt = Instant.now();
        }
        if (lastDetectedAt == null) {
            lastDetectedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Mark pattern as resolved
     */
    public void markResolved(String notes) {
        this.isResolved = true;
        this.resolvedAt = Instant.now();
        this.resolutionNotes = notes;
    }

    /**
     * Increment occurrence count
     */
    public void incrementOccurrences() {
        this.occurrences++;
        this.lastDetectedAt = Instant.now();
    }
}