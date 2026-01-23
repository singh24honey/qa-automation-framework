package com.company.qa.integration;

import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.model.dto.QualityTrendDTO;
import com.company.qa.model.dto.StoredPatternDTO;
import com.company.qa.model.entity.*;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.repository.*;
import com.company.qa.service.analytics.AnalyticsService;
import com.company.qa.service.quality.TestQualityHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for TestQualityHistoryService
 *
 * Tests the complete workflow using proper API endpoints with authentication:
 * 1. Create API key for authentication
 * 2. Create tests via POST /api/v1/tests endpoint
 * 3. Execute tests and record history
 * 4. Verify historical data and analytics
 *
 * This approach follows production security patterns instead of bypassing
 * authentication by directly manipulating database entities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TestQualityHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestQualityHistoryService historyService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private TestExecutionHistoryRepository historyRepository;

    @Autowired
    private TestQualitySnapshotRepository snapshotRepository;

    @Autowired
    private TestFailurePatternRepository patternRepository;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private TestExecutionRepository executionRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private String validApiKey;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up all data in proper order (respecting foreign keys)
        historyRepository.deleteAll();
        snapshotRepository.deleteAll();
        patternRepository.deleteAll();
        executionRepository.deleteAll();
        testRepository.deleteAll();
        apiKeyRepository.deleteAll();

        // Create API key for authentication following production patterns
        CreateApiKeyRequest apiKeyRequest = CreateApiKeyRequest.builder()
                .name("Test History Integration Key")
                .description("For testing quality history integration")
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

    @org.junit.jupiter.api.Test
    @DisplayName("Should record execution history asynchronously")
    void recordExecutionHistory_CreatesHistoryRecord() throws Exception {
        // Given: Test created via API
        UUID testId = createTestViaApi("LoginTest", "SELENIUM");

        // Create execution directly (as this would come from execution engine)
        TestExecution execution = createExecution(testId, TestStatus.PASSED);

        // When: Record history
        historyService.recordExecutionHistory(execution);

        // Wait for async processing
        Thread.sleep(1000);

        // Then: History should be created
        List<TestExecutionHistory> history = historyRepository.findByExecutionId(execution.getId());

        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getTestName()).isEqualTo("LoginTest");
        assertThat(history.get(0).getStatus()).isEqualTo("PASSED");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should record failure pattern on failed execution")
    void recordExecutionHistory_WithFailure_RecordsPattern() throws Exception {
        // Given: Failed test execution
        UUID testId = createTestViaApi("CheckoutTest", "SELENIUM");
        TestExecution execution = createFailedExecution(testId, "Element not found: #submit-button");

        // When: Record history
        historyService.recordExecutionHistory(execution);

        // Wait for async processing
        Thread.sleep(1000);

        // Then: Failure pattern should be created
        List<TestFailurePattern> patterns = patternRepository.findByTestNameOrderByOccurrencesDesc("CheckoutTest");

        assertThat(patterns).isNotEmpty();
        assertThat(patterns.get(0).getPatternType()).isEqualTo("ELEMENT_NOT_FOUND");
        assertThat(patterns.get(0).getOccurrences()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should increment pattern occurrences on repeated failure")
    void recordFailurePattern_WithExistingPattern_IncrementsCount() throws Exception {
        // Given: Test created via API
        String testName = "PaymentTest";
        createTestViaApi(testName, "SELENIUM");

        String errorSignature = "Timeout waiting for payment confirmation";

        // First occurrence
        historyService.recordFailurePattern(testName, "TIMEOUT", errorSignature, "chrome");

        // When: Same pattern occurs again
        historyService.recordFailurePattern(testName, "TIMEOUT", errorSignature, "firefox");

        // Then: Occurrence count should increase
        List<TestFailurePattern> patterns = patternRepository.findByTestNameOrderByOccurrencesDesc(testName);

        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0).getOccurrences()).isEqualTo(2);
        assertThat(patterns.get(0).getAffectedBrowsers()).contains("chrome", "firefox");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should create daily snapshot successfully")
    void createDailySnapshot_CreatesSnapshot() throws Exception {
        // Given: Some test data created via API
        UUID test1Id = createTestViaApi("Test1", "SELENIUM");
        UUID test2Id = createTestViaApi("Test2", "SELENIUM");

        createExecution(test1Id, TestStatus.PASSED);
        createExecution(test1Id, TestStatus.PASSED);
        createExecution(test2Id, TestStatus.FAILED);

        // When: Create snapshot
        historyService.createDailySnapshot();

        // Then: Snapshot should be created
        Optional<TestQualitySnapshot> snapshot = snapshotRepository.findBySnapshotDate(LocalDate.now());

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getTotalTests()).isEqualTo(2);
        assertThat(snapshot.get().getTotalExecutions()).isGreaterThan(0);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should not create duplicate snapshots for same day")
    void createDailySnapshot_WithExistingSnapshot_DoesNotDuplicate() {
        // Given: Snapshot already exists
        historyService.createDailySnapshot();
        long initialCount = snapshotRepository.count();

        // When: Try to create again
        historyService.createDailySnapshot();

        // Then: Should not create duplicate
        long finalCount = snapshotRepository.count();
        assertThat(finalCount).isEqualTo(initialCount);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should get quality trends successfully")
    void getQualityTrends_ReturnsTrendData() {
        // Given: Snapshots for multiple days
        for (int i = 5; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            createSnapshot(date, 10, 8, 1);
        }

        // When: Get trends
        List<QualityTrendDTO> trends = historyService.getQualityTrends(7);

        // Then: Should return trend data
        assertThat(trends).hasSize(6);
        assertThat(trends.get(0).getDate()).isEqualTo(LocalDate.now().minusDays(5));
        assertThat(trends.get(trends.size() - 1).getDate()).isEqualTo(LocalDate.now());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should get stored patterns for test")
    void getStoredPatterns_ReturnsPatterns() throws Exception {
        // Given: Test created via API and patterns recorded
        String testName = "SearchTest";
        createTestViaApi(testName, "SELENIUM");

        historyService.recordFailurePattern(testName, "TIMEOUT", "Search timeout", "chrome");
        historyService.recordFailurePattern(testName, "ELEMENT_NOT_FOUND", "Results not found", "firefox");

        // When: Get patterns
        List<StoredPatternDTO> patterns = historyService.getStoredPatterns(testName, false);

        // Then: Should return patterns
        assertThat(patterns).hasSize(2);
        assertThat(patterns).extracting(StoredPatternDTO::getPatternType)
                .contains("TIMEOUT", "ELEMENT_NOT_FOUND");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should get high-impact patterns")
    void getHighImpactPatterns_ReturnsHighImpactOnly() throws Exception {
        // Given: Patterns with different impact scores
        String test1 = "HighImpactTest";
        String test2 = "LowImpactTest";

        createTestViaApi(test1, "SELENIUM");
        createTestViaApi(test2, "SELENIUM");

        // Create high-impact pattern (10 occurrences = 100 impact)
        for (int i = 0; i < 10; i++) {
            historyService.recordFailurePattern(test1, "TIMEOUT", "Critical timeout", "chrome");
        }

        // Create low-impact pattern (1 occurrence = 10 impact)
        historyService.recordFailurePattern(test2, "ASSERTION", "Minor assertion", "chrome");

        // When: Get high-impact patterns (>50)
        List<StoredPatternDTO> patterns = historyService.getHighImpactPatterns(50.0);

        // Then: Should return only high-impact
        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0).getTestName()).isEqualTo(test1);
        assertThat(patterns.get(0).getImpactScore()).isGreaterThan(50.0);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should resolve pattern successfully")
    void resolvePattern_MarksPatternAsResolved() throws Exception {
        // Given: Unresolved pattern
        String testName = "BuggyTest";
        createTestViaApi(testName, "SELENIUM");

        historyService.recordFailurePattern(testName, "NETWORK", "Connection failed", "chrome");

        List<TestFailurePattern> patterns = patternRepository.findByTestNameOrderByOccurrencesDesc(testName);
        Long patternId = patterns.get(0).getId();

        // When: Resolve pattern
        historyService.resolvePattern(patternId, "Fixed in v1.2.0");

        // Then: Pattern should be marked resolved
        TestFailurePattern resolved = patternRepository.findById(patternId).orElseThrow();

        assertThat(resolved.getIsResolved()).isTrue();
        assertThat(resolved.getResolvedAt()).isNotNull();
        assertThat(resolved.getResolutionNotes()).isEqualTo("Fixed in v1.2.0");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should cleanup old execution history")
    void cleanupOldHistory_DeletesOldRecords() {
        // Given: Old and new history records
        TestExecution oldExecution = createExecution(UUID.randomUUID(), TestStatus.PASSED);
        oldExecution.setStartTime(Instant.now().minus(100, ChronoUnit.DAYS));
        executionRepository.save(oldExecution);

        TestExecutionHistory oldHistory = TestExecutionHistory.builder()
                .executionId(oldExecution.getId())
                .testName("OldTest")
                .status("PASSED")
                .durationMs(5000L)
                .executedAt(Instant.now().minus(100, ChronoUnit.DAYS))
                .build();
        historyRepository.save(oldHistory);

        TestExecutionHistory newHistory = TestExecutionHistory.builder()
                .executionId(UUID.randomUUID())
                .testName("NewTest")
                .status("PASSED")
                .durationMs(5000L)
                .executedAt(Instant.now())
                .build();
        historyRepository.save(newHistory);

        long initialCount = historyRepository.count();
        assertThat(initialCount).isEqualTo(2);

        // When: Cleanup with 90-day retention
        historyService.cleanupOldHistory(90);

        // Then: Old record should be deleted
        long finalCount = historyRepository.count();
        assertThat(finalCount).isEqualTo(1);

        List<TestExecutionHistory> remaining = historyRepository.findAll();
        assertThat(remaining.get(0).getTestName()).isEqualTo("NewTest");
    }

    // ========== Helper Methods ==========

    /**
     * Creates a test via the proper API endpoint with authentication
     * This follows production patterns instead of bypassing security
     */
    private UUID createTestViaApi(String name, String framework) throws Exception {
        String testJson = String.format("""
                {
                  "name": "%s",
                  "framework": "%s",
                  "language": "java",
                  "isActive": true
                }
                """, name, framework);

        MvcResult result = mockMvc.perform(post("/api/v1/tests")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testJson))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String testIdString = objectMapper.readTree(responseJson)
                .get("data")
                .get("id")
                .asText();

        return UUID.fromString(testIdString);
    }

    /**
     * Creates test execution directly (simulating execution engine)
     * In production, this would be created by the Selenium execution engine
     */
    private TestExecution createExecution(UUID testId, TestStatus status) {
        TestExecution execution = TestExecution.builder()
                .testId(testId)
                .status(status)
                .browser("chrome")
                .environment("test")
                .startTime(Instant.now())
                .endTime(Instant.now().plus(5, ChronoUnit.SECONDS))
                .duration(5000)
                .triggeredBy("test-user")
                .build();
        return executionRepository.save(execution);
    }

    /**
     * Creates a failed test execution with error details
     */
    private TestExecution createFailedExecution(UUID testId, String errorDetails) {
        TestExecution execution = createExecution(testId, TestStatus.FAILED);
        execution.setErrorDetails(errorDetails);
        return executionRepository.save(execution);
    }

    /**
     * Creates a quality snapshot for a specific date
     * Used for testing trend analysis
     */
    private void createSnapshot(LocalDate date, int total, int stable, int flaky) {
        TestQualitySnapshot snapshot = TestQualitySnapshot.builder()
                .snapshotDate(date)
                .totalTests(total)
                .activeTests(total)
                .stableTests(stable)
                .flakyTests(flaky)
                .failingTests(total - stable - flaky)
                .avgPassRate(java.math.BigDecimal.valueOf(85.0))
                .avgFlakinessScore(java.math.BigDecimal.valueOf(15.0))
                .overallHealthScore(java.math.BigDecimal.valueOf(80.0))
                .totalExecutions(100)
                .avgExecutionTimeMs(5000L)
                .build();
        snapshotRepository.save(snapshot);
    }
}