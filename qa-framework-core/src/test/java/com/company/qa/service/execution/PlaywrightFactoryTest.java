package com.company.qa.service.execution;

import com.company.qa.config.PlaywrightProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PlaywrightFactory.
 * Tests all browser creation and management functionality.
 *
 * @author QA Framework
 * @since Week 11 Day 2
 */
@DisplayName("PlaywrightFactory Tests")
class PlaywrightFactoryTest {

    private PlaywrightFactory factory;
    private PlaywrightProperties properties;

    // Resources to clean up
    private Browser browser;
    private BrowserContext context;

    @BeforeEach
    void setUp() {
        // Create test configuration
        properties = new PlaywrightProperties();
        properties.setEnabled(true);
        properties.setBrowser("chromium");
        properties.setHeadless(false);  // Always headless in tests
        properties.setTimeout(10000);
        properties.setSlowMo(0);
        properties.setRecordVideo(false);
        properties.setRecordTrace(false);

        // Create factory
        factory = new PlaywrightFactory(properties);
    }

    @AfterEach
    void tearDown() {
        // Clean up resources in reverse order
        if (context != null) {
            factory.closeContext(context);
            context = null;
        }
        if (browser != null) {
            factory.closeBrowser(browser);
            browser = null;
        }

        // Cleanup factory
        factory.cleanup();
    }

    @Test
    @DisplayName("Should create Playwright instance")
    void shouldCreatePlaywrightInstance() {
        // When
        Playwright playwright = factory.getPlaywright();

        // Then
        assertThat(playwright).isNotNull();
    }

    @Test
    @DisplayName("Should return same Playwright instance (singleton)")
    void shouldReturnSameInstance() {
        // When
        Playwright pw1 = factory.getPlaywright();
        Playwright pw2 = factory.getPlaywright();

        // Then
        assertThat(pw1).isSameAs(pw2);
    }

    @Test
    @DisplayName("Should create Chromium browser")
    void shouldCreateChromiumBrowser() {
        // When
        browser = factory.createBrowser(PlaywrightProperties.BrowserType.CHROMIUM);

        // Then
        assertThat(browser).isNotNull();
        assertThat(browser.browserType().name()).isEqualTo("chromium");
        assertThat(browser.isConnected()).isTrue();
    }

    @Test
    @DisplayName("Should create Firefox browser")
    void shouldCreateFirefoxBrowser() {
        // When
        browser = factory.createBrowser(PlaywrightProperties.BrowserType.FIREFOX);

        // Then
        assertThat(browser).isNotNull();
        assertThat(browser.browserType().name()).isEqualTo("firefox");
    }

    @Test
    @DisplayName("Should create WebKit browser")
    void shouldCreateWebKitBrowser() {
        // When
        browser = factory.createBrowser(PlaywrightProperties.BrowserType.WEBKIT);

        // Then
        assertThat(browser).isNotNull();
        assertThat(browser.browserType().name()).isEqualTo("webkit");
    }

    @Test
    @DisplayName("Should create browser using default type from config")
    void shouldCreateBrowserFromConfig() {
        // When
        browser = factory.createBrowser();

        // Then
        assertThat(browser).isNotNull();
        assertThat(browser.browserType().name()).isEqualTo("chromium");
    }

    @Test
    @DisplayName("Should create browser context")
    void shouldCreateContext() {
        // Given
        browser = factory.createBrowser();

        // When
        context = factory.createContext(browser);

        // Then
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("Should create browser context with execution ID")
    void shouldCreateContextWithExecutionId() {
        // Given
        browser = factory.createBrowser();

        // When
        context = factory.createContext(browser, "test-execution-123");

        // Then
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("Should create page in context")
    void shouldCreatePage() {
        // Given
        browser = factory.createBrowser();
        context = factory.createContext(browser);

        // When
        Page page = factory.createPage(context);

        // Then
        assertThat(page).isNotNull();
        assertThat(page.isClosed()).isFalse();
    }

    @Test
    @DisplayName("Should navigate to URL")
    void shouldNavigateToUrl() {
        // Given
        browser = factory.createBrowser();
        context = factory.createContext(browser);
        Page page = factory.createPage(context);

        // When
        page.navigate("https://example.com");

        // Then
        assertThat(page.url()).contains("example.com");
        assertThat(page.title()).isNotEmpty();
    }

    @Test
    @DisplayName("Should capture screenshot")
    void shouldCaptureScreenshot() {
        // Given
        browser = factory.createBrowser();
        context = factory.createContext(browser);
        Page page = factory.createPage(context);
        page.navigate("https://example.com");

        // When
        Path screenshot = factory.captureScreenshot(page, "test-screenshot");

        // Then
        assertThat(screenshot).isNotNull();
        assertThat(screenshot.toString()).contains("test-screenshot.png");
    }

    @Test
    @DisplayName("Should capture screenshot with execution ID and step")
    void shouldCaptureScreenshotWithStep() {
        // Given
        browser = factory.createBrowser();
        context = factory.createContext(browser);
        Page page = factory.createPage(context);
        page.navigate("https://example.com");

        // When
        Path screenshot = factory.captureScreenshot(page, "test-123", 1);

        // Then
        assertThat(screenshot).isNotNull();
        assertThat(screenshot.toString()).contains("test-123_step_1.png");
    }

    @Test
    @DisplayName("Should close context gracefully")
    void shouldCloseContext() {
        // Given
        browser = factory.createBrowser();
        context = factory.createContext(browser);

        // When
        factory.closeContext(context);

        // Then - no exception, context closed
        context = null; // Prevent double-close in tearDown
    }

    @Test
    @DisplayName("Should close browser gracefully")
    void shouldCloseBrowser() {
        // Given
        browser = factory.createBrowser();

        // When
        factory.closeBrowser(browser);

        // Then
        assertThat(browser.isConnected()).isFalse();
        browser = null; // Prevent double-close in tearDown
    }

    @Test
    @DisplayName("Should check if Playwright is enabled")
    void shouldCheckIfEnabled() {
        // When
        boolean enabled = factory.isEnabled();

        // Then
        assertThat(enabled).isTrue();
    }

    @Test
    @DisplayName("Should get available browsers")
    void shouldGetAvailableBrowsers() {
        // When
        String[] browsers = factory.getAvailableBrowsers();

        // Then
        assertThat(browsers).containsExactly("CHROMIUM", "FIREFOX", "WEBKIT");
    }

    @Test
    @DisplayName("Should handle null context in close")
    void shouldHandleNullContextInClose() {
        // When/Then - should not throw exception
        assertThatCode(() -> factory.closeContext(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle null browser in close")
    void shouldHandleNullBrowserInClose() {
        // When/Then - should not throw exception
        assertThatCode(() -> factory.closeBrowser(null))
                .doesNotThrowAnyException();
    }
}