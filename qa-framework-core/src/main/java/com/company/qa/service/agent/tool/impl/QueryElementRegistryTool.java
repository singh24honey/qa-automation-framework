package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.playwright.ElementLocator;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.playwright.ElementRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool to query Element Registry for alternative locators.
 *
 * Searches registry for elements matching the broken locator's purpose.
 * Returns alternatives in priority order.
 *
 * Input parameters:
 * - pageName: Page where element is located
 * - elementPurpose: What the element does (e.g., "submit button")
 * - brokenLocator: The locator that failed (for comparison)
 *
 * Output:
 * - success: true/false
 * - alternatives: List of alternative locators
 * - totalFound: Number of alternatives found
 * - primaryRecommendation: Best alternative to try first
 * - error: Error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryElementRegistryTool implements AgentTool {

    private final ElementRegistryService registryService;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;

    // Priority order for locator strategies
    private static final List<String> STRATEGY_PRIORITY = List.of(
            "role",      // Most stable
            "label",
            "testid",
            "placeholder",
            "text",
            "css",       // Least stable
            "xpath"
    );

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.QUERY_ELEMENT_REGISTRY;
    }

    @Override
    public String getName() {
        return "Element Registry Query Tool";
    }

    @Override
    public String getDescription() {
        return "Queries Element Registry for alternative locators. " +
                "Searches by page name and element purpose. " +
                "Returns alternatives in priority order (role > label > testid > text > css).";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("🔍 Querying Element Registry for alternatives");

        try {
            String pageName = (String) parameters.get("pageName");
            String elementPurpose = (String) parameters.get("elementPurpose");
            String brokenLocator = (String) parameters.get("brokenLocator");

            // Search registry
            List<ElementLocator> matches = searchRegistry(pageName, elementPurpose);

            if (matches.isEmpty()) {
                log.warn("No alternatives found in registry for: {} on page: {}",
                        elementPurpose, pageName);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("alternatives", Collections.emptyList());
                result.put("totalFound", 0);
                result.put("primaryRecommendation", null);
                return result;
            }

            // Filter out the broken locator
            if (brokenLocator != null) {
                matches = matches.stream()
                        .filter(loc -> !loc.getPlaywrightCode().contains(brokenLocator))
                        .collect(Collectors.toList());
            }

            // Sort by priority
            List<ElementLocator> sorted = sortByPriority(matches);

            // Convert to simple format
            List<Map<String, Object>> alternatives = sorted.stream()
                    .map(this::toAlternativeFormat)
                    .collect(Collectors.toList());

            // Primary recommendation is first in sorted list
            Map<String, Object> primaryRecommendation = alternatives.isEmpty()
                    ? null
                    : alternatives.get(0);

            log.info("✅ Found {} alternatives for: {} on page: {}",
                    alternatives.size(), elementPurpose, pageName);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("alternatives", alternatives);
            result.put("totalFound", alternatives.size());
            result.put("primaryRecommendation", primaryRecommendation);

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to query Element Registry: {}", e.getMessage(), e);

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
                parameters.containsKey("elementPurpose");
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("pageName", "string (required) - Page name where element is located");
        schema.put("elementPurpose", "string (required) - Element purpose/description");
        schema.put("brokenLocator", "string (optional) - Broken locator to exclude from results");
        return schema;
    }

    /**
     * Search registry for matching elements.
     */
    private List<ElementLocator> searchRegistry(String pageName, String elementPurpose) {
        List<ElementLocator> matches = new ArrayList<>();

        // First, try to get elements from specific page
        if (!"unknown".equals(pageName)) {
            List<ElementLocator> pageElements = registryService.getPageElements(pageName);

            for (ElementLocator element : pageElements) {
                if (matchesElementPurpose(element, elementPurpose)) {
                    matches.add(element);
                }
            }
        }

        // If no matches, try keyword search across all pages
        if (matches.isEmpty()) {
            String[] keywords = elementPurpose.toLowerCase().split("\\s+");
            for (String keyword : keywords) {
                List<ElementLocator> searchResults = registryService.searchElements(keyword);
                for (ElementLocator result : searchResults) {
                    if (!matches.contains(result) && matchesElementPurpose(result, elementPurpose)) {
                        matches.add(result);
                    }
                }
            }
        }

        return matches;
    }

    /**
     * Check if element matches the purpose.
     */
    private boolean matchesElementPurpose(ElementLocator element, String purpose) {
        String lowerPurpose = purpose.toLowerCase();

        // Check element name
        if (element.getElementName() != null &&
                element.getElementName().toLowerCase().contains(lowerPurpose)) {
            return true;
        }

        // Check description
        if (element.getDescription() != null &&
                element.getDescription().toLowerCase().contains(lowerPurpose)) {
            return true;
        }

        // Check individual keywords
        String[] purposeWords = lowerPurpose.split("\\s+");
        for (String word : purposeWords) {
            if (word.length() > 3) { // Only meaningful words
                String elementNameLower = element.getElementName().toLowerCase();
                if (elementNameLower.contains(word)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Sort elements by locator strategy priority.
     */
    private List<ElementLocator> sortByPriority(List<ElementLocator> elements) {
        return elements.stream()
                .sorted((a, b) -> {
                    int priorityA = STRATEGY_PRIORITY.indexOf(a.getStrategy());
                    int priorityB = STRATEGY_PRIORITY.indexOf(b.getStrategy());

                    // If strategy not in priority list, put it last
                    if (priorityA == -1) priorityA = 999;
                    if (priorityB == -1) priorityB = 999;

                    return Integer.compare(priorityA, priorityB);
                })
                .collect(Collectors.toList());
    }

    /**
     * Convert ElementLocator to simple map format.
     */
    private Map<String, Object> toAlternativeFormat(ElementLocator element) {
        Map<String, Object> alternative = new HashMap<>();
        alternative.put("locator", element.getPlaywrightCode());
        alternative.put("strategy", element.getStrategy());
        alternative.put("elementName", element.getElementName());
        alternative.put("description", element.getDescription());
        alternative.put("priority", STRATEGY_PRIORITY.indexOf(element.getStrategy()));
        return alternative;
    }
}