package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for API schema
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSchemaResponseDTO {

    private Long id;
    private Long specificationId;
    private String specificationName;

    private String schemaName;
    private String schemaType;
    private String description;

    private Boolean isEnum;
    private List<String> enumValues;

    private Integer usedInRequests;
    private Integer usedInResponses;

    private Instant createdAt;
}