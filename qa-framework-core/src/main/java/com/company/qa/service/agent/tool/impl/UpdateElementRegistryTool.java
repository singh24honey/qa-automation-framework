package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.playwright.ElementRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool to update Element Registry with discovered locators.
 *
 * DAY 4 IMPLEMENTATION:
 * - Loads element-registry.json
 * - Updates with new working locator
 * - Marks old locator as deprecated
 * - Saves back to file
 * - Reloads ElementRegistryService
 *
 * Input parameters:
 * - pageName: Page where element is located
 * - elementName: Name of the element
 * - workingLocator: The locator that now works
 * - brokenLocator: The locator that was broken (mark as deprecated)
 * - strategy: Locator strategy (role, testid, css, etc.)
 * - discoveredBy: "registry" or "ai"
 *
 * Output:
 * - success: true/false
 * - updated: true if registry was updated
 * - message: Description of what was updated
 * - error: Error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateElementRegistryTool implements AgentTool {

    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;
    private final ElementRegistryService registryService;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.UPDATE_ELEMENT_REGISTRY;
    }

    @Override
    public String getName() {
        return "Element Registry Update Tool";
    }

    @Override
    public String getDescription() {
        return "Updates Element Registry with discovered working locators. " +
                "Marks broken locators as deprecated and promotes working alternatives. " +
                "Creates learning loop for future self-healing.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("📝 Updating Element Registry with discovered locator");

        try {
            String pageName = (String) parameters.get("pageName");
            String elementName = (String) parameters.get("elementName");
            String workingLocator = (String) parameters.get("workingLocator");
            String brokenLocator = (String) parameters.get("brokenLocator");
            String strategy = (String) parameters.getOrDefault("strategy", "css");
            String discoveredBy = (String) parameters.getOrDefault("discoveredBy", "agent");

            log.info("Updating registry:");
            log.info("  Page: {}", pageName);
            log.info("  Element: {}", elementName);
            log.info("  Working Locator: {}", workingLocator);
            log.info("  Broken Locator: {}", brokenLocator);

            // Load current registry
            Path registryPath = getRegistryFilePath();
            JsonNode root = loadRegistry(registryPath);

            // Update registry with new locator
            root = updateRegistryJson(root, pageName, elementName, workingLocator,
                    brokenLocator, strategy, discoveredBy);

            // Save back to file
            saveRegistry(registryPath, root);

            // Reload service to pick up changes
            registryService.loadRegistry();

            log.info("✅ Registry updated and reloaded successfully");

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("updated", true);
            result.put("message", String.format("Added %s locator for %s on %s page",
                    strategy, elementName, pageName));

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to update Element Registry: {}", e.getMessage(), e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return parameters != null &&
                parameters.containsKey("pageName") &&
                parameters.containsKey("elementName") &&
                parameters.containsKey("workingLocator");
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("pageName", "string (required) - Page name");
        schema.put("elementName", "string (required) - Element name");
        schema.put("workingLocator", "string (required) - Working locator to add");
        schema.put("brokenLocator", "string (optional) - Broken locator to deprecate");
        schema.put("strategy", "string (optional) - Locator strategy");
        schema.put("discoveredBy", "string (optional) - Discovery method");
        return schema;
    }

    /**
     * Get path to registry file.
     */
    private Path getRegistryFilePath() throws Exception {
        // Try to get from classpath
        ClassPathResource resource = new ClassPathResource("element-registry-saucedemo.json");

        if (resource.exists()) {
            // In JAR, extract to temp location for editing
            if (resource.getURL().toString().startsWith("jar:")) {
                Path tempFile = Files.createTempFile("element-registry", ".json");
                try (InputStream is = resource.getInputStream()) {
                    Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return tempFile;
            }

            // In development, use file system path
            return Paths.get(resource.getURI());
        }

        // If not found, create new file
        Path registryPath = Paths.get("src/main/resources/playwright/element-registry-saucedemo.json");
        if (!Files.exists(registryPath)) {
            // Create empty registry
            String emptyRegistry = """
                {
                  "version": "1.0",
                  "lastUpdated": "%s",
                  "defaultStrategy": "role",
                  "pages": {}
                }
                """.formatted(Instant.now().toString());
            Files.writeString(registryPath, emptyRegistry);
        }

        return registryPath;
    }

    /**
     * Load registry JSON.
     */
    private JsonNode loadRegistry(Path path) throws Exception {
        String content = Files.readString(path);
        return objectMapper.readTree(content);
    }

    /**
     * Update registry JSON with new locator.
     */
    private JsonNode updateRegistryJson(JsonNode root, String pageName, String elementName,
                                        String workingLocator, String brokenLocator,
                                        String strategy, String discoveredBy) {

        ObjectNode mutableRoot = (ObjectNode) root;

        // Update lastUpdated
        mutableRoot.put("lastUpdated", Instant.now().toString());

        // Get or create pages node
        ObjectNode pages = (ObjectNode) mutableRoot.path("pages");
        if (pages.isMissingNode()) {
            pages = objectMapper.createObjectNode();
            mutableRoot.set("pages", pages);
        }

        // Get or create page node
        ObjectNode page = (ObjectNode) pages.path(pageName);
        if (page.isMissingNode()) {
            page = objectMapper.createObjectNode();
            page.put("url", "/" + pageName);
            pages.set(pageName, page);
        }

        // Get or create elements node
        ObjectNode elements = (ObjectNode) page.path("elements");
        if (elements.isMissingNode()) {
            elements = objectMapper.createObjectNode();
            page.set("elements", elements);
        }

        // Create new element entry
        ObjectNode element = objectMapper.createObjectNode();
        element.put("strategy", strategy);
        element.put("value", extractLocatorValue(workingLocator));
        element.put("playwrightCode", workingLocator);
        element.put("discoveredBy", discoveredBy);
        element.put("discoveredAt", Instant.now().toString());

        // Add broken locator as deprecated alternative
        if (brokenLocator != null) {
            element.put("replacedLocator", brokenLocator);
        }

        elements.set(elementName, element);

        return mutableRoot;
    }

    /**
     * Extract locator value from Playwright code.
     */
    private String extractLocatorValue(String playwrightCode) {
        // Simple extraction - gets string between quotes
        int firstQuote = playwrightCode.indexOf('"');
        if (firstQuote == -1) {
            firstQuote = playwrightCode.indexOf("'");
        }

        if (firstQuote != -1) {
            int lastQuote = playwrightCode.lastIndexOf('"');
            if (lastQuote == -1) {
                lastQuote = playwrightCode.lastIndexOf("'");
            }

            if (lastQuote > firstQuote) {
                return playwrightCode.substring(firstQuote + 1, lastQuote);
            }
        }

        return playwrightCode;
    }

    /**
     * Save registry JSON back to file.
     */
    private void saveRegistry(Path path, JsonNode root) throws Exception {
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(root);

        Files.writeString(path, prettyJson,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        log.info("Registry saved to: {}", path);
    }
}