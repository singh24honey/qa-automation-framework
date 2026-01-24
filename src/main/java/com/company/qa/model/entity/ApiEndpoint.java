package com.company.qa.model.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Entity
@Table(name = "api_endpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpoint {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_id", nullable = false)
    private ApiSpecification specification;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "method", nullable = false, length = 10)
    private String method;

    @Column(name = "operation_id")
    private String operationId;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Store tags as JSON string in TEXT column
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "request_schema", columnDefinition = "TEXT")
    private String requestSchema;

    @Column(name = "response_schema", columnDefinition = "TEXT")
    private String responseSchema;

    @Column(name = "path_parameters", columnDefinition = "TEXT")
    private String pathParameters;

    @Column(name = "query_parameters", columnDefinition = "TEXT")
    private String queryParameters;

    @Column(name = "header_parameters", columnDefinition = "TEXT")
    private String headerParameters;

    @Column(name = "request_examples", columnDefinition = "TEXT")
    private String requestExamples;

    @Column(name = "response_examples", columnDefinition = "TEXT")
    private String responseExamples;

    @Column(name = "security_requirements", columnDefinition = "TEXT")
    private String securityRequirements;

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

    // Convenience methods for tags
    @Transient
    public List<String> getTags() {
        if (tagsJson == null || tagsJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            String[] array = objectMapper.readValue(tagsJson, String[].class);
            return Arrays.asList(array);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tags JSON: {}", tagsJson, e);
            return new ArrayList<>();
        }
    }

    public void setTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            this.tagsJson = null;
            return;
        }
        try {
            this.tagsJson = objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tags to JSON", e);
            this.tagsJson = null;
        }
    }

    public void setTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            this.tagsJson = null;
            return;
        }
        setTags(Arrays.asList(tags));
    }

    public String getUniqueIdentifier() {
        return method + " " + path;
    }
}