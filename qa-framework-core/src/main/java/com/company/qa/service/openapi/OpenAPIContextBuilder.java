package com.company.qa.service.openapi;

import com.company.qa.model.entity.ApiEndpoint;
import com.company.qa.model.entity.ApiSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FIXED: Builds clean, concise context for AI test generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAPIContextBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Build enhanced prompt with endpoint context - FIXED VERSION
     */
    public String buildEndpointTestPrompt(ApiEndpoint endpoint, String userPrompt) {
        StringBuilder prompt = new StringBuilder();

        // User's original request
        prompt.append(userPrompt).append("\n\n");

        // Endpoint context
        prompt.append("=== API Endpoint Context ===\n");
        prompt.append("Method: ").append(endpoint.getMethod()).append("\n");
        prompt.append("Path: ").append(endpoint.getPath()).append("\n");
        prompt.append("Summary: ").append(endpoint.getSummary() != null ? endpoint.getSummary() : "N/A").append("\n");

        if (endpoint.getDescription() != null && !endpoint.getDescription().isEmpty()) {
            prompt.append("Description: ").append(endpoint.getDescription()).append("\n");
        }

        prompt.append("\n");

        // Request body schema (CLEANED)
        if (endpoint.getRequestSchema() != null && !endpoint.getRequestSchema().isEmpty()) {
            prompt.append("=== Request Body ===\n");
            String cleanedRequest = cleanSchemaJson(endpoint.getRequestSchema());
            prompt.append(cleanedRequest).append("\n\n");
        }

        // Path parameters
        if (endpoint.getPathParameters() != null && !endpoint.getPathParameters().isEmpty()) {
            prompt.append("=== Path Parameters ===\n");
            String cleanedPath = cleanParametersJson(endpoint.getPathParameters());
            prompt.append(cleanedPath).append("\n\n");
        }

        // Query parameters
        if (endpoint.getQueryParameters() != null && !endpoint.getQueryParameters().isEmpty()) {
            prompt.append("=== Query Parameters ===\n");
            String cleanedQuery = cleanParametersJson(endpoint.getQueryParameters());
            prompt.append(cleanedQuery).append("\n\n");
        }

        // Expected response (CLEANED)
        if (endpoint.getResponseSchema() != null && !endpoint.getResponseSchema().isEmpty()) {
            prompt.append("=== Expected Response ===\n");
            String cleanedResponse = cleanSchemaJson(endpoint.getResponseSchema());
            prompt.append(cleanedResponse).append("\n\n");
        }

        // Test requirements
        prompt.append("=== Test Requirements ===\n");
        //prompt.append("- Use Selenium WebDriver with Java\n");
        prompt.append("- Use Selenium WebDriver with Java\n");
        prompt.append("- Use Cucumber Gherkin for feature file\n");
        prompt.append("- Include proper assertions\n");
        prompt.append("- Add error handling\n");
        prompt.append("- Use explicit waits\n");
        prompt.append("- Follow Page Object Model pattern\n");

        String finalPrompt = prompt.toString();
        log.info("Built prompt with {} characters", finalPrompt.length());

        return finalPrompt;
    }

    /**
     * Clean schema JSON - remove null fields and internal metadata
     */
    private String cleanSchemaJson(String rawSchemaJson) {
        if (rawSchemaJson == null || rawSchemaJson.isEmpty()) {
            return "N/A";
        }

        try {
            JsonNode schemaNode = objectMapper.readTree(rawSchemaJson);
            Map<String, Object> cleanSchema = buildCleanSchema(schemaNode);
            return objectMapper
                    .writer()
                    .with(new com.fasterxml.jackson.core.util.DefaultPrettyPrinter())
                    .writeValueAsString(cleanSchema);
        } catch (Exception e) {
            log.warn("Failed to clean schema JSON, using simplified version", e);
            return extractSchemaEssentials(rawSchemaJson);
        }
    }

    /**
     * Build clean schema object with only relevant fields
     */
    private Map<String, Object> buildCleanSchema(JsonNode schemaNode) {
        Map<String, Object> clean = new LinkedHashMap<>();

        // Type
        if (schemaNode.has("type") && !schemaNode.get("type").isNull()) {
            clean.put("type", schemaNode.get("type").asText());
        }

        // Required fields
        if (schemaNode.has("required") && schemaNode.get("required").isArray()) {
            List<String> required = new ArrayList<>();
            schemaNode.get("required").forEach(node -> required.add(node.asText()));
            if (!required.isEmpty()) {
                clean.put("required", required);
            }
        }

        // Properties (recursively clean)
        if (schemaNode.has("properties") && schemaNode.get("properties").isObject()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            schemaNode.get("properties").fields().forEachRemaining(entry -> {
                properties.put(entry.getKey(), buildCleanProperty(entry.getValue()));
            });
            clean.put("properties", properties);
        }

        // Description
        if (schemaNode.has("description") && !schemaNode.get("description").isNull()) {
            clean.put("description", schemaNode.get("description").asText());
        }

        // $ref (schema reference)
        if (schemaNode.has("$ref") && !schemaNode.get("$ref").isNull()) {
            clean.put("$ref", schemaNode.get("$ref").asText());
        }

        // Items (for arrays)
        if (schemaNode.has("items") && !schemaNode.get("items").isNull()) {
            clean.put("items", buildCleanSchema(schemaNode.get("items")));
        }

        // Enum values
        if (schemaNode.has("enum") && schemaNode.get("enum").isArray()) {
            List<String> enumValues = new ArrayList<>();
            schemaNode.get("enum").forEach(node -> enumValues.add(node.asText()));
            if (!enumValues.isEmpty()) {
                clean.put("enum", enumValues);
            }
        }

        return clean;
    }

    /**
     * Build clean property object
     */
    private Map<String, Object> buildCleanProperty(JsonNode propertyNode) {
        Map<String, Object> clean = new LinkedHashMap<>();

        // Type
        if (propertyNode.has("type") && !propertyNode.get("type").isNull()) {
            clean.put("type", propertyNode.get("type").asText());
        }

        // Format
        if (propertyNode.has("format") && !propertyNode.get("format").isNull()) {
            clean.put("format", propertyNode.get("format").asText());
        }

        // Description
        if (propertyNode.has("description") && !propertyNode.get("description").isNull()) {
            clean.put("description", propertyNode.get("description").asText());
        }

        // Min/Max length
        if (propertyNode.has("minLength") && !propertyNode.get("minLength").isNull()) {
            clean.put("minLength", propertyNode.get("minLength").asInt());
        }
        if (propertyNode.has("maxLength") && !propertyNode.get("maxLength").isNull()) {
            clean.put("maxLength", propertyNode.get("maxLength").asInt());
        }

        // Min/Max value
        if (propertyNode.has("minimum") && !propertyNode.get("minimum").isNull()) {
            clean.put("minimum", propertyNode.get("minimum").asDouble());
        }
        if (propertyNode.has("maximum") && !propertyNode.get("maximum").isNull()) {
            clean.put("maximum", propertyNode.get("maximum").asDouble());
        }

        // Pattern
        if (propertyNode.has("pattern") && !propertyNode.get("pattern").isNull()) {
            clean.put("pattern", propertyNode.get("pattern").asText());
        }

        // Example
        if (propertyNode.has("example") && !propertyNode.get("example").isNull()) {
            clean.put("example", propertyNode.get("example").asText());
        }

        // $ref
        if (propertyNode.has("$ref") && !propertyNode.get("$ref").isNull()) {
            clean.put("$ref", propertyNode.get("$ref").asText());
        }

        // Enum
        if (propertyNode.has("enum") && propertyNode.get("enum").isArray()) {
            List<String> enumValues = new ArrayList<>();
            propertyNode.get("enum").forEach(node -> enumValues.add(node.asText()));
            if (!enumValues.isEmpty()) {
                clean.put("enum", enumValues);
            }
        }

        // Items (for arrays)
        if (propertyNode.has("items") && !propertyNode.get("items").isNull()) {
            clean.put("items", buildCleanProperty(propertyNode.get("items")));
        }

        return clean;
    }

    /**
     * Fallback: Extract schema essentials as simple text
     */
    private String extractSchemaEssentials(String rawSchemaJson) {
        try {
            JsonNode node = objectMapper.readTree(rawSchemaJson);
            StringBuilder sb = new StringBuilder();

            sb.append("Type: ").append(node.has("type") ? node.get("type").asText() : "object").append("\n");

            if (node.has("required") && node.get("required").isArray()) {
                sb.append("Required: ");
                List<String> required = new ArrayList<>();
                node.get("required").forEach(n -> required.add(n.asText()));
                sb.append(String.join(", ", required)).append("\n");
            }

            if (node.has("properties") && node.get("properties").isObject()) {
                sb.append("Properties:\n");
                node.get("properties").fields().forEachRemaining(entry -> {
                    JsonNode prop = entry.getValue();
                    String type = prop.has("type") ? prop.get("type").asText() : "any";
                    String format = prop.has("format") ? " (" + prop.get("format").asText() + ")" : "";
                    sb.append("  - ").append(entry.getKey()).append(": ").append(type).append(format).append("\n");
                });
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to extract schema essentials", e);
            return "Schema details not available";
        }
    }

    /**
     * Clean parameters JSON
     */
    private String cleanParametersJson(String rawParametersJson) {
        if (rawParametersJson == null || rawParametersJson.isEmpty()) {
            return "N/A";
        }

        try {
            JsonNode paramsNode = objectMapper.readTree(rawParametersJson);

            if (paramsNode.isArray()) {
                List<Map<String, Object>> cleanParams = new ArrayList<>();
                paramsNode.forEach(param -> {
                    Map<String, Object> clean = new LinkedHashMap<>();
                    if (param.has("name")) clean.put("name", param.get("name").asText());
                    if (param.has("type")) clean.put("type", param.get("type").asText());
                    if (param.has("required")) clean.put("required", param.get("required").asBoolean());
                    if (param.has("description") && !param.get("description").isNull()) {
                        clean.put("description", param.get("description").asText());
                    }
                    cleanParams.add(clean);
                });

                return objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(cleanParams);
            }

            return rawParametersJson;
        } catch (Exception e) {
            log.warn("Failed to clean parameters JSON", e);
            return rawParametersJson;
        }
    }

    /**
     * Extract schema names referenced in endpoint
     */
    public List<String> extractReferencedSchemas(ApiEndpoint endpoint) {
        Set<String> schemaNames = new HashSet<>();

        // Extract from request schema
        if (endpoint.getRequestSchema() != null) {
            schemaNames.addAll(extractSchemaReferences(endpoint.getRequestSchema()));
        }

        // Extract from response schema
        if (endpoint.getResponseSchema() != null) {
            schemaNames.addAll(extractSchemaReferences(endpoint.getResponseSchema()));
        }

        return new ArrayList<>(schemaNames);
    }

    /**
     * Extract schema references ($ref) from JSON
     */
    private List<String> extractSchemaReferences(String schemaJson) {
        List<String> refs = new ArrayList<>();

        try {
            JsonNode node = objectMapper.readTree(schemaJson);
            extractRefsRecursive(node, refs);
        } catch (Exception e) {
            log.warn("Failed to extract schema references", e);
        }

        return refs;
    }

    /**
     * Recursively extract $ref values
     */
    private void extractRefsRecursive(JsonNode node, List<String> refs) {
        if (node == null) return;

        if (node.has("$ref")) {
            String ref = node.get("$ref").asText();
            // Extract schema name from #/components/schemas/SchemaName
            if (ref.contains("/")) {
                String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                refs.add(schemaName);
            }
        }

        // Recurse through object properties
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    extractRefsRecursive(entry.getValue(), refs));
        }

        // Recurse through arrays
        if (node.isArray()) {
            node.forEach(child -> extractRefsRecursive(child, refs));
        }
    }

    /**
     * Estimate token count for prompt
     */
    public int estimateTokens(String prompt) {
        // Rough estimate: ~4 chars per token
        return prompt.length() / 4;
    }
}