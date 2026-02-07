package com.company.qa.controller;

import com.company.qa.model.dto.ApiKeyDto;
import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.service.ApiKeyService;
import com.company.qa.service.playwright.ElementRegistryService;
import com.company.qa.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for ElementRegistryController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Element Registry REST API Tests")
class ElementRegistryControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ElementRegistryService registryService;

    @Autowired
    private ApiKeyService apiKeyService;

    private String apiKey;

    @BeforeEach
    void setUp() {
        registryService.reloadRegistry();
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .name("E2E Test Key")
                .description("API key for E2E testing")
                .build();

        ApiKeyDto apiKeyDto = apiKeyService.createApiKey(request);
        apiKey = apiKeyDto.getKeyValue();
    }

    @Test
    @DisplayName("GET /api/v1/registry/elements - Should return all elements")
    void getAllElements_Success() throws Exception {
        mockMvc.perform(get("/api/v1/registry/elements")
                .header("X-API-Key", apiKey))  // ✅ ADDED API KEY
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pageCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.pages").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/registry/elements/search - Should search elements")
    void searchElements_Success() throws Exception {
        mockMvc.perform(get("/api/v1/registry/elements/search")
                        .param("q", "email")
                .header("X-API-Key", apiKey))  // ✅ ADDED API KEY
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.query").value("email"))
                .andExpect(jsonPath("$.resultCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/registry/pages - Should return all pages")
    void getAllPages_Success() throws Exception {
        mockMvc.perform(get("/api/v1/registry/pages")
                .header("X-API-Key", apiKey))  // ✅ ADDED API KEY
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.pages").isArray())
                .andExpect(jsonPath("$.pages[0].pageName").exists());
    }

    @Test
    @DisplayName("GET /api/v1/registry/pages/{pageName} - Should return specific page")
    void getPage_Success() throws Exception {
        mockMvc.perform(get("/api/v1/registry/pages/login")
                .header("X-API-Key", apiKey))  // ✅ ADDED API KEY
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.page.pageName").value("login"))
                .andExpect(jsonPath("$.page.url").value("/login"))
                .andExpect(jsonPath("$.page.elements").isMap());
    }

    @Test
    @DisplayName("GET /api/v1/registry/pages/{pageName} - Should return 404 for non-existent page")
    void getPage_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/registry/pages/nonexistent")
                .header("X-API-Key", apiKey))  // ✅ ADDED API KEY
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/registry/pages/{pageName}/elements - Should return page elements")
    void getPageElements_Success() throws Exception {
        mockMvc.perform(get("/api/v1/registry/pages/login/elements")
                .header("X-API-Key", apiKey))  // ✅ ADDED API KEY
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pageName").value("login"))
                .andExpect(jsonPath("$.elementCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.elements").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/registry/pages/{pageName}/elements/{elementName} - Should return specific element")
    void getElement_Success() throws Exception {
        mockMvc.perform(get("/api/v1/registry/pages/login/elements/emailInput").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.element.elementName").value("emailInput"))
                .andExpect(jsonPath("$.element.strategy").value("label"))
                .andExpect(jsonPath("$.element.playwrightCode").exists());
    }

    @Test
    @DisplayName("GET /api/v1/registry/pages/{pageName}/elements/{elementName} - Should return 404 for non-existent element")
    void getElement_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/registry/pages/login/elements/nonexistent").header("X-API-Key", apiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/registry/pages/{pageName}/elements/{elementName}/fallbacks - Should return fallback locators")
    void getFallbackLocators_Success() throws Exception {
        mockMvc.perform(get("/api/v1/registry/pages/login/elements/emailInput/fallbacks").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pageName").value("login"))
                .andExpect(jsonPath("$.elementName").value("emailInput"))
                .andExpect(jsonPath("$.fallbacks").isArray())
                .andExpect(jsonPath("$.fallbacks[0]").isString());
    }

    @Test
    @DisplayName("GET /api/v1/registry/ai-context - Should return AI context for specific pages")
    void getAIContext_SpecificPages() throws Exception {
        mockMvc.perform(get("/api/v1/registry/ai-context")
                        .param("pages", "login", "dashboard").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.context").isString())
                .andExpect(jsonPath("$.context").value(containsString("login")))
                .andExpect(jsonPath("$.context").value(containsString("dashboard")));
    }

    @Test
    @DisplayName("GET /api/v1/registry/ai-context - Should return AI context for all pages when no params")
    void getAIContext_AllPages() throws Exception {
        mockMvc.perform(get("/api/v1/registry/ai-context").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.context").isString())
                .andExpect(jsonPath("$.context").value(containsString("Available Elements")));
    }

    @Test
    @DisplayName("POST /api/v1/registry/reload - Should reload registry")
    void reloadRegistry_Success() throws Exception {
        mockMvc.perform(post("/api/v1/registry/reload").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.statistics").isMap());
    }

    @Test
    @DisplayName("GET /api/v1/registry/stats - Should return statistics")
    void getStatistics_Success() throws Exception {
        mockMvc.perform(get("/api/v1/registry/stats").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.statistics.version").value("1.0"))
                .andExpect(jsonPath("$.statistics.pageCount").value(3))
                .andExpect(jsonPath("$.statistics.totalElements").exists())
                .andExpect(jsonPath("$.statistics.strategyBreakdown").isMap());
    }
}