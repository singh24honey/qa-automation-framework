package com.company.qa.service.openapi;

import com.company.qa.model.dto.OpenAPISpecDTO;
import com.company.qa.model.dto.ParsedEndpointDTO;
import com.company.qa.model.dto.ParsedSchemaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for parsing OpenAPI/Swagger specifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAPIParserService {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Parse OpenAPI specification from string content
     */
    public OpenAPI parseSpecification(String content, String format) {
        log.info("Parsing OpenAPI specification (format: {})", format);

        try {
            // Use swagger-parser to parse
            ParseOptions parseOptions = new ParseOptions();
            parseOptions.setResolve(true);
            parseOptions.setResolveFully(true);

            SwaggerParseResult result = new OpenAPIV3Parser().readContents(content, null, parseOptions);

            if (result.getMessages() != null && !result.getMessages().isEmpty()) {
                log.warn("Parser warnings: {}", result.getMessages());
            }

            OpenAPI openAPI = result.getOpenAPI();
            if (openAPI == null) {
                throw new IllegalArgumentException("Failed to parse OpenAPI specification: " +
                        (result.getMessages() != null ? String.join(", ", result.getMessages()) : "Unknown error"));
            }

            log.info("Successfully parsed OpenAPI spec: {} v{}",
                    openAPI.getInfo().getTitle(),
                    openAPI.getInfo().getVersion());

            return openAPI;

        } catch (Exception e) {
            log.error("Error parsing OpenAPI specification", e);
            throw new RuntimeException("Failed to parse OpenAPI specification: " + e.getMessage(), e);
        }
    }

    /**
     * Extract metadata from OpenAPI spec
     */
    public OpenAPISpecDTO extractMetadata(OpenAPI openAPI, String originalContent, String format) {
        log.info("Extracting metadata from OpenAPI spec");

        String baseUrl = null;
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            baseUrl = openAPI.getServers().get(0).getUrl();
        }

        // Extract unique tags
        Set<String> tags = new HashSet<>();
        if (openAPI.getPaths() != null) {
            openAPI.getPaths().values().forEach(pathItem -> {
                getAllOperations(pathItem).forEach(operation -> {
                    if (operation.getTags() != null) {
                        tags.addAll(operation.getTags());
                    }
                });
            });
        }

        return OpenAPISpecDTO.builder()
                .name(openAPI.getInfo().getTitle())
                .version(openAPI.getInfo().getVersion())
                .description(openAPI.getInfo().getDescription())
                .content(originalContent)
                .format(format)
                .openapiVersion(openAPI.getOpenapi())
                .baseUrl(baseUrl)
                .endpointCount(countEndpoints(openAPI))
                .schemaCount(countSchemas(openAPI))
                .tags(new ArrayList<>(tags))
                .build();
    }

    /**
     * Extract all endpoints from OpenAPI spec
     */
    public List<ParsedEndpointDTO> extractEndpoints(OpenAPI openAPI) {
        log.info("Extracting endpoints from OpenAPI spec");

        List<ParsedEndpointDTO> endpoints = new ArrayList<>();

        if (openAPI.getPaths() == null) {
            log.warn("No paths found in OpenAPI spec");
            return endpoints;
        }

        openAPI.getPaths().forEach((path, pathItem) -> {
            // GET
            if (pathItem.getGet() != null) {
                endpoints.add(extractEndpoint(path, "GET", pathItem.getGet(), pathItem.getParameters()));
            }
            // POST
            if (pathItem.getPost() != null) {
                endpoints.add(extractEndpoint(path, "POST", pathItem.getPost(), pathItem.getParameters()));
            }
            // PUT
            if (pathItem.getPut() != null) {
                endpoints.add(extractEndpoint(path, "PUT", pathItem.getPut(), pathItem.getParameters()));
            }
            // DELETE
            if (pathItem.getDelete() != null) {
                endpoints.add(extractEndpoint(path, "DELETE", pathItem.getDelete(), pathItem.getParameters()));
            }
            // PATCH
            if (pathItem.getPatch() != null) {
                endpoints.add(extractEndpoint(path, "PATCH", pathItem.getPatch(), pathItem.getParameters()));
            }
            // HEAD
            if (pathItem.getHead() != null) {
                endpoints.add(extractEndpoint(path, "HEAD", pathItem.getHead(), pathItem.getParameters()));
            }
            // OPTIONS
            if (pathItem.getOptions() != null) {
                endpoints.add(extractEndpoint(path, "OPTIONS", pathItem.getOptions(), pathItem.getParameters()));
            }
        });

        log.info("Extracted {} endpoints", endpoints.size());
        return endpoints;
    }

    /**
     * Extract single endpoint
     */
    private ParsedEndpointDTO extractEndpoint(String path, String method, Operation operation,
                                              List<Parameter> pathLevelParameters) {

        // Combine path-level and operation-level parameters
        List<Parameter> allParameters = new ArrayList<>();
        if (pathLevelParameters != null) {
            allParameters.addAll(pathLevelParameters);
        }
        if (operation.getParameters() != null) {
            allParameters.addAll(operation.getParameters());
        }

        // Extract parameters by type
        List<ParsedEndpointDTO.ParameterDTO> pathParams = extractParametersByType(allParameters, "path");
        List<ParsedEndpointDTO.ParameterDTO> queryParams = extractParametersByType(allParameters, "query");
        List<ParsedEndpointDTO.ParameterDTO> headerParams = extractParametersByType(allParameters, "header");

        // Extract request schema
        String requestSchema = extractRequestSchema(operation);

        // Extract response schema (for 200 response)
        String responseSchema = extractResponseSchema(operation);

        // Extract security requirements
        List<String> securityRequirements = extractSecurityRequirements(operation);

        return ParsedEndpointDTO.builder()
                .path(path)
                .method(method)
                .operationId(operation.getOperationId())
                .summary(operation.getSummary())
                .description(operation.getDescription())
                .tags(operation.getTags())
                .requestSchema(requestSchema)
                .responseSchema(responseSchema)
                .pathParameters(pathParams)
                .queryParameters(queryParams)
                .headerParameters(headerParams)
                .securityRequirements(securityRequirements)
                .isDeprecated(operation.getDeprecated() != null && operation.getDeprecated())
                .build();
    }

    /**
     * Extract parameters by type (path, query, header)
     */
    private List<ParsedEndpointDTO.ParameterDTO> extractParametersByType(List<Parameter> parameters, String type) {
        if (parameters == null) {
            return new ArrayList<>();
        }

        return parameters.stream()
                .filter(p -> type.equals(p.getIn()))
                .map(p -> ParsedEndpointDTO.ParameterDTO.builder()
                        .name(p.getName())
                        .type(p.getSchema() != null ? p.getSchema().getType() : "string")
                        .required(p.getRequired() != null && p.getRequired())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Extract request schema from operation
     */
    private String extractRequestSchema(Operation operation) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return null;
        }

        Content content = operation.getRequestBody().getContent();
        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            // Try first available content type
            mediaType = content.values().stream().findFirst().orElse(null);
        }

        if (mediaType != null && mediaType.getSchema() != null) {
            return gson.toJson(mediaType.getSchema());
        }

        return null;
    }

    /**
     * Extract response schema (for 200 response)
     */
    private String extractResponseSchema(Operation operation) {
        if (operation.getResponses() == null) {
            return null;
        }

        // Try 200, 201, then default
        ApiResponse response = operation.getResponses().get("200");
        if (response == null) {
            response = operation.getResponses().get("201");
        }
        if (response == null) {
            response = operation.getResponses().get("default");
        }

        if (response != null && response.getContent() != null) {
            MediaType mediaType = response.getContent().get("application/json");
            if (mediaType == null) {
                mediaType = response.getContent().values().stream().findFirst().orElse(null);
            }

            if (mediaType != null && mediaType.getSchema() != null) {
                return gson.toJson(mediaType.getSchema());
            }
        }

        return null;
    }

    /**
     * Extract security requirements
     */
    private List<String> extractSecurityRequirements(Operation operation) {
        if (operation.getSecurity() == null) {
            return new ArrayList<>();
        }

        return operation.getSecurity().stream()
                .flatMap(securityRequirement -> securityRequirement.keySet().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Extract all schemas from OpenAPI spec
     */
    public List<ParsedSchemaDTO> extractSchemas(OpenAPI openAPI) {
        log.info("Extracting schemas from OpenAPI spec");

        List<ParsedSchemaDTO> schemas = new ArrayList<>();

        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            log.warn("No schemas found in OpenAPI spec");
            return schemas;
        }

        openAPI.getComponents().getSchemas().forEach((name, schema) -> {
            ParsedSchemaDTO schemaDTO = ParsedSchemaDTO.builder()
                    .schemaName(name)
                    .schemaType(schema.getType())
                    .description(schema.getDescription())
                    .schemaDefinition(gson.toJson(schema))
                    .isEnum(schema.getEnum() != null && !schema.getEnum().isEmpty())
                    .enumValues(schema.getEnum() != null ?
                            (List<String>) schema.getEnum().stream()
                                    .map(Object::toString)
                                    .collect(Collectors.toList()) :
                            new ArrayList<>())
                    .build();

            schemas.add(schemaDTO);
        });

        log.info("Extracted {} schemas", schemas.size());
        return schemas;
    }

    // ========== HELPER METHODS ==========

    private int countEndpoints(OpenAPI openAPI) {
        if (openAPI.getPaths() == null) {
            return 0;
        }

        return openAPI.getPaths().values().stream()
                .mapToInt(pathItem -> getAllOperations(pathItem).size())
                .sum();
    }

    private int countSchemas(OpenAPI openAPI) {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return 0;
        }

        return openAPI.getComponents().getSchemas().size();
    }

    private List<Operation> getAllOperations(PathItem pathItem) {
        List<Operation> operations = new ArrayList<>();

        if (pathItem.getGet() != null) operations.add(pathItem.getGet());
        if (pathItem.getPost() != null) operations.add(pathItem.getPost());
        if (pathItem.getPut() != null) operations.add(pathItem.getPut());
        if (pathItem.getDelete() != null) operations.add(pathItem.getDelete());
        if (pathItem.getPatch() != null) operations.add(pathItem.getPatch());
        if (pathItem.getHead() != null) operations.add(pathItem.getHead());
        if (pathItem.getOptions() != null) operations.add(pathItem.getOptions());

        return operations;
    }

    /**
     * Validate OpenAPI specification
     */
    public void validateSpecification(String content, String format) {
        log.info("Validating OpenAPI specification");

        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Specification content cannot be empty");
        }

        if (!"JSON".equalsIgnoreCase(format) && !"YAML".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("Invalid format: must be JSON or YAML");
        }

        // Try parsing - will throw exception if invalid
        OpenAPI openAPI = parseSpecification(content, format);

        // Additional validation
        if (openAPI.getInfo() == null) {
            throw new IllegalArgumentException("OpenAPI spec must have info section");
        }

        if (openAPI.getInfo().getTitle() == null || openAPI.getInfo().getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("OpenAPI spec must have a title");
        }

        if (openAPI.getInfo().getVersion() == null || openAPI.getInfo().getVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("OpenAPI spec must have a version");
        }

        log.info("OpenAPI specification is valid");
    }

    /**
     * Detect format (JSON or YAML) from content
     */
    public String detectFormat(String content) {
        content = content.trim();

        // JSON starts with { or [
        if (content.startsWith("{") || content.startsWith("[")) {
            return "JSON";
        }

        // YAML typically starts with --- or contains : without {}
        if (content.startsWith("---") || content.startsWith("openapi:")) {
            return "YAML";
        }

        // Try to detect by structure
        if (content.contains("{") && content.contains("}")) {
            return "JSON";
        }

        // Default to YAML (more common for OpenAPI)
        return "YAML";
    }
}