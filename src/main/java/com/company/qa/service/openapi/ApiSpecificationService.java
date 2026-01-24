package com.company.qa.service.openapi;

import com.company.qa.model.dto.OpenAPISpecDTO;
import com.company.qa.model.dto.ParsedEndpointDTO;
import com.company.qa.model.dto.ParsedSchemaDTO;
import com.company.qa.model.entity.ApiEndpoint;
import com.company.qa.model.entity.ApiSchema;
import com.company.qa.model.entity.ApiSpecification;
import com.company.qa.repository.ApiEndpointRepository;
import com.company.qa.repository.ApiSchemaRepository;
import com.company.qa.repository.ApiSpecificationRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing API specifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiSpecificationService {

    private final ApiSpecificationRepository specRepository;
    private final ApiEndpointRepository endpointRepository;
    private final ApiSchemaRepository schemaRepository;
    private final OpenAPIParserService parserService;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Upload and parse OpenAPI specification
     */
    @Transactional
    public ApiSpecification uploadSpecification(String content, String format, String uploadedBy) {
        log.info("Uploading OpenAPI specification (format: {}, uploadedBy: {})", format, uploadedBy);

        // Step 1: Validate format
        if (format == null || format.trim().isEmpty()) {
            format = parserService.detectFormat(content);
            log.info("Auto-detected format: {}", format);
        }

        // Step 2: Validate specification
        parserService.validateSpecification(content, format);

        // Step 3: Parse OpenAPI
        OpenAPI openAPI = parserService.parseSpecification(content, format);

        // Step 4: Extract metadata
        OpenAPISpecDTO specDTO = parserService.extractMetadata(openAPI, content, format);

        // Step 5: Check if spec already exists
        Optional<ApiSpecification> existing = specRepository.findByNameAndVersion(
                specDTO.getName(), specDTO.getVersion());

        if (existing.isPresent()) {
            log.info("Specification already exists, updating: {} v{}",
                    specDTO.getName(), specDTO.getVersion());
            return updateSpecification(existing.get().getId(), content, format, uploadedBy);
        }

        // Step 6: Create ApiSpecification entity
        ApiSpecification specification = ApiSpecification.builder()
                .name(specDTO.getName())
                .version(specDTO.getVersion())
                .description(specDTO.getDescription())
                .openapiVersion(specDTO.getOpenapiVersion())
                .baseUrl(specDTO.getBaseUrl())
                .specContent(content)
                .specFormat(format.toUpperCase())
                .uploadedBy(uploadedBy)
                .uploadedAt(Instant.now())
                .isActive(true)
                .endpointCount(0)
                .schemaCount(0)
                .build();

        // Step 7: Save specification first
        ApiSpecification savedSpec = specRepository.save(specification);
        log.info("Saved specification: {} (id: {})", savedSpec.getName(), savedSpec.getId());

        // Step 8: Extract and save endpoints
        List<ParsedEndpointDTO> parsedEndpoints = parserService.extractEndpoints(openAPI);
        List<ApiEndpoint> endpoints = saveEndpoints(savedSpec, parsedEndpoints);
        log.info("Saved {} endpoints", endpoints.size());

        // Step 9: Extract and save schemas
        List<ParsedSchemaDTO> parsedSchemas = parserService.extractSchemas(openAPI);
        List<ApiSchema> schemas = saveSchemas(savedSpec, parsedSchemas);
        log.info("Saved {} schemas", schemas.size());

        // Step 10: Update counts
        savedSpec.setEndpointCount(endpoints.size());
        savedSpec.setSchemaCount(schemas.size());
        savedSpec = specRepository.save(savedSpec);

        log.info("Upload complete: {} v{} with {} endpoints and {} schemas",
                savedSpec.getName(), savedSpec.getVersion(),
                savedSpec.getEndpointCount(), savedSpec.getSchemaCount());

        return savedSpec;
    }

    /**
     * Update existing specification
     */
    @Transactional
    public ApiSpecification updateSpecification(Long specId, String content,
                                                String format, String uploadedBy) {
        log.info("Updating specification: {}", specId);

        ApiSpecification existing = specRepository.findById(specId)
                .orElseThrow(() -> new IllegalArgumentException("Specification not found: " + specId));

        // Validate and parse new content
        parserService.validateSpecification(content, format);
        OpenAPI openAPI = parserService.parseSpecification(content, format);
        OpenAPISpecDTO specDTO = parserService.extractMetadata(openAPI, content, format);

        // Delete old endpoints and schemas (cascade will handle this)
        endpointRepository.deleteAll(endpointRepository.findBySpecificationIdOrderByPathAsc(specId));
        schemaRepository.deleteAll(schemaRepository.findBySpecificationIdOrderBySchemaNameAsc(specId));

        // Update specification
        existing.setSpecContent(content);
        existing.setSpecFormat(format.toUpperCase());
        existing.setDescription(specDTO.getDescription());
        existing.setOpenapiVersion(specDTO.getOpenapiVersion());
        existing.setBaseUrl(specDTO.getBaseUrl());
        existing.setUploadedAt(Instant.now());
        existing.setUploadedBy(uploadedBy);

        ApiSpecification updated = specRepository.save(existing);

        // Save new endpoints and schemas
        List<ParsedEndpointDTO> parsedEndpoints = parserService.extractEndpoints(openAPI);
        List<ApiEndpoint> endpoints = saveEndpoints(updated, parsedEndpoints);

        List<ParsedSchemaDTO> parsedSchemas = parserService.extractSchemas(openAPI);
        List<ApiSchema> schemas = saveSchemas(updated, parsedSchemas);

        // Update counts
        updated.setEndpointCount(endpoints.size());
        updated.setSchemaCount(schemas.size());
        updated = specRepository.save(updated);

        log.info("Updated specification: {} (endpoints: {}, schemas: {})",
                specId, endpoints.size(), schemas.size());

        return updated;
    }

    /**
     * Save endpoints for specification
     */
    private List<ApiEndpoint> saveEndpoints(ApiSpecification specification,
                                             List<ParsedEndpointDTO> parsedEndpoints) {

        List<ApiEndpoint> endpoints = parsedEndpoints.stream()
                .map(dto -> {
                    ApiEndpoint endpoint = ApiEndpoint.builder()
                            .specification(specification)
                            .path(dto.getPath())
                            .method(dto.getMethod())
                            .operationId(dto.getOperationId())
                            .summary(dto.getSummary())
                            .description(dto.getDescription())
                            // Use setTags method instead of direct assignment
                            .requestSchema(dto.getRequestSchema())
                            .responseSchema(dto.getResponseSchema())
                            .pathParameters(convertParametersToJson(dto.getPathParameters()))
                            .queryParameters(convertParametersToJson(dto.getQueryParameters()))
                            .headerParameters(convertParametersToJson(dto.getHeaderParameters()))
                            .securityRequirements(dto.getSecurityRequirements() != null ?
                                    gson.toJson(dto.getSecurityRequirements()) : null)
                            .isDeprecated(dto.getIsDeprecated())
                            .build();

                    // Set tags using the convenience method
                    if (dto.getTags() != null && !dto.getTags().isEmpty()) {
                        endpoint.setTags(dto.getTags());
                    }

                    return endpoint;
                })
                .collect(Collectors.toList());

        return endpointRepository.saveAll(endpoints);
    }
    /**
     * Save schemas for specification
     */
    private List<ApiSchema> saveSchemas(ApiSpecification specification,
                                        List<ParsedSchemaDTO> parsedSchemas) {

        List<ApiSchema> schemas = parsedSchemas.stream()
                .map(dto -> {
                    ApiSchema schema = ApiSchema.builder()
                            .specification(specification)
                            .schemaName(dto.getSchemaName())
                            .schemaType(dto.getSchemaType())
                            .description(dto.getDescription())
                            .schemaDefinition(dto.getSchemaDefinition())
                            .isEnum(dto.getIsEnum())
                            // Don't set enumValues here
                            .usedInRequests(0)
                            .usedInResponses(0)
                            .build();

                    // Set enum values using the convenience method
                    if (dto.getEnumValues() != null && !dto.getEnumValues().isEmpty()) {
                        schema.setEnumValues(dto.getEnumValues());
                    }

                    return schema;
                })
                .collect(Collectors.toList());

        return schemaRepository.saveAll(schemas);
    }

    /**
     * Convert parameter DTOs to JSON string
     */
    private String convertParametersToJson(List<ParsedEndpointDTO.ParameterDTO> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        return gson.toJson(parameters);
    }

    // ========== QUERY METHODS ==========

    /**
     * Get specification by ID
     */
    @Transactional(readOnly = true)
    public ApiSpecification getSpecification(Long id) {
        return specRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Specification not found: " + id));
    }

    /**
     * Get all active specifications
     */
    @Transactional(readOnly = true)
    public List<ApiSpecification> getAllActiveSpecifications() {
        return specRepository.findByIsActiveTrueOrderByUploadedAtDesc();
    }

    /**
     * Get endpoints for specification
     */
    @Transactional(readOnly = true)
    public List<ApiEndpoint> getEndpoints(Long specId) {
        return endpointRepository.findBySpecificationIdOrderByPathAsc(specId);
    }

    /**
     * Get schemas for specification
     */
    @Transactional(readOnly = true)
    public List<ApiSchema> getSchemas(Long specId) {
        return schemaRepository.findBySpecificationIdOrderBySchemaNameAsc(specId);
    }

    /**
     * Get endpoint by ID
     */
    @Transactional(readOnly = true)
    public ApiEndpoint getEndpoint(Long endpointId) {
        return endpointRepository.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found: " + endpointId));
    }

    /**
     * Get schema by ID
     */
    @Transactional(readOnly = true)
    public ApiSchema getSchema(Long schemaId) {
        return schemaRepository.findById(schemaId)
                .orElseThrow(() -> new IllegalArgumentException("Schema not found: " + schemaId));
    }

    /**
     * Search specifications by name
     */
    @Transactional(readOnly = true)
    public List<ApiSpecification> searchSpecifications(String searchTerm) {
        return specRepository.searchByName(searchTerm);
    }

    /**
     * Search endpoints
     */
    @Transactional(readOnly = true)
    public List<ApiEndpoint> searchEndpoints(String searchTerm) {
        return endpointRepository.searchEndpoints(searchTerm);
    }

    /**
     * Delete specification (cascade will delete endpoints and schemas)
     */
    @Transactional
    public void deleteSpecification(Long id) {
        log.info("Deleting specification: {}", id);

        ApiSpecification spec = getSpecification(id);
        specRepository.delete(spec);

        log.info("Deleted specification: {} v{}", spec.getName(), spec.getVersion());
    }

    /**
     * Deactivate specification (soft delete)
     */
    @Transactional
    public void deactivateSpecification(Long id) {
        log.info("Deactivating specification: {}", id);

        ApiSpecification spec = getSpecification(id);
        spec.setIsActive(false);
        specRepository.save(spec);

        log.info("Deactivated specification: {}", id);
    }

    /**
     * Get specification statistics
     */
    @Transactional(readOnly = true)
    public SpecificationStatistics getStatistics(Long specId) {
        ApiSpecification spec = getSpecification(specId);

        long endpointCount = endpointRepository.countBySpecificationId(specId);
        long schemaCount = schemaRepository.countBySpecificationId(specId);
        long deprecatedEndpoints = endpointRepository.findBySpecificationIdOrderByPathAsc(specId)
                .stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsDeprecated()))
                .count();

        return SpecificationStatistics.builder()
                .specificationId(specId)
                .name(spec.getName())
                .version(spec.getVersion())
                .totalEndpoints(endpointCount)
                .totalSchemas(schemaCount)
                .deprecatedEndpoints(deprecatedEndpoints)
                .uploadedAt(spec.getUploadedAt())
                .uploadedBy(spec.getUploadedBy())
                .build();
    }

    /**
     * Statistics DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SpecificationStatistics {
        private Long specificationId;
        private String name;
        private String version;
        private Long totalEndpoints;
        private Long totalSchemas;
        private Long deprecatedEndpoints;
        private Instant uploadedAt;
        private String uploadedBy;
    }
}