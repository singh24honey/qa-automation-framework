package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for API endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpointResponseDTO {

    private Long id;
    private Long specificationId;
    private String specificationName;

    private String path;
    private String method;
    private String operationId;
    private String summary;
    private String description;
    private List<String> tags;

    private Boolean hasRequestBody;
    private Boolean hasResponse;
    private Boolean isDeprecated;

    private Instant createdAt;
}