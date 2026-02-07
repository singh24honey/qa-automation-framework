package com.company.qa.service.playwright;

import com.company.qa.model.playwright.PageObjectInfo;
import com.company.qa.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PageObjectRegistryService.
 */
@DisplayName("Page Object Registry Service Tests")
class PageObjectRegistryServiceTest extends PostgresIntegrationTest {

    private PageObjectRegistryService registryService;

    @BeforeEach
    void setUp() {
        registryService = new PageObjectRegistryService();
        ReflectionTestUtils.setField(registryService, "scanPath", "src/test/java");
        ReflectionTestUtils.setField(registryService, "packagePattern", ".*\\.pages\\..*");

        registryService.scanPageObjects();
    }

    @Test
    @DisplayName("Should scan and find Page Objects")
    void scanPageObjects_FindsPageObjects() {
        // When
        List<PageObjectInfo> pageObjects = registryService.getAllPageObjects();

        // Then
        assertThat(pageObjects).isNotEmpty();
        assertThat(pageObjects).extracting(PageObjectInfo::getSimpleName)
                .contains("LoginPage", "DashboardPage");

        System.out.println("Found " + pageObjects.size() + " Page Objects");
    }

    @Test
    @DisplayName("Should extract Page Object methods")
    void scanPageObjects_ExtractsMethods() {
        // When
        Optional<PageObjectInfo> loginPage = registryService.getPageObject("LoginPage");

        // Then
        assertThat(loginPage).isPresent();
        assertThat(loginPage.get().getMethods()).isNotEmpty();
        assertThat(loginPage.get().getMethods())
                .extracting(PageObjectInfo.PageObjectMethod::getName)
                .contains("login", "navigate", "getErrorMessage", "isLoginSuccessful");

        System.out.println("LoginPage methods:");
        loginPage.get().getMethods().forEach(m ->
                System.out.println("  - " + m.getSignature()));
    }

    @Test
    @DisplayName("Should extract method parameters")
    void scanPageObjects_ExtractsMethodParameters() {
        // When
        Optional<PageObjectInfo> loginPage = registryService.getPageObject("LoginPage");

        // Then
        assertThat(loginPage).isPresent();

        Optional<PageObjectInfo.PageObjectMethod> loginMethod = loginPage.get().getMethods().stream()
                .filter(m -> m.getName().equals("login"))
                .findFirst();

        assertThat(loginMethod).isPresent();
        assertThat(loginMethod.get().getParameters()).hasSize(2);
        assertThat(loginMethod.get().getParameters())
                .extracting(PageObjectInfo.MethodParameter::getName)
                .contains("email", "password");
    }

    @Test
    @DisplayName("Should extract page URLs")
    void scanPageObjects_ExtractsPageUrls() {
        // When
        Optional<PageObjectInfo> loginPage = registryService.getPageObject("LoginPage");

        // Then
        assertThat(loginPage).isPresent();
        assertThat(loginPage.get().getPageUrl()).isEqualTo("/login");
    }

    @Test
    @DisplayName("Should search Page Objects by keyword")
    void searchPageObjects_FindsByKeyword() {
        // When
        List<PageObjectInfo> results = registryService.searchPageObjects("login");

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(PageObjectInfo::getSimpleName)
                .contains("LoginPage");
    }

    @Test
    @DisplayName("Should generate AI context")
    void getContextForAIPrompt_GeneratesContext() {
        // When
        String context = registryService.getContextForAIPrompt();

        // Then
        assertThat(context).contains("=== Available Page Objects ===");
        assertThat(context).contains("LoginPage");
        assertThat(context).contains("DashboardPage");
        assertThat(context).contains("login(String email, String password)");
        assertThat(context).contains("Methods:");

        System.out.println("AI Context:\n" + context);
    }

    @Test
    @DisplayName("Should get statistics")
    void getStatistics_ReturnsStats() {
        // When
        Map<String, Object> stats = registryService.getStatistics();

        // Then
        assertThat(stats).containsKeys("pageObjectCount", "totalMethods", "pageObjectsWithUrls");
        assertThat((Integer) stats.get("pageObjectCount")).isGreaterThan(0);
        assertThat((Integer) stats.get("totalMethods")).isGreaterThan(0);

        System.out.println("Statistics: " + stats);
    }

    @Test
    @DisplayName("Should reload registry")
    void reload_Success() {
        // Given
        int initialCount = registryService.getAllPageObjects().size();

        // When
        registryService.reload();

        // Then
        int reloadedCount = registryService.getAllPageObjects().size();
        assertThat(reloadedCount).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("Should handle empty scan path gracefully")
    void scanPageObjects_EmptyPath_NoError() {
        // Given
        PageObjectRegistryService emptyService = new PageObjectRegistryService();
        ReflectionTestUtils.setField(emptyService, "scanPath", "nonexistent/path");

        // When/Then - should not throw exception
        emptyService.scanPageObjects();
        assertThat(emptyService.getAllPageObjects()).isEmpty();
    }
}