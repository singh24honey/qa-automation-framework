package com.company.qa.service.playwright;

import com.company.qa.model.playwright.ElementLocator;
import com.company.qa.model.playwright.PageInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing Playwright Element Registry.
 *
 * Provides:
 * - Element lookup by page and name
 * - Search functionality
 * - Context for AI prompts
 * - Fallback locators for self-healing
 *
 * Used by:
 * - PlaywrightContextBuilder (AI prompt enhancement)
 * - SelfHealingAgent (find alternatives)
 * - FlakyTestAgent (understand elements)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ElementRegistryService {

    private final ObjectMapper objectMapper;

    private Map<String, PageInfo> registry = new HashMap<>();
    private String version;
    private String lastUpdated;
    private String defaultStrategy;

    @PostConstruct
    public void init() {
        loadRegistry();
    }

    /**
     * Load element registry from JSON file.
     */
    public void loadRegistry() {
        try {
            log.info("Loading Element Registry...");

            ClassPathResource resource = new ClassPathResource("element-registry-saucedemo.json");

            if (!resource.exists()) {
                log.warn("element-registry.json not found, creating empty registry");
                registry = new HashMap<>();
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);

                version = root.path("version").asText("1.0");
                lastUpdated = root.path("lastUpdated").asText();
                defaultStrategy = root.path("defaultStrategy").asText("role");

                JsonNode pagesNode = root.path("pages");

                pagesNode.fields().forEachRemaining(entry -> {
                    String pageName = entry.getKey();
                    JsonNode pageNode = entry.getValue();

                    PageInfo pageInfo = parsePage(pageName, pageNode);
                    registry.put(pageName, pageInfo);
                });

                log.info("Element Registry loaded: {} pages, {} total elements",
                        registry.size(), getTotalElementCount());

            }

        } catch (IOException e) {
            log.error("Failed to load Element Registry: {}", e.getMessage(), e);
            registry = new HashMap<>();
        }
    }

    /**
     * Parse page from JSON node.
     */
    private PageInfo parsePage(String pageName, JsonNode pageNode) {
        Map<String, ElementLocator> elements = new HashMap<>();

        JsonNode elementsNode = pageNode.path("elements");
        elementsNode.fields().forEachRemaining(entry -> {
            String elementName = entry.getKey();
            JsonNode elementNode = entry.getValue();

            ElementLocator locator = parseElement(pageName, elementName, elementNode);
            locator.setPageUrl(pageNode.path("url").asText());
            locator.setPageObjectClass(pageNode.path("pageObjectClass").asText(null));

            elements.put(elementName, locator);
        });

        return PageInfo.builder()
                .pageName(pageName)
                .url(pageNode.path("url").asText())
                .pageObjectClass(pageNode.path("pageObjectClass").asText(null))
                .description(pageNode.path("description").asText(null))
                .elements(elements)
                .build();
    }

    /**
     * Parse element from JSON node.
     */
    private ElementLocator parseElement(String pageName, String elementName, JsonNode elementNode) {
        // Parse options if present
        Map<String, String> options = new HashMap<>();
        JsonNode optionsNode = elementNode.path("options");
        if (!optionsNode.isMissingNode()) {
            optionsNode.fields().forEachRemaining(entry -> {
                options.put(entry.getKey(), entry.getValue().asText());
            });
        }

        // Parse fallbacks
        List<String> fallbacks = new ArrayList<>();
        JsonNode fallbacksNode = elementNode.path("fallbacks");
        if (fallbacksNode.isArray()) {
            fallbacksNode.forEach(node -> fallbacks.add(node.asText()));
        }

        return ElementLocator.builder()
                .pageName(pageName)
                .elementName(elementName)
                .strategy(elementNode.path("strategy").asText())
                .value(elementNode.path("value").asText())
                .options(options.isEmpty() ? null : options)
                .playwrightCode(elementNode.path("playwrightCode").asText())
                .description(elementNode.path("description").asText(null))
                .fallbacks(fallbacks.isEmpty() ? null : fallbacks)
                .build();
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Get specific element by page and name.
     */
    public Optional<ElementLocator> getElement(String pageName, String elementName) {
        PageInfo page = registry.get(pageName);
        if (page == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(page.getElements().get(elementName));
    }

    /**
     * Get all elements for a specific page.
     */
    public List<ElementLocator> getPageElements(String pageName) {
        PageInfo page = registry.get(pageName);
        if (page == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(page.getElements().values());
    }

    /**
     * Get page information.
     */
    public Optional<PageInfo> getPage(String pageName) {
        return Optional.ofNullable(registry.get(pageName));
    }

    /**
     * Get all pages.
     */
    public List<PageInfo> getAllPages() {
        return new ArrayList<>(registry.values());
    }

    /**
     * Search elements by keyword (searches in element name, description, page name).
     */
    public List<ElementLocator> searchElements(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String searchTerm = keyword.toLowerCase();

        return registry.values().stream()
                .flatMap(page -> page.getElements().values().stream())
                .filter(element -> matchesKeyword(element, searchTerm))
                .collect(Collectors.toList());
    }

    /**
     * Check if element matches keyword.
     */
    private boolean matchesKeyword(ElementLocator element, String keyword) {
        return element.getElementName().toLowerCase().contains(keyword)
                || element.getPageName().toLowerCase().contains(keyword)
                || (element.getDescription() != null && element.getDescription().toLowerCase().contains(keyword))
                || element.getValue().toLowerCase().contains(keyword);
    }

    /**
     * Get context for AI prompt - provides available locators for relevant pages.
     */
    public String getContextForAIPrompt(List<String> relevantPages) {
        if (relevantPages == null || relevantPages.isEmpty()) {
            return getFullRegistryContext();
        }

        StringBuilder context = new StringBuilder();
        context.append("=== Available Elements (from Element Registry) ===\n\n");

        for (String pageName : relevantPages) {
            PageInfo page = registry.get(pageName);
            if (page != null) {
                context.append(String.format("Page: %s (%s)\n", pageName, page.getUrl()));

                if (page.getPageObjectClass() != null) {
                    context.append(String.format("Page Object: %s\n", page.getPageObjectClass()));
                }

                context.append("Elements:\n");

                page.getElements().forEach((elementName, locator) -> {
                    context.append(String.format("  - %s: %s\n",
                            elementName, locator.getPlaywrightCode()));

                    if (locator.getDescription() != null) {
                        context.append(String.format("    (%s)\n", locator.getDescription()));
                    }
                });

                context.append("\n");
            }
        }

        return context.toString();
    }

    /**
     * Get full registry context (all pages).
     */
    private String getFullRegistryContext() {
        return getContextForAIPrompt(new ArrayList<>(registry.keySet()));
    }

    /**
     * Get fallback locators for self-healing.
     */
    public List<String> getFallbackLocators(String pageName, String elementName) {
        return getElement(pageName, elementName)
                .map(ElementLocator::getFallbacks)
                .orElse(Collections.emptyList());
    }

    /**
     * Reload registry from file.
     */
    public void reloadRegistry() {
        log.info("Reloading Element Registry...");
        loadRegistry();
    }

    /**
     * Get registry statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("version", version);
        stats.put("lastUpdated", lastUpdated);
        stats.put("pageCount", registry.size());
        stats.put("totalElements", getTotalElementCount());
        stats.put("defaultStrategy", defaultStrategy);

        // Strategy breakdown
        Map<String, Long> strategyCount = registry.values().stream()
                .flatMap(page -> page.getElements().values().stream())
                .collect(Collectors.groupingBy(ElementLocator::getStrategy, Collectors.counting()));
        stats.put("strategyBreakdown", strategyCount);

        return stats;
    }

    /**
     * Get total element count across all pages.
     */
    private int getTotalElementCount() {
        return registry.values().stream()
                .mapToInt(PageInfo::getElementCount)
                .sum();
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public String getVersion() {
        return version;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public String getDefaultStrategy() {
        return defaultStrategy;
    }
}