package com.company.qa.model.entity;

import com.company.qa.model.entity.JiraStory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entity representing AI-generated test code with approval workflow.
 *
 * Architecture:
 * - JSONB storage for flexible test code structure (feature files, step definitions, POMs)
 * - Approval workflow: DRAFT -> PENDING_REVIEW -> APPROVED/REJECTED -> COMMITTED
 * - Integrates with Week 7 cost tracking and Week 6 quality gates
 * - Transient fields for JSONB deserialization (same pattern as JiraStory)
 *
 */
@Entity
@Table(name = "ai_generated_tests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIGeneratedTest extends BaseEntity {  // ✅ Extends BaseEntity for UUID id

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    // ============================================================
    // SOURCE REFERENCE
    // ============================================================

    // Change 1: Make JiraStory relationship optional
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jira_story_id", nullable = true)
    private JiraStory jiraStory;  // ✅ UUID FK

    @Column(name = "jira_story_key", nullable = false, length = 50)
    private String jiraStoryKey;

    // ============================================================
    // TEST METADATA
    // ============================================================

    @Column(name = "test_name", nullable = false)
    private String testName;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 50)
    private TestType testType;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_framework", nullable = false, length = 50)
    private TestFramework testFramework;

    // ============================================================
    // GENERATED CONTENT (JSONB)
    // ============================================================

    /**
     * JSONB column storing generated test code.
     * Structure: {
     *   "featureFile": "Feature content...",
     *   "stepDefinitions": ["StepDef1.java", "StepDef2.java"],
     *   "pageObjects": ["LoginPage.java", "DashboardPage.java"],
     *   "testData": {...}
     * }
     */
    @Column(name = "test_code_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String testCodeJsonRaw;

    @Transient
    private Map<String, Object> testCodeJson;

    // ============================================================
    // AI GENERATION METADATA
    // ============================================================

    @Column(name = "ai_provider", nullable = false, length = 50)
    private String aiProvider;

    @Column(name = "ai_model", nullable = false, length = 100)
    private String aiModel;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_cost_usd", precision = 10, scale = 6)
    private BigDecimal totalCostUsd;

    // ============================================================
    // QUALITY METRICS (from AIRecommendationService)
    // ============================================================

    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", length = 20)
    private ConfidenceLevel confidenceLevel;

    /**
     * JSONB array of quality concern objects.
     * Structure: [
     *   {"type": "INCOMPLETE_COVERAGE", "severity": "MEDIUM", "message": "..."},
     *   {"type": "MISSING_ASSERTIONS", "severity": "HIGH", "message": "..."}
     * ]
     */
    @Column(name = "quality_concerns", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String qualityConcernsRaw;

    @Transient
    private List<QualityConcern> qualityConcerns;

    // ============================================================
    // APPROVAL WORKFLOW
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private TestGenerationStatus status = TestGenerationStatus.DRAFT;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comments", columnDefinition = "TEXT")
    private String reviewComments;

    // ============================================================
    // FILE SYSTEM TRACKING
    // ============================================================

    @Column(name = "draft_folder_path", length = 500)
    private String draftFolderPath;

    @Column(name = "committed_folder_path", length = 500)
    private String committedFolderPath;

    // ============================================================
    // TIMESTAMPS
    // ============================================================

    @Column(name = "generated_at", nullable = false)
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();

   /* @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();*/

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    // ============================================================
    // JSONB HELPERS (Same pattern as JiraStory)
    // ============================================================

    @PostLoad
    public void deserializeJsonFields() {
        deserializeTestCodeJson();
        deserializeQualityConcerns();
    }

    @PrePersist
    @PreUpdate
    public void serializeJsonFields() {
        serializeTestCodeJson();
        serializeQualityConcerns();
    }

    private void deserializeTestCodeJson() {
        if (testCodeJsonRaw != null && !testCodeJsonRaw.isEmpty()) {
            try {
                testCodeJson = OBJECT_MAPPER.readValue(
                        testCodeJsonRaw,
                        new TypeReference<Map<String, Object>>() {}
                );
            } catch (JsonProcessingException e) {
                testCodeJson = new HashMap<>();
            }
        }
    }

    private void serializeTestCodeJson() {
        if (testCodeJson != null) {
            try {
                testCodeJsonRaw = OBJECT_MAPPER.writeValueAsString(testCodeJson);
            } catch (JsonProcessingException e) {
                testCodeJsonRaw = "{}";
            }
        }
    }

    private void deserializeQualityConcerns() {
        if (qualityConcernsRaw != null && !qualityConcernsRaw.isEmpty()) {
            try {
                qualityConcerns = OBJECT_MAPPER.readValue(
                        qualityConcernsRaw,
                        new TypeReference<List<QualityConcern>>() {}
                );
            } catch (JsonProcessingException e) {
                qualityConcerns = new ArrayList<>();
            }
        }
    }

    private void serializeQualityConcerns() {
        if (qualityConcerns != null) {
            try {
                qualityConcernsRaw = OBJECT_MAPPER.writeValueAsString(qualityConcerns);
            } catch (JsonProcessingException e) {
                qualityConcernsRaw = "[]";
            }
        }
    }

    // ============================================================
    // BUSINESS METHODS
    // ============================================================

    public boolean isPendingReview() {
        return status == TestGenerationStatus.DRAFT ||
                status == TestGenerationStatus.PENDING_REVIEW;
    }

    public boolean isApproved() {
        return status == TestGenerationStatus.APPROVED;
    }

    public boolean isCommitted() {
        return status == TestGenerationStatus.COMMITTED;
    }

    public void markAsApproved(String reviewer, String comments) {
        this.status = TestGenerationStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.reviewComments = comments;
    }

    public void markAsRejected(String reviewer, String comments) {
        this.status = TestGenerationStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.reviewComments = comments;
    }

    public void markAsCommitted(String folderPath) {
        this.status = TestGenerationStatus.COMMITTED;
        this.committedFolderPath = folderPath;
        this.committedAt = LocalDateTime.now();
    }

    // ============================================================
    // ENUMS
    // ============================================================

    public enum TestType {
        UI, API, E2E, UNIT
    }

    public enum TestFramework {
        CUCUMBER, TESTNG, JUNIT
    }

    public enum TestGenerationStatus {
        DRAFT,           // Initial generation
        PENDING_REVIEW,  // Submitted for QA review
        APPROVED,        // QA approved, ready to commit
        REJECTED,        // QA rejected, needs regeneration
        COMMITTED        // Written to file system
    }

    public enum ConfidenceLevel {
        HIGH, MEDIUM, LOW
    }

    // ============================================================
    // NESTED CLASSES
    // ============================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QualityConcern {
        private String type;
        private String severity;
        private String message;
        private String suggestion;
    }
}