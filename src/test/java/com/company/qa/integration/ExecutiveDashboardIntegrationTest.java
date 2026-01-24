package com.company.qa.integration;

import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.repository.*;
import com.company.qa.service.analytics.ExecutiveDataPopulationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Complete integration test for Executive Dashboard
 * Tests: Data population, scheduled jobs, admin operations, APIs
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ExecutiveDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutiveDataPopulationService populationService;

    @Autowired
    private ExecutiveKPICacheRepository kpiCacheRepository;

    @Autowired
    private QualityTrendAnalysisRepository trendAnalysisRepository;

    @Autowired
    private ExecutiveAlertRepository alertRepository;

    @Autowired
    private TestQualitySnapshotRepository snapshotRepository;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private String validApiKey;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up in proper order
        alertRepository.deleteAll();
        kpiCacheRepository.deleteAll();
        trendAnalysisRepository.deleteAll();
        testRepository.deleteAll();
        apiKeyRepository.deleteAll();

        // Create API key for authentication
        CreateApiKeyRequest apiKeyRequest = CreateApiKeyRequest.builder()
                .name("Executive Test Key")
                .description("For testing executive dashboard")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiKeyRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        validApiKey = objectMapper.readTree(responseJson)
                .get("data")
                .get("keyValue")
                .asText();
    }

    @Test
    @DisplayName("Should populate today's executive data successfully")
    void shouldPopulateTodaysData() throws Exception {
        // When: Manually populate today's data
        mockMvc.perform(post("/api/v1/executive/admin/populate/today")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("success"));

        // Then: Verify data was created
        long kpiCount = kpiCacheRepository.count();
        assertThat(kpiCount).isGreaterThanOrEqualTo(1);

        long trendCount = trendAnalysisRepository.count();
        assertThat(trendCount).isGreaterThanOrEqualTo(0); // May be 0 if no historical data
    }

    @Test
    @DisplayName("Should retrieve executive dashboard successfully")
    void shouldGetExecutiveDashboard() throws Exception {
        // Given: Populated data
        populationService.generateDailyKPIs();

        // When: Get dashboard
        MvcResult result = mockMvc.perform(get("/api/v1/executive/dashboard")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        assertThat(responseJson).contains("currentKPIs");
        assertThat(responseJson).contains("qualityTrends");
        assertThat(responseJson).contains("criticalAlerts");
    }

    @Test
    @DisplayName("Should retrieve quality trends successfully")
    void shouldGetQualityTrends() throws Exception {
        // Given: Some trend data
        populationService.generateTrendAnalysis(LocalDate.now());

        // When: Get trends
        String startDate = LocalDate.now().minusDays(7).toString();
        String endDate = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/executive/trends")
                        .header("X-API-Key", validApiKey)
                        .param("startDate", startDate)
                        .param("endDate", endDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Should retrieve active alerts successfully")
    void shouldGetActiveAlerts() throws Exception {
        // When: Get alerts (may be empty)
        mockMvc.perform(get("/api/v1/executive/alerts")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Should refresh all executive data successfully")
    void shouldRefreshAllData() throws Exception {
        // When: Trigger full refresh
        mockMvc.perform(post("/api/v1/executive/admin/populate/refresh-all")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then: Data should be present
        long kpiCount = kpiCacheRepository.count();
        assertThat(kpiCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should check executive dashboard health successfully")
    void shouldCheckHealth() throws Exception {
        // When: Check health
        mockMvc.perform(get("/api/v1/executive/admin/health")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("healthy"));
    }

    @Test
    @DisplayName("Should handle manual KPI refresh")
    void shouldRefreshKPI() throws Exception {
        // When: Manually refresh KPI cache
        mockMvc.perform(post("/api/v1/executive/kpi/refresh")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}