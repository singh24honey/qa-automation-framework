package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for parsed endpoint information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedEndpointDTO {

    private String path;
    private String method;
    private String operationId;
    private String summary;
    private String description;
    private List<String> tags;

    // Schemas
    private String requestSchema;
    private String responseSchema;

    // Parameters
    private List<ParameterDTO> pathParameters;
    private List<ParameterDTO> queryParameters;
    private List<ParameterDTO> headerParameters;

    // Examples
    private Map<String, Object> requestExample;
    private Map<String, Object> responseExample;

    // Security
    private List<String> securityRequirements;

    private Boolean isDeprecated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterDTO {
        private String name;
        private String type;
        private Boolean required;
        private String description;
        private Object defaultValue;
        private String example;
    }
}