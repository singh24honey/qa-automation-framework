package com.company.qa.service.openapi;

import com.company.qa.model.dto.OpenAPISpecDTO;
import com.company.qa.model.dto.ParsedEndpointDTO;
import com.company.qa.model.dto.ParsedSchemaDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for parsing OpenAPI/Swagger specifications
 * FIXED: Using Jackson instead of Gson for Java 17+ compatibility
 */
@Slf4j
@Service
public class OpenAPIParserService {

    // ✅ USE JACKSON FOR ALL SERIALIZATION
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Parse OpenAPI specification from string content
     */
    public OpenAPI parseSpecification(String content, String format) {
        log.info("Parsing OpenAPI specification (format: {})", format);

        try {
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
                .tags(new ArrayList<>(tags))
                .endpointCount(countEndpoints(openAPI))
                .schemaCount(countSchemas(openAPI))
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
            List<Operation> operations = getAllOperations(pathItem);
            operations.forEach(operation -> {
                endpoints.add(extractEndpoint(path, operation, pathItem));
            });
        });

        log.info("Extracted {} endpoints", endpoints.size());
        return endpoints;
    }

    /**
     * Extract single endpoint
     */
    private ParsedEndpointDTO extractEndpoint(String path, Operation operation, PathItem pathItem) {
        String method = getMethodFromOperation(pathItem, operation);

        List<ParsedEndpointDTO.ParameterDTO> pathParams = extractParametersByType(
                operation.getParameters() != null ? operation.getParameters() : pathItem.getParameters(),
                "path");
        List<ParsedEndpointDTO.ParameterDTO> queryParams = extractParametersByType(
                operation.getParameters() != null ? operation.getParameters() : pathItem.getParameters(),
                "query");
        List<ParsedEndpointDTO.ParameterDTO> headerParams = extractParametersByType(
                operation.getParameters() != null ? operation.getParameters() : pathItem.getParameters(),
                "header");

        String requestSchema = extractRequestSchema(operation);
        String responseSchema = extractResponseSchema(operation);

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
                .isDeprecated(operation.getDeprecated() != null && operation.getDeprecated())
                .build();
    }

    /**
     * Extract request schema from operation - FIXED WITH JACKSON
     */
    private String extractRequestSchema(Operation operation) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return null;
        }

        Content content = operation.getRequestBody().getContent();
        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = content.values().stream().findFirst().orElse(null);
        }

        if (mediaType != null && mediaType.getSchema() != null) {
            try {
                // ✅ USE JACKSON
                return jsonMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(mediaType.getSchema());
            } catch (Exception e) {
                log.error("Failed to serialize request schema", e);
                return null;
            }
        }

        return null;
    }

    /**
     * Extract response schema - FIXED WITH JACKSON
     */
    private String extractResponseSchema(Operation operation) {
        if (operation.getResponses() == null) {
            return null;
        }

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
                try {
                    // ✅ USE JACKSON
                    return jsonMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(mediaType.getSchema());
                } catch (Exception e) {
                    log.error("Failed to serialize response schema", e);
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Extract all schemas - FIXED WITH JACKSON
     */
    public List<ParsedSchemaDTO> extractSchemas(OpenAPI openAPI) {
        log.info("Extracting schemas from OpenAPI spec");

        List<ParsedSchemaDTO> schemas = new ArrayList<>();

        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            log.warn("No schemas found in OpenAPI spec");
            return schemas;
        }

        openAPI.getComponents().getSchemas().forEach((name, schema) -> {
            try {
                // ✅ USE JACKSON
                String schemaJson = jsonMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(schema);

                ParsedSchemaDTO schemaDTO = ParsedSchemaDTO.builder()
                        .schemaName(name)
                        .schemaType(schema.getType())
                        .description(schema.getDescription())
                        .schemaDefinition(schemaJson)
                        .isEnum(schema.getEnum() != null && !schema.getEnum().isEmpty())
                        .enumValues(schema.getEnum() != null ?
                                (List<String>) schema.getEnum().stream()
                                        .map(Object::toString)
                                        .collect(Collectors.toList()) :
                                new ArrayList<>())
                        .build();

                schemas.add(schemaDTO);
            } catch (Exception e) {
                log.error("Failed to serialize schema: {}", name, e);
            }
        });

        log.info("Extracted {} schemas", schemas.size());
        return schemas;
    }

    // Helper methods remain the same...

    private List<ParsedEndpointDTO.ParameterDTO> extractParametersByType(
            List<Parameter> parameters, String type) {
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

    private String getMethodFromOperation(PathItem pathItem, Operation operation) {
        if (pathItem.getGet() == operation) return "GET";
        if (pathItem.getPost() == operation) return "POST";
        if (pathItem.getPut() == operation) return "PUT";
        if (pathItem.getDelete() == operation) return "DELETE";
        if (pathItem.getPatch() == operation) return "PATCH";
        if (pathItem.getHead() == operation) return "HEAD";
        if (pathItem.getOptions() == operation) return "OPTIONS";
        return "UNKNOWN";
    }

    public String detectFormat(String content) {
        content = content.trim();

        if (content.startsWith("{") || content.startsWith("[")) {
            return "JSON";
        }

        if (content.startsWith("---") || content.startsWith("openapi:")) {
            return "YAML";
        }

        if (content.contains("{") && content.contains("}")) {
            return "JSON";
        }

        return "YAML";
    }

    public void validateSpecification(String content, String format) {
        log.info("Validating OpenAPI specification");

        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Specification content cannot be empty");
        }

        OpenAPI openAPI = parseSpecification(content, format);

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
}