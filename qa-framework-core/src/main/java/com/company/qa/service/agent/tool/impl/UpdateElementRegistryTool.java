package com.company.qa.service.agent.tool.impl;

import com.company.qa.config.PlaywrightProperties;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.playwright.ElementRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final PlaywrightProperties playwrightProperties;  // already a Spring bean


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


        Path path = Paths.get(playwrightProperties.getRegistryPath());
       // Path path = Paths.get(registryFilePath);

        // Ensure parent directory exists
        Files.createDirectories(path.getParent());

        // If the external file doesn't exist yet, seed it from classpath
        if (!Files.exists(path)) {
            ClassPathResource seed = new ClassPathResource("element-registry-saucedemo.json");
            if (seed.exists()) {
                try (InputStream is = seed.getInputStream()) {
                    Files.copy(is, path);
                }
                log.info("📋 Seeded element registry from classpath to: {}", path);
            } else {
                // No classpath seed — create minimal empty registry
                String empty = """
                {
                  "version": "1.0",
                  "application": "Sauce Demo",
                  "baseUrl": "https://www.saucedemo.com",
                  "lastUpdated": "%s",
                  "pages": {}
                }
                """.formatted(Instant.now().toString());
                Files.writeString(path, empty);
                log.info("📋 Created empty element registry at: {}", path);
            }
        }

        return path;
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
     *
     * Uses safe node resolution throughout — never casts .path() results directly,
     * because the existing registry may have arrays or other unexpected node types
     * at any level, causing ClassCastException at runtime.
     */
    private JsonNode updateRegistryJson(JsonNode root, String pageName, String elementName,
                                        String workingLocator, String brokenLocator,
                                        String strategy, String discoveredBy) {

        ObjectNode mutableRoot = toObjectNode(root, "root");
        mutableRoot.put("lastUpdated", Instant.now().toString());

        // Pages is now an ARRAY — find target page by name or create it
        JsonNode pagesRaw = mutableRoot.get("pages");
        ArrayNode pagesArray;
        if (pagesRaw instanceof ArrayNode an) {
            pagesArray = an;
        } else {
            // Legacy object format or missing — create fresh array
            pagesArray = objectMapper.createArrayNode();
            mutableRoot.set("pages", pagesArray);
        }

        // Find existing page entry by name
        ObjectNode targetPage = null;
        for (JsonNode p : pagesArray) {
            if (pageName.equals(p.path("name").asText())) {
                targetPage = (ObjectNode) p;
                break;
            }
        }
        if (targetPage == null) {
            targetPage = objectMapper.createObjectNode();
            targetPage.put("name", pageName);
            targetPage.put("url", "/" + pageName);
            targetPage.set("elements", objectMapper.createArrayNode());
            pagesArray.add(targetPage);
        }

        // Elements is an ARRAY inside the page
        JsonNode elementsRaw = targetPage.get("elements");
        ArrayNode elementsArray;
        if (elementsRaw instanceof ArrayNode an) {
            elementsArray = an;
        } else {
            elementsArray = objectMapper.createArrayNode();
            targetPage.set("elements", elementsArray);
        }

        // DEDUP CHECK — skip if we already have this exact working→broken mapping
        for (JsonNode el : elementsArray) {
            if (workingLocator.equals(el.path("primarySelector").asText())
                    && brokenLocator != null
                    && brokenLocator.equals(el.path("replacedLocator").asText())) {
                log.info("⏭️  Skipping duplicate registry entry — already healed {} → {}",
                        brokenLocator, workingLocator);
                return mutableRoot;
            }
        }

        // Build new element entry in unified format
        ObjectNode element = objectMapper.createObjectNode();
        element.put("name", deriveSemanticName(workingLocator, brokenLocator, strategy));
        element.put("primarySelector", toStrategyValueFormat(workingLocator, strategy));
        element.put("playwrightCode", toPlaywrightApiCall(workingLocator, strategy));
        element.put("description", "AI-discovered replacement for: " + brokenLocator);
        element.put("replacedLocator", brokenLocator != null ? brokenLocator : "");
        element.put("discoveredBy", discoveredBy);
        element.put("discoveredAt", Instant.now().toString());

        // Keep broken locator as fallback — may heal in future UI reverts
        ArrayNode fallbacks = objectMapper.createArrayNode();
        if (brokenLocator != null && !brokenLocator.isBlank()) {
            fallbacks.add(toPlaywrightApiCall(brokenLocator, strategy));
        }
        element.set("fallbacks", fallbacks);

        elementsArray.add(element);

        return mutableRoot;
    }

// NEW helpers needed alongside this method:

    private String toStrategyValueFormat(String locator, String strategy) {
        String val = extractLocatorValue(locator);
        return switch (strategy.toLowerCase()) {
            case "testid" -> "testid=" + val;
            case "role"   -> "role=" + val;
            case "label"  -> "label=" + val;
            case "text"   -> "text=" + val;
            case "css"    -> locator.startsWith("css=") ? locator : "css=" + locator;
            case "xpath"  -> locator.startsWith("xpath=") ? locator : "xpath=" + locator;
            default       -> "css=" + locator;
        };
    }

    private String toPlaywrightApiCall(String locator, String strategy) {
        return switch (strategy.toLowerCase()) {
            case "testid" -> "page.getByTestId(\"" + extractLocatorValue(locator) + "\")";
            case "role"   -> "page.getByRole(AriaRole." + extractLocatorValue(locator).toUpperCase() + ")";
            case "label"  -> "page.getByLabel(\"" + extractLocatorValue(locator) + "\")";
            case "text"   -> "page.getByText(\"" + extractLocatorValue(locator) + "\")";
            case "css"    -> "page.locator(\"" + locator + "\")";
            case "xpath"  -> "page.locator(\"" + locator + "\")";
            default       -> "page.locator(\"" + locator + "\")";
        };
    }

    private String deriveSemanticName(String workingLocator, String brokenLocator, String strategy) {
        String val = extractLocatorValue(workingLocator)
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return val + "_healed";
    }

    /**
     * Safely coerce a JsonNode to ObjectNode.
     *
     * If the node is already an ObjectNode, returns it as-is.
     * If it is missing, null, or any other type (e.g. ArrayNode), returns a
     * fresh empty ObjectNode — the caller is responsible for re-attaching it.
     */
    private ObjectNode toObjectNode(JsonNode node, String label) {
        if (node instanceof ObjectNode on) {
            return on;
        }
        if (node == null || node.isMissingNode() || node.isNull()) {
            log.debug("Registry node '{}' is missing/null — creating empty ObjectNode", label);
        } else {
            log.warn("Registry node '{}' is {} not ObjectNode — replacing with empty ObjectNode",
                    label, node.getNodeType());
        }
        return objectMapper.createObjectNode();
    }

    /**
     * Get the child ObjectNode at {@code fieldName} inside {@code parent},
     * creating and attaching a fresh one if absent or of the wrong type.
     */
    private ObjectNode getOrCreateObjectNode(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.get(fieldName);
        if (existing instanceof ObjectNode on) {
            return on;
        }
        // Missing, null, or wrong type (e.g. ArrayNode) — replace with object
        if (existing != null && !existing.isMissingNode() && !existing.isNull()) {
            log.warn("Registry field '{}' was {} — replacing with ObjectNode",
                    fieldName, existing.getNodeType());
        }
        ObjectNode fresh = objectMapper.createObjectNode();
        parent.set(fieldName, fresh);
        return fresh;
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