package com.company.qa.integration;

import com.company.qa.model.dto.ApiKeyDto;
import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.model.entity.TestQualitySnapshot;
import com.company.qa.repository.TestQualitySnapshotRepository;
import com.company.qa.service.ApiKeyService;
import com.company.qa.service.quality.TestQualityHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TestQualityHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestQualityHistoryService historyService;

    @Autowired
    private TestQualitySnapshotRepository snapshotRepository;

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

        snapshotRepository.deleteAll();
    }
    @Test
    @DisplayName("GET /api/v1/quality/history/trends - should return trend data")
    void getQualityTrends_ReturnsSuccessfully() throws Exception {
        // Given: Snapshots exist
        createSnapshot(LocalDate.now().minusDays(1));
        createSnapshot(LocalDate.now());

        // When/Then
        mockMvc.perform(get("/api/v1/quality/history/trends")
                        .header("X-API-Key", apiKey)  // ✅ ADDED API KEY
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/quality/history/snapshots/latest - should return latest snapshot")
    void getLatestSnapshot_ReturnsSuccessfully() throws Exception {
        // Given: Snapshot exists
        createSnapshot(LocalDate.now());

        // When/Then
        mockMvc.perform(get("/api/v1/quality/history/snapshots/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.snapshotDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.totalTests").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/quality/history/snapshots/create - should create snapshot")
    void createSnapshot_ReturnsSuccessfully() throws Exception {
        // When/Then
        mockMvc.perform(post("/api/v1/quality/history/snapshots/create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("created successfully")));
    }

    @Test
    @DisplayName("GET /api/v1/quality/history/stats - should return statistics")
    void getHistoricalStats_ReturnsSuccessfully() throws Exception {
        // Given: Snapshots exist
        createSnapshot(LocalDate.now().minusDays(2));
        createSnapshot(LocalDate.now().minusDays(1));
        createSnapshot(LocalDate.now());

        // When/Then
        mockMvc.perform(get("/api/v1/quality/history/stats")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.period").value("7 days"))
                .andExpect(jsonPath("$.data.avgHealthScore").exists())
                .andExpect(jsonPath("$.data.snapshotsAnalyzed").value(3));
    }

    @Test
    @DisplayName("PUT /api/v1/quality/history/patterns/{id}/resolve - should resolve pattern")
    void resolvePattern_ReturnsSuccessfully() throws Exception {
        // Given: Pattern exists
        historyService.recordFailurePattern("TestName", "TIMEOUT", "Error message", "chrome");

        Long patternId = 1L; // Assuming first pattern

        Map<String, String> request = Map.of("notes", "Fixed in version 1.2.0");

        // When/Then
        mockMvc.perform(put("/api/v1/quality/history/patterns/{id}/resolve", patternId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/quality/history/cleanup - should trigger cleanup")
    void triggerCleanup_ReturnsSuccessfully() throws Exception {
        // When/Then
        mockMvc.perform(post("/api/v1/quality/history/cleanup")
                        .param("historyRetentionDays", "90")
                        .param("snapshotRetentionDays", "365")
                        .param("patternRetentionDays", "180"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.historyCleanup").exists())
                .andExpect(jsonPath("$.data.snapshotCleanup").exists())
                .andExpect(jsonPath("$.data.patternCleanup").exists());
    }

    // Helper method
    private void createSnapshot(LocalDate date) {
        TestQualitySnapshot snapshot = TestQualitySnapshot.builder()
                .snapshotDate(date)
                .totalTests(10)
                .activeTests(10)
                .stableTests(8)
                .flakyTests(1)
                .failingTests(1)
                .avgPassRate(BigDecimal.valueOf(85.0))
                .avgFlakinessScore(BigDecimal.valueOf(15.0))
                .overallHealthScore(BigDecimal.valueOf(80.0))
                .totalExecutions(100)
                .avgExecutionTimeMs(5000L)
                .build();
        snapshotRepository.save(snapshot);
    }
}