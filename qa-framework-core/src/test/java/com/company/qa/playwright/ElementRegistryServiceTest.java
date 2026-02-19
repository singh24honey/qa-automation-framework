package com.company.qa.service.playwright;

import com.company.qa.model.playwright.ElementLocator;
import com.company.qa.model.playwright.PageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ElementRegistryService.
 */
class ElementRegistryServiceTest {

    private ElementRegistryService registryService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
      //  registryService = new ElementRegistryService(objectMapper,);
        registryService.init();
    }

    @Test
    @DisplayName("Should load registry from JSON file")
    void loadRegistry_Success() {
        // Verify registry loaded
        Map<String, Object> stats = registryService.getStatistics();

        assertThat(stats.get("pageCount")).isNotNull();
        assertThat((Integer) stats.get("pageCount")).isGreaterThan(0);
        assertThat(stats.get("totalElements")).isNotNull();
    }

    @Test
    @DisplayName("Should get element by page and name")
    void getElement_Found() {
        // When
        Optional<ElementLocator> element = registryService.getElement("login", "emailInput");

        // Then
        assertThat(element).isPresent();
        assertThat(element.get().getElementName()).isEqualTo("emailInput");
        assertThat(element.get().getStrategy()).isEqualTo("label");
        assertThat(element.get().getValue()).isEqualTo("Email");
        assertThat(element.get().getPlaywrightCode()).contains("getByLabel");
    }

    @Test
    @DisplayName("Should return empty when element not found")
    void getElement_NotFound() {
        // When
        Optional<ElementLocator> element = registryService.getElement("nonexistent", "element");

        // Then
        assertThat(element).isEmpty();
    }

    @Test
    @DisplayName("Should get all elements for a page")
    void getPageElements_Success() {
        // When
        List<ElementLocator> elements = registryService.getPageElements("login");

        // Then
        assertThat(elements).isNotEmpty();
        assertThat(elements).extracting(ElementLocator::getPageName)
                .containsOnly("login");
    }

    @Test
    @DisplayName("Should get page information")
    void getPage_Success() {
        // When
        Optional<PageInfo> page = registryService.getPage("login");

        // Then
        assertThat(page).isPresent();
        assertThat(page.get().getPageName()).isEqualTo("login");
        assertThat(page.get().getUrl()).isEqualTo("/login");
        assertThat(page.get().getElements()).isNotEmpty();
    }

    @Test
    @DisplayName("Should search elements by keyword")
    void searchElements_ByElementName() {
        // When
        List<ElementLocator> results = registryService.searchElements("email");

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(ElementLocator::getElementName)
                .anyMatch(name -> name.toLowerCase().contains("email"));
    }

    @Test
    @DisplayName("Should search elements by description")
    void searchElements_ByDescription() {
        // When
        List<ElementLocator> results = registryService.searchElements("input");

        // Then
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("Should return empty list for empty search")
    void searchElements_EmptyKeyword() {
        // When
        List<ElementLocator> results = registryService.searchElements("");

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should generate AI context for specific pages")
    void getContextForAIPrompt_SpecificPages() {
        // When
        String context = registryService.getContextForAIPrompt(Arrays.asList("login", "dashboard"));

        // Then
        assertThat(context).contains("=== Available Elements");
        assertThat(context).contains("login");
        assertThat(context).contains("dashboard");
        assertThat(context).contains("emailInput");
        assertThat(context).contains("getByLabel");
    }

    @Test
    @DisplayName("Should get fallback locators")
    void getFallbackLocators_Success() {
        // When
        List<String> fallbacks = registryService.getFallbackLocators("login", "emailInput");

        // Then
        assertThat(fallbacks).isNotEmpty();
        assertThat(fallbacks).anyMatch(fb -> fb.contains("getByPlaceholder") || fb.contains("locator"));
    }

    @Test
    @DisplayName("Should return empty list for non-existent fallbacks")
    void getFallbackLocators_NotFound() {
        // When
        List<String> fallbacks = registryService.getFallbackLocators("nonexistent", "element");

        // Then
        assertThat(fallbacks).isEmpty();
    }

    @Test
    @DisplayName("Should get registry statistics")
    void getStatistics_Success() {
        // When
        Map<String, Object> stats = registryService.getStatistics();

        // Then
        assertThat(stats).containsKeys("version", "pageCount", "totalElements", "defaultStrategy");
        assertThat(stats.get("version")).isEqualTo("1.0");
        assertThat(stats.get("defaultStrategy")).isEqualTo("role");

        @SuppressWarnings("unchecked")
        Map<String, Long> strategyBreakdown = (Map<String, Long>) stats.get("strategyBreakdown");
        assertThat(strategyBreakdown).isNotEmpty();
    }

    @Test
    @DisplayName("Should get all pages")
    void getAllPages_Success() {
        // When
        List<PageInfo> pages = registryService.getAllPages();

        // Then
        assertThat(pages).isNotEmpty();
        assertThat(pages).extracting(PageInfo::getPageName)
                .contains("login", "dashboard");
    }

    @Test
    @DisplayName("Should reload registry")
    void reloadRegistry_Success() {
        // Given
        int initialCount = registryService.getAllPages().size();

        // When
        registryService.reloadRegistry();

        // Then
        int reloadedCount = registryService.getAllPages().size();
        assertThat(reloadedCount).isEqualTo(initialCount);
    }
}