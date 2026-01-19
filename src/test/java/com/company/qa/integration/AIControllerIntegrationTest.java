package com.company.qa.integration;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.repository.ApiKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AI endpoints.
 *
 * Tests all AI functionality with different providers:
 * - Mock AI (always available, fast)
 * - Ollama (local, requires ollama running)
 * - Bedrock (AWS, requires credentials)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AIControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private String validApiKey;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
       // apiKeyRepository.deleteAll();

        // Create API key for tests
        MvcResult result = mockMvc.perform(post("/api/v1/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AI Test Key\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        validApiKey = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("keyValue")
                .asText();
    }

    // ========== Status Endpoint Tests ==========

    @Test
    @Order(1)
    @DisplayName("GET /api/v1/ai/status - Should return AI service status")
    void getStatus_ReturnsServiceStatus() throws Exception {
        // When/Then
        MvcResult result = mockMvc.perform(get("/api/v1/ai/status")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.provider").exists())
                .andExpect(jsonPath("$.data.available").exists())
                .andExpect(jsonPath("$.data.message").exists())
                .andReturn();

        // Parse and verify
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("=".repeat(80));
        System.out.println("AI Status Response:");
        System.out.println(responseBody);
        System.out.println("=".repeat(80));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/ai/status - Should work without authentication")
    void getStatus_WorksWithoutAuth() throws Exception {
        // When/Then - Status endpoint should be public
        mockMvc.perform(get("/api/v1/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ========== Test Generation Tests ==========

    @Test
    @Order(3)
    @DisplayName("POST /api/v1/ai/generate-test - Should generate test code")
    void generateTest_WithValidRequest_ReturnsGeneratedTest() throws Exception {
        // Given
        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test login functionality with username and password")
                .targetUrl("https://example.com/login")
                .framework("SELENIUM")
                .language("java")
                .build();

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/ai/generate-test")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.content").exists())
                .andExpect(jsonPath("$.data.provider").exists())
                .andExpect(jsonPath("$.data.taskType").value("TEST_GENERATION"))
                .andReturn();

        // Parse and display
        String responseBody = result.getResponse().getContentAsString();
        AIResponse aiResponse = extractAIResponse(responseBody);

        System.out.println("=".repeat(80));
        System.out.println("Test Generation Result:");
        System.out.println("Provider: " + aiResponse.getProvider());
        System.out.println("Tokens Used: " + aiResponse.getTokensUsed());
        System.out.println("Duration: " + aiResponse.getDurationMs() + "ms");
        System.out.println("-".repeat(80));
        System.out.println("Generated Test Code:");
        System.out.println(aiResponse.getContent().substring(0,
                Math.min(500, aiResponse.getContent().length())) + "...");
        System.out.println("=".repeat(80));

        // Assertions
        assertThat(aiResponse.getContent()).isNotEmpty();
        assertThat(aiResponse.getContent().toLowerCase()).contains("test");
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/v1/ai/generate-test - Should handle missing fields")
    void generateTest_WithMissingFields_ReturnsBadRequest() throws Exception {
        // Given - Request with missing required fields
        String invalidRequest = "{\"framework\":\"SELENIUM\"}";

        // When/Then
        mockMvc.perform(post("/api/v1/ai/generate-test")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/v1/ai/generate-test - Should require authentication")
    void generateTest_WithoutApiKey_ReturnsUnauthorized() throws Exception {
        // Given
        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test")
                .targetUrl("https://example.com")
                .framework("SELENIUM")
                .language("java")
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/ai/generate-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ========== Failure Analysis Tests ==========

    @Test
    @Order(6)
    @DisplayName("POST /api/v1/ai/analyze-failure - Should analyze test failure")
    void analyzeFailure_WithValidRequest_ReturnsAnalysis() throws Exception {
        // Given
        FailureAnalysisRequest request = FailureAnalysisRequest.builder()
                .testName("LoginTest.testSuccessfulLogin")
                .errorMessage("NoSuchElementException: Unable to locate element: #login-button")
                .stackTrace("org.openqa.selenium.NoSuchElementException: no such element\n  at LoginTest.java:45")
                .testCode("@Test public void testLogin() { driver.findElement(By.id(\"login-button\")).click(); }")
                .build();

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/ai/analyze-failure")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.content").exists())
                .andExpect(jsonPath("$.data.taskType").value("FAILURE_ANALYSIS"))
                .andReturn();

        // Parse and display
        AIResponse aiResponse = extractAIResponse(result.getResponse().getContentAsString());

        System.out.println("=".repeat(80));
        System.out.println("Failure Analysis Result:");
        System.out.println("Provider: " + aiResponse.getProvider());
        System.out.println("Tokens Used: " + aiResponse.getTokensUsed());
        System.out.println("-".repeat(80));
        System.out.println("Analysis:");
        System.out.println(aiResponse.getContent().substring(0,
                Math.min(500, aiResponse.getContent().length())) + "...");
        System.out.println("=".repeat(80));

        // Assertions
        assertThat(aiResponse.getContent()).isNotEmpty();
    }

    // ========== Fix Suggestion Tests ==========

    @Test
    @Order(7)
    @DisplayName("POST /api/v1/ai/suggest-fix - Should suggest fixes")
    void suggestFix_WithValidRequest_ReturnsSuggestions() throws Exception {
        // Given
        FixSuggestionRequest request = FixSuggestionRequest.builder()
                .testCode("@Test public void testLogin() { driver.findElement(By.id(\"btn\")).click(); }")
                .errorMessage("ElementClickInterceptedException: element click intercepted")
                .build();

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/ai/suggest-fix")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.taskType").value("FIX_SUGGESTION"))
                .andReturn();

        // Parse and display
        AIResponse aiResponse = extractAIResponse(result.getResponse().getContentAsString());

        System.out.println("=".repeat(80));
        System.out.println("Fix Suggestion Result:");
        System.out.println("Provider: " + aiResponse.getProvider());
        System.out.println("-".repeat(80));
        System.out.println("Suggestions:");
        System.out.println(aiResponse.getContent().substring(0,
                Math.min(500, aiResponse.getContent().length())) + "...");
        System.out.println("=".repeat(80));

        // Assertions
        assertThat(aiResponse.getContent()).isNotEmpty();
    }

    // ========== Custom Execute Tests ==========

    @Test
    @Order(8)
    @DisplayName("POST /api/v1/ai/execute - Should execute custom AI task")
    void execute_WithCustomTask_ReturnsResult() throws Exception {
        // Given
        AIRequest request = AIRequest.builder()
                .prompt("Generate a simple Hello World test in Java using JUnit 5")
                .taskType(com.company.qa.model.enums.AITaskType.TEST_GENERATION)
                .maxTokens(500)
                .temperature(0.7)
                .build();

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/ai/execute")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.content").exists())
                .andReturn();

        // Parse and display
        AIResponse aiResponse = extractAIResponse(result.getResponse().getContentAsString());

        System.out.println("=".repeat(80));
        System.out.println("Custom Execute Result:");
        System.out.println("Provider: " + aiResponse.getProvider());
        System.out.println("Content: " + aiResponse.getContent().substring(0,
                Math.min(200, aiResponse.getContent().length())) + "...");
        System.out.println("=".repeat(80));

        // Assertions
        assertThat(aiResponse.getContent()).isNotEmpty();
    }

    // ========== Error Handling Tests ==========

    @Test
    @Order(9)
    @DisplayName("All endpoints - Should handle invalid API key")
    void allEndpoints_WithInvalidApiKey_ReturnUnauthorized() throws Exception {
        // Test each endpoint with invalid key
        String invalidKey = "invalid-key-12345";

        // Status endpoint (should work without auth)
        mockMvc.perform(get("/api/v1/ai/status")
                        .header("X-API-Key", invalidKey))
                .andExpect(status().isOk());

        // Generate test
        mockMvc.perform(post("/api/v1/ai/generate-test")
                        .header("X-API-Key", invalidKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // Analyze failure
        mockMvc.perform(post("/api/v1/ai/analyze-failure")
                        .header("X-API-Key", invalidKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // Suggest fix
        mockMvc.perform(post("/api/v1/ai/suggest-fix")
                        .header("X-API-Key", invalidKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // Execute
        mockMvc.perform(post("/api/v1/ai/execute")
                        .header("X-API-Key", invalidKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ========== Helper Methods ==========

    private AIResponse extractAIResponse(String responseBody) throws Exception {
        // Parse ApiResponse<AIResponse>
        var jsonNode = objectMapper.readTree(responseBody);
        var dataNode = jsonNode.get("data");
        return objectMapper.treeToValue(dataNode, AIResponse.class);
    }
}