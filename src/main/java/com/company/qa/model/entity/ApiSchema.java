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
@Table(name = "api_schemas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSchema {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_id", nullable = false)
    private ApiSpecification specification;

    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    @Column(name = "schema_type", length = 50)
    private String schemaType;

    @Column(name = "schema_definition", nullable = false, columnDefinition = "TEXT")
    private String schemaDefinition;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_enum")
    private Boolean isEnum;

    // Store enum values as JSON string in TEXT column
    @Column(name = "enum_values", columnDefinition = "TEXT")
    private String enumValuesJson;

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

    // Convenience methods for enum values
    @Transient
    public List<String> getEnumValues() {
        if (enumValuesJson == null || enumValuesJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            String[] array = objectMapper.readValue(enumValuesJson, String[].class);
            return Arrays.asList(array);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse enum values JSON: {}", enumValuesJson, e);
            return new ArrayList<>();
        }
    }

    public void setEnumValues(List<String> enumValues) {
        if (enumValues == null || enumValues.isEmpty()) {
            this.enumValuesJson = null;
            return;
        }
        try {
            this.enumValuesJson = objectMapper.writeValueAsString(enumValues);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize enum values to JSON", e);
            this.enumValuesJson = null;
        }
    }

    public void setEnumValues(String[] enumValues) {
        if (enumValues == null || enumValues.length == 0) {
            this.enumValuesJson = null;
            return;
        }
        setEnumValues(Arrays.asList(enumValues));
    }

    public void incrementRequestUsage() {
        this.usedInRequests++;
    }

    public void incrementResponseUsage() {
        this.usedInResponses++;
    }
}