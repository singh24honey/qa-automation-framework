package com.company.qa.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "jira_stories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JiraStory {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    @JsonIgnore
    private JiraConfiguration configuration;

    @Column(name = "jira_key", unique = true, nullable = false, length = 50)
    private String jiraKey;

    @Column(name = "jira_id", length = 50)
    private String jiraId;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "acceptance_criteria", columnDefinition = "TEXT")
    private String acceptanceCriteria;

    @Column(name = "story_type", length = 50)
    private String storyType;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "assignee", length = 100)
    private String assignee;

    @Column(name = "reporter", length = 100)
    private String reporter;

    @Column(name = "labels", columnDefinition = "text")
    private String labelsString;

    @Column(name = "components", columnDefinition = "text")
    private String componentsString;

    @Column(name = "jira_created_at")
    private LocalDateTime jiraCreatedAt;

    @Column(name = "jira_updated_at")
    private LocalDateTime jiraUpdatedAt;

    // ✅ THE FIX: Use ColumnTransformer to cast to JSONB
    @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(
            write = "?::jsonb"
    )
    private String rawJsonString;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ✅ THE FIX: Use ColumnTransformer to cast to JSONB
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    @ColumnTransformer(
            write = "?::jsonb"
    )
    private String customFieldsString;

    @Transient
    private Map<String, Object> rawJson;

    @Transient
    private Map<String, Object> customFields;

    // Array getters/setters
    public String[] getLabels() {
        if (labelsString == null || labelsString.isEmpty()) {
            return new String[0];
        }
        return labelsString.split("\\|\\|\\|");
    }

    public void setLabels(String[] labels) {
        if (labels == null || labels.length == 0) {
            this.labelsString = null;
        } else {
            this.labelsString = String.join("|||", labels);
        }
    }

    public String[] getComponents() {
        if (componentsString == null || componentsString.isEmpty()) {
            return new String[0];
        }
        return componentsString.split("\\|\\|\\|");
    }

    public void setComponents(String[] components) {
        if (components == null || components.length == 0) {
            this.componentsString = null;
        } else {
            this.componentsString = String.join("|||", components);
        }
    }

    // JSON Map getters/setters
    public Map<String, Object> getRawJson() {
        if (rawJson == null && rawJsonString != null) {
            try {
                rawJson = objectMapper.readValue(rawJsonString, Map.class);
            } catch (JsonProcessingException e) {
                rawJson = new HashMap<>();
            }
        }
        return rawJson;
    }

    public void setRawJson(Map<String, Object> rawJson) {
        this.rawJson = rawJson;
        if (rawJson != null) {
            try {
                this.rawJsonString = objectMapper.writeValueAsString(rawJson);
            } catch (JsonProcessingException e) {
                this.rawJsonString = "{}";
            }
        } else {
            this.rawJsonString = null;
        }
    }

    public Map<String, Object> getCustomFields() {
        if (customFields == null && customFieldsString != null) {
            try {
                customFields = objectMapper.readValue(customFieldsString, Map.class);
            } catch (JsonProcessingException e) {
                customFields = new HashMap<>();
            }
        }
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
        if (customFields != null) {
            try {
                this.customFieldsString = objectMapper.writeValueAsString(customFields);
            } catch (JsonProcessingException e) {
                this.customFieldsString = null;
            }
        } else {
            this.customFieldsString = null;
        }
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (fetchedAt == null) {
            fetchedAt = now;
        }

        // ✅ ALWAYS guarantee raw_json
        if (rawJsonString == null) {
            if (rawJson != null) {
                setRawJson(rawJson);
            } else {
                rawJsonString = "{}";
            }
        }

        // Optional but consistent
        if (customFieldsString == null && customFields != null) {
            setCustomFields(customFields);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (rawJson != null) {
            setRawJson(rawJson);
        }
        if (customFields != null) {
            setCustomFields(customFields);
        }
    }

    public String getDisplayName() {
        return String.format("%s: %s", jiraKey, summary);
    }

    public boolean hasAcceptanceCriteria() {
        return acceptanceCriteria != null && !acceptanceCriteria.trim().isEmpty();
    }

    public boolean isReadyForTestGeneration() {
        return summary != null && !summary.trim().isEmpty() &&
                (description != null || acceptanceCriteria != null);
    }
}