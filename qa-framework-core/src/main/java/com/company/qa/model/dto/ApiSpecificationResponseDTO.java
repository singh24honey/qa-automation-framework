package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for API specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSpecificationResponseDTO {

    private Long id;
    private String name;
    private String version;
    private String description;
    private String openapiVersion;
    private String baseUrl;
    private String specFormat;

    private Integer endpointCount;
    private Integer schemaCount;

    private Instant uploadedAt;
    private String uploadedBy;
    private Boolean isActive;

    private Instant createdAt;
    private Instant updatedAt;
}