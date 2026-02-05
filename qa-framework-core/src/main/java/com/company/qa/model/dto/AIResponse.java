package com.company.qa.model.dto;

import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponse {

    private boolean success;
    private String content;
    private AITaskType taskType;
    private AIProvider provider;
    private Integer tokensUsed;
    private Long durationMs;
    private String errorMessage;
    private Map<String, Object> metadata;
    private Instant generatedAt;

    // Convenience methods
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    public boolean isError() {
        return !success;
    }

    // Factory methods
    public static AIResponse success(String content, AIProvider provider, AITaskType taskType) {
        return AIResponse.builder()
                .success(true)
                .content(content)
                .provider(provider)
                .taskType(taskType)
                .generatedAt(Instant.now())
                .build();
    }

    public static AIResponse error(String errorMessage, AIProvider provider, AITaskType taskType) {
        return AIResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .provider(provider)
                .taskType(taskType)
                .generatedAt(Instant.now())
                .build();
    }
}