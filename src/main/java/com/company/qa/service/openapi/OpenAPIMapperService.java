package com.company.qa.service.openapi;

import com.company.qa.model.dto.ApiEndpointResponseDTO;
import com.company.qa.model.dto.ApiSchemaResponseDTO;
import com.company.qa.model.dto.ApiSpecificationResponseDTO;
import com.company.qa.model.entity.ApiEndpoint;
import com.company.qa.model.entity.ApiSchema;
import com.company.qa.model.entity.ApiSpecification;
import org.springframework.stereotype.Service;

/**
 * Maps entities to response DTOs
 */
@Service
public class OpenAPIMapperService {

    /**
     * Map ApiSpecification to response DTO
     */
    public ApiSpecificationResponseDTO toResponseDTO(ApiSpecification spec) {
        return ApiSpecificationResponseDTO.builder()
                .id(spec.getId())
                .name(spec.getName())
                .version(spec.getVersion())
                .description(spec.getDescription())
                .openapiVersion(spec.getOpenapiVersion())
                .baseUrl(spec.getBaseUrl())
                .specFormat(spec.getSpecFormat())
                .endpointCount(spec.getEndpointCount())
                .schemaCount(spec.getSchemaCount())
                .uploadedAt(spec.getUploadedAt())
                .uploadedBy(spec.getUploadedBy())
                .isActive(spec.getIsActive())
                .createdAt(spec.getCreatedAt())
                .updatedAt(spec.getUpdatedAt())
                .build();
    }

    /**
     * Map ApiEndpoint to response DTO
     */
    public ApiEndpointResponseDTO toResponseDTO(ApiEndpoint endpoint) {
        return ApiEndpointResponseDTO.builder()
                .id(endpoint.getId())
                .specificationId(endpoint.getSpecification().getId())
                .specificationName(endpoint.getSpecification().getName())
                .path(endpoint.getPath())
                .method(endpoint.getMethod())
                .operationId(endpoint.getOperationId())
                .summary(endpoint.getSummary())
                .description(endpoint.getDescription())
                .tags(endpoint.getTags())
                .hasRequestBody(endpoint.getRequestSchema() != null)
                .hasResponse(endpoint.getResponseSchema() != null)
                .isDeprecated(endpoint.getIsDeprecated())
                .createdAt(endpoint.getCreatedAt())
                .build();
    }

    /**
     * Map ApiSchema to response DTO
     */
    public ApiSchemaResponseDTO toResponseDTO(ApiSchema schema) {
        return ApiSchemaResponseDTO.builder()
                .id(schema.getId())
                .specificationId(schema.getSpecification().getId())
                .specificationName(schema.getSpecification().getName())
                .schemaName(schema.getSchemaName())
                .schemaType(schema.getSchemaType())
                .description(schema.getDescription())
                .isEnum(schema.getIsEnum())
                .enumValues(schema.getEnumValues())
                .usedInRequests(schema.getUsedInRequests())
                .usedInResponses(schema.getUsedInResponses())
                .createdAt(schema.getCreatedAt())
                .build();
    }
}