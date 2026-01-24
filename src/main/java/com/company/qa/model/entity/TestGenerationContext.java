package com.company.qa.model.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Entity
@Table(name = "test_generation_context")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestGenerationContext {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_id")
    private ApiSpecification specification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id")
    private ApiEndpoint endpoint;

    @Column(name = "prompt_with_context", columnDefinition = "TEXT")
    private String promptWithContext;

    @Column(name = "context_type", length = 50)
    private String contextType;

    // Store schemas used as JSON string in TEXT column
    @Column(name = "schemas_used", columnDefinition = "TEXT")
    private String schemasUsedJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "ai_cost", precision = 10, scale = 4)
    private BigDecimal aiCost;

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

    // Convenience methods for schemas used
    @Transient
    public List<String> getSchemasUsed() {
        if (schemasUsedJson == null || schemasUsedJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            String[] array = objectMapper.readValue(schemasUsedJson, String[].class);
            return Arrays.asList(array);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse schemas used JSON: {}", schemasUsedJson, e);
            return new ArrayList<>();
        }
    }

    public void setSchemasUsed(List<String> schemasUsed) {
        if (schemasUsed == null || schemasUsed.isEmpty()) {
            this.schemasUsedJson = null;
            return;
        }
        try {
            this.schemasUsedJson = objectMapper.writeValueAsString(schemasUsed);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize schemas used to JSON", e);
            this.schemasUsedJson = null;
        }
    }

    public void setSchemasUsed(String[] schemasUsed) {
        if (schemasUsed == null || schemasUsed.length == 0) {
            this.schemasUsedJson = null;
            return;
        }
        setSchemasUsed(Arrays.asList(schemasUsed));
    }

    public void markApproved(String notes) {
        this.approved = true;
        this.approvalNotes = notes;
    }

    public void markRejected(String notes) {
        this.approved = false;
        this.approvalNotes = notes;
    }
}