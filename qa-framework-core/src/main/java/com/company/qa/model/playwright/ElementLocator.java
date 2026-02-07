package com.company.qa.model.playwright;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a Playwright element locator from the registry.
 *
 * Used by:
 * - AI test generation (provide context)
 * - Self-healing tests (fallback locators)
 * - Test maintenance (understand element strategies)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElementLocator {

    /**
     * Page name (e.g., "login", "dashboard")
     */
    private String pageName;

    /**
     * Element name (e.g., "emailInput", "submitButton")
     */
    private String elementName;

    /**
     * Locator strategy: role, label, testid, text, placeholder, css
     */
    private String strategy;

    /**
     * Locator value (depends on strategy)
     */
    private String value;

    /**
     * Additional options for the locator (e.g., {name: "Submit"} for role)
     */
    private Map<String, String> options;

    /**
     * Ready-to-use Playwright code
     * Example: "page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\"Submit\"))"
     */
    private String playwrightCode;

    /**
     * Human-readable description
     */
    private String description;

    /**
     * Fallback locators for self-healing
     */
    private List<String> fallbacks;

    /**
     * Page URL pattern
     */
    private String pageUrl;

    /**
     * Page Object class (if exists)
     */
    private String pageObjectClass;
}