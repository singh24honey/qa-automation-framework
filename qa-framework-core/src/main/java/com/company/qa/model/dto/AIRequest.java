package com.company.qa.model.dto;

import com.company.qa.model.enums.AITaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIRequest {

    private AITaskType taskType;
    private String prompt;
    private Map<String, Object> context;
    private Integer maxTokens;
    private Double temperature;

    // Convenience methods
    public void addContext(String key, Object value) {
        if (context == null) {
            context = new HashMap<>();
        }
        context.put(key, value);
    }

    public Object getContext(String key) {
        return context != null ? context.get(key) : null;
    }
}