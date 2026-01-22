package com.company.qa.controller;

import com.company.qa.model.dto.ApiKeyDto;
import com.company.qa.model.dto.CostAnalyticsDTO;
import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.service.ApiKeyService;
import com.company.qa.service.ai.AIBudgetService;
import com.company.qa.service.ai.AICostAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Cost Analysis")
class CostAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AICostAnalyticsService costAnalyticsService;

    @MockBean
    private AIBudgetService budgetService;

    @Autowired
    private ApiKeyService apiKeyService;

    private String apiKey;
    private UUID userId;
    @BeforeEach
    void setUp() {
        // Create API key for testing
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .name("E2E Test Key")
                .description("API key for E2E testing")
                .build();

        ApiKeyDto apiKeyDto = apiKeyService.createApiKey(request);
        apiKey = apiKeyDto.getKeyValue();
        userId = UUID.randomUUID();
    }
    @Test
    void testGetLast30DaysAnalytics() throws Exception {
        // Arrange
        CostAnalyticsDTO analytics = CostAnalyticsDTO.builder()
                .totalCost(new BigDecimal("45.50"))
                .totalRequests(450L)
                .totalTokens(2500000L)
                .costByProvider(new HashMap<>())
                .costByTaskType(new HashMap<>())
                .build();

        when(costAnalyticsService.getLast30DaysAnalytics()).thenReturn(analytics);

        // Act & Assert
        mockMvc.perform(get("/api/v1/ai-costs/analytics/last-30-days")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCost").value(45.50))
                .andExpect(jsonPath("$.data.totalRequests").value(450))
                .andExpect(jsonPath("$.data.totalTokens").value(2500000));
    }

    @Test
    void testGetCurrentMonthAnalytics() throws Exception {
        // Arrange
        CostAnalyticsDTO analytics = CostAnalyticsDTO.builder()
                .totalCost(new BigDecimal("30.00"))
                .totalRequests(300L)
                .totalTokens(1500000L)
                .build();

        when(costAnalyticsService.getCurrentMonthAnalytics()).thenReturn(analytics);

        // Act & Assert
        mockMvc.perform(get("/api/v1/ai-costs/analytics/current-month")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCost").value(30.00));
    }
}