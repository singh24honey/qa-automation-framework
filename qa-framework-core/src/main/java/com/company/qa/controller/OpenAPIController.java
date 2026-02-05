package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.model.entity.ApiEndpoint;
import com.company.qa.model.entity.ApiSchema;
import com.company.qa.model.entity.ApiSpecification;
import com.company.qa.repository.ApiEndpointRepository;
import com.company.qa.repository.ApiSchemaRepository;
import com.company.qa.repository.ApiSpecificationRepository;
import com.company.qa.service.openapi.ApiSpecificationService;
import com.company.qa.service.openapi.OpenAPIMapperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for OpenAPI specification management
 */
@Slf4j
@RestController
@RequestMapping("/api/openapi")
@RequiredArgsConstructor
@Tag(name = "OpenAPI Management", description = "Upload and manage OpenAPI specifications")
public class OpenAPIController {

    private final ApiSpecificationService specificationService;
    private final OpenAPIMapperService mapperService;
    private final ApiSpecificationRepository specificationRepository;
    private final ApiEndpointRepository endpointRepository;
    private final ApiSchemaRepository schemaRepository;

    /**
     * Upload OpenAPI specification file
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload OpenAPI specification",
            description = "Upload an OpenAPI/Swagger specification file (JSON or YAML)")
    public ResponseEntity<UploadResponseDTO> uploadSpecification(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false, defaultValue = "system") String uploadedBy) {

        log.info("Uploading OpenAPI specification: {} (size: {} bytes)",
                file.getOriginalFilename(), file.getSize());

        try {
            // Read file content
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            // Detect format from filename
            String format = detectFormat(file.getOriginalFilename(), content);

            // Upload and parse
            ApiSpecification spec = specificationService.uploadSpecification(content, format, uploadedBy);

            // Build response
            UploadResponseDTO response = UploadResponseDTO.builder()
                    .success(true)
                    .message("OpenAPI specification uploaded successfully")
                    .specificationId(spec.getId())
                    .specificationName(spec.getName())
                    .version(spec.getVersion())
                    .endpointsExtracted(spec.getEndpointCount())
                    .schemasExtracted(spec.getSchemaCount())
                    .build();

            log.info("Upload successful: {} v{} (endpoints: {}, schemas: {})",
                    spec.getName(), spec.getVersion(),
                    spec.getEndpointCount(), spec.getSchemaCount());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            log.error("Failed to read file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.badRequest().body(
                    UploadResponseDTO.builder()
                            .success(false)
                            .message("Failed to read file: " + e.getMessage())
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to upload specification: {}", file.getOriginalFilename(), e);
            return ResponseEntity.badRequest().body(
                    UploadResponseDTO.builder()
                            .success(false)
                            .message("Failed to parse specification: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * Get all specifications
     */
    @GetMapping("/specifications")
    @Operation(summary = "List all specifications", description = "Get all active OpenAPI specifications")
    public ResponseEntity<List<ApiSpecificationResponseDTO>> getAllSpecifications() {
        log.info("Fetching all active specifications");

        List<ApiSpecification> specs = specificationService.getAllActiveSpecifications();
        List<ApiSpecificationResponseDTO> response = specs.stream()
                .map(mapperService::toResponseDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get specification by ID
     */
    @GetMapping("/specifications/{id}")
    @Operation(summary = "Get specification details", description = "Get detailed information about a specification")
    public ResponseEntity<ApiSpecificationResponseDTO> getSpecification(
            @Parameter(description = "Specification ID") @PathVariable Long id) {

        log.info("Fetching specification: {}", id);

        ApiSpecification spec = specificationService.getSpecification(id);
        return ResponseEntity.ok(mapperService.toResponseDTO(spec));
    }

    /**
     * Delete specification
     */
    @DeleteMapping("/specifications/{id}")
    @Operation(summary = "Delete specification", description = "Delete an OpenAPI specification and all its data")
    public ResponseEntity<Void> deleteSpecification(
            @Parameter(description = "Specification ID") @PathVariable Long id) {

        log.info("Deleting specification: {}", id);
        specificationService.deleteSpecification(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get endpoints for specification
     */
    @GetMapping("/specifications/{id}/endpoints")
    @Operation(summary = "Get specification endpoints", description = "Get all endpoints for a specification")
    public ResponseEntity<List<ApiEndpointResponseDTO>> getEndpoints(
            @Parameter(description = "Specification ID") @PathVariable Long id) {

        log.info("Fetching endpoints for specification: {}", id);

        List<ApiEndpoint> endpoints = specificationService.getEndpoints(id);
        List<ApiEndpointResponseDTO> response = endpoints.stream()
                .map(mapperService::toResponseDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get schemas for specification
     */
    @GetMapping("/specifications/{id}/schemas")
    @Operation(summary = "Get specification schemas", description = "Get all schemas for a specification")
    public ResponseEntity<List<ApiSchemaResponseDTO>> getSchemas(
            @Parameter(description = "Specification ID") @PathVariable Long id) {

        log.info("Fetching schemas for specification: {}", id);

        List<ApiSchema> schemas = specificationService.getSchemas(id);
        List<ApiSchemaResponseDTO> response = schemas.stream()
                .map(mapperService::toResponseDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Search endpoints
     */
    @GetMapping("/endpoints/search")
    @Operation(summary = "Search endpoints", description = "Search endpoints by path or summary")
    public ResponseEntity<List<ApiEndpointResponseDTO>> searchEndpoints(
            @Parameter(description = "Search query") @RequestParam("q") String query) {

        log.info("Searching endpoints: {}", query);

        List<ApiEndpoint> endpoints = specificationService.searchEndpoints(query);
        List<ApiEndpointResponseDTO> response = endpoints.stream()
                .map(mapperService::toResponseDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Search schemas
     */
    @GetMapping("/schemas/search")
    @Operation(summary = "Search schemas", description = "Search schemas by name")
    public ResponseEntity<List<ApiSchemaResponseDTO>> searchSchemas(
            @Parameter(description = "Search query") @RequestParam("q") String query) {

        log.info("Searching schemas: {}", query);

        List<ApiSchema> schemas = schemaRepository.searchSchemas(query);
        List<ApiSchemaResponseDTO> response = schemas.stream()
                .map(mapperService::toResponseDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get overall statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get statistics", description = "Get overall OpenAPI import statistics")
   /* public ResponseEntity<StatisticsResponseDTO> getStatistics() {
        log.info("Fetching OpenAPI statistics");

        long totalSpecs = specificationRepository.count();
        long activeSpecs = specificationRepository.countByIsActiveTrue();
        long totalEndpoints = endpointRepository.count();
        long totalSchemas = schemaRepository.count();
        long deprecatedEndpoints = endpointRepository.findByIsDeprecatedTrue().size();

        StatisticsResponseDTO response = StatisticsResponseDTO.builder()
                .totalSpecifications(totalSpecs)
                .activeSpecifications(activeSpecs)
                .totalEndpoints(totalEndpoints)
                .totalSchemas(totalSchemas)
                .deprecatedEndpoints(deprecatedEndpoints)
                .build();

        return ResponseEntity.ok(response);*/
  //  }

    // ========== HELPER METHODS ==========

    /**
     * Detect format from filename or content
     */
    private String detectFormat(String filename, String content) {
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".json")) {
                return "JSON";
            } else if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
                return "YAML";
            }
        }

        // Fallback to content detection
        String trimmed = content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "JSON";
        }

        return "YAML";
    }
}