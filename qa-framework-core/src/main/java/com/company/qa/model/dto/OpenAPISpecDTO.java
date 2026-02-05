package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for uploading OpenAPI specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAPISpecDTO {

    private String name;
    private String version;
    private String description;
    private String content;  // Raw JSON or YAML content
    private String format;   // JSON or YAML

    // Parsed metadata (populated after parsing)
    private String openapiVersion;
    private String baseUrl;
    private Integer endpointCount;
    private Integer schemaCount;
    private List<String> tags;
}