package com.company.qa.controller;

import com.company.qa.model.playwright.ElementLocator;
import com.company.qa.model.playwright.PageInfo;
import com.company.qa.service.playwright.ElementRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for Element Registry.
 *
 * Provides endpoints to query available elements and locators.
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Element Registry", description = "Playwright Element Registry API")
public class ElementRegistryController {

    private final ElementRegistryService registryService;

    @GetMapping("/elements")
    @Operation(summary = "Get all elements across all pages")
    public ResponseEntity<Map<String, Object>> getAllElements() {
        log.info("REST API: Getting all elements");

        List<PageInfo> pages = registryService.getAllPages();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("pageCount", pages.size());
        response.put("pages", pages);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/elements/search")
    @Operation(summary = "Search elements by keyword")
    public ResponseEntity<Map<String, Object>> searchElements(
            @RequestParam(required = true) String q) {

        log.info("REST API: Searching elements with keyword: {}", q);

        List<ElementLocator> results = registryService.searchElements(q);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("query", q);
        response.put("resultCount", results.size());
        response.put("results", results);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/pages")
    @Operation(summary = "Get all pages")
    public ResponseEntity<Map<String, Object>> getAllPages() {
        log.info("REST API: Getting all pages");

        List<PageInfo> pages = registryService.getAllPages();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", pages.size());
        response.put("pages", pages);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/pages/{pageName}")
    @Operation(summary = "Get specific page with all its elements")
    public ResponseEntity<Map<String, Object>> getPage(
            @PathVariable String pageName) {

        log.info("REST API: Getting page: {}", pageName);

        return registryService.getPage(pageName)
                .map(page -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("page", page);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("error", "Page not found: " + pageName);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping("/pages/{pageName}/elements")
    @Operation(summary = "Get all elements for a specific page")
    public ResponseEntity<Map<String, Object>> getPageElements(
            @PathVariable String pageName) {

        log.info("REST API: Getting elements for page: {}", pageName);

        List<ElementLocator> elements = registryService.getPageElements(pageName);

        if (elements.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Page not found or has no elements: " + pageName);
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("pageName", pageName);
        response.put("elementCount", elements.size());
        response.put("elements", elements);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/pages/{pageName}/elements/{elementName}")
    @Operation(summary = "Get specific element")
    public ResponseEntity<Map<String, Object>> getElement(
            @PathVariable String pageName,
            @PathVariable String elementName) {

        log.info("REST API: Getting element: {}.{}", pageName, elementName);

        return registryService.getElement(pageName, elementName)
                .map(element -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("element", element);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("error", "Element not found: " + pageName + "." + elementName);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping("/pages/{pageName}/elements/{elementName}/fallbacks")
    @Operation(summary = "Get fallback locators for self-healing")
    public ResponseEntity<Map<String, Object>> getFallbackLocators(
            @PathVariable String pageName,
            @PathVariable String elementName) {

        log.info("REST API: Getting fallbacks for: {}.{}", pageName, elementName);

        List<String> fallbacks = registryService.getFallbackLocators(pageName, elementName);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("pageName", pageName);
        response.put("elementName", elementName);
        response.put("fallbackCount", fallbacks.size());
        response.put("fallbacks", fallbacks);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ai-context")
    @Operation(summary = "Get AI prompt context for specific pages")
    public ResponseEntity<Map<String, Object>> getAIContext(
            @RequestParam(required = false) List<String> pages) {

        log.info("REST API: Getting AI context for pages: {}", pages);

        String context = registryService.getContextForAIPrompt(pages);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("pages", pages != null ? pages : "all");
        response.put("context", context);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reload")
    @Operation(summary = "Reload registry from JSON file")
    public ResponseEntity<Map<String, Object>> reloadRegistry() {
        log.info("REST API: Reloading Element Registry");

        registryService.reloadRegistry();

        Map<String, Object> stats = registryService.getStatistics();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Registry reloaded successfully");
        response.put("statistics", stats);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get registry statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.info("REST API: Getting registry statistics");

        Map<String, Object> stats = registryService.getStatistics();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statistics", stats);

        return ResponseEntity.ok(response);
    }
}