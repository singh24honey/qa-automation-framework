package com.company.qa.controller;

import com.company.qa.model.dto.ApiKeyDto;
import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.model.dto.SecureAIRequest;
import com.company.qa.model.enums.UserRole;
import com.company.qa.service.ApiKeyService;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Complete E2E test for AI Gateway.
 *
 * Tests the entire flow:
 * HTTP → Controller → Gateway → Security → AI → Response
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AI Gateway E2E Tests")
class AIGatewayE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("E2E: Generate test with complete security flow")
    void e2eGenerateTestWithSecurityFlow() throws Exception {
        // Setup
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(userId)
                .userRole(UserRole.QA_ENGINEER)
                .content("Generate a test for login page at https://example.com")
                .framework("JUnit 5")
                .language("Java")
                .targetUrl("https://example.com/login")
                .strictMode(true)
                .build();

        // Execute & Verify
        mockMvc.perform(post("/api/v1/ai/generate-test")
                        .header("X-API-Key", apiKey)  // ✅ ADDED API KEY
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "QA_ENGINEER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").exists())
                .andExpect(jsonPath("$.data.timestamp").exists());
    }

    @Test
    @DisplayName("E2E: Block request with AWS keys")
    void e2eBlockRequestWithAWSKeys() throws Exception {
        // Setup - Request with AWS keys should be blocked
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(userId)
                .userRole(UserRole.QA_ENGINEER)
                .content("Test with AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE")
                .framework("JUnit 5")
                .language("Java")
                .strictMode(true)
                .build();

        // Execute & Verify
        mockMvc.perform(post("/api/v1/ai/generate-test")
                        .header("X-API-Key", apiKey)  // ✅ ADDED API KEY
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "QA_ENGINEER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.blockedBySecurityPolicy").value(true));
    }

    @Test
    @DisplayName("E2E: Enforce rate limits")
    void e2eEnforceRateLimits() throws Exception {
        // Setup
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(userId)
                .userRole(UserRole.DEVELOPER) // 50 requests/hour limit
                .content("Test")
                .framework("JUnit 5")
                .language("Java")
                .build();

        // Make 51 requests - last one should be rate limited
        for (int i = 0; i < 51; i++) {
            var result = mockMvc.perform(post("/api/v1/ai/generate-test")
                    .header("X-API-Key", apiKey)  // ✅ ADDED API KEY
                    .header("X-User-Id", userId.toString())
                    .header("X-User-Role", "DEVELOPER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            if (i < 50) {
                // First 50 should succeed or fail for other reasons
                result.andExpect(status().isOk());
            } else {
                // 51st should be rate limited
                result.andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.data.rateLimitExceeded").value(true));
            }
        }
    }

    @Test
    @DisplayName("E2E: Get usage stats")
    void e2eGetUsageStats() throws Exception {
        // First make a request to generate some usage
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(userId)
                .userRole(UserRole.QA_ENGINEER)
                .content("Test")
                .framework("JUnit 5")
                .language("Java")
                .build();

        mockMvc.perform(post("/api/v1/ai/generate-test")
                .header("X-API-Key", apiKey)  // ✅ ADDED API KEY
                .header("X-User-Id", userId.toString())
                .header("X-User-Role", "QA_ENGINEER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Then check usage stats
        mockMvc.perform(get("/api/v1/ai/usage-stats")
                        .header("X-API-Key", apiKey)  // ✅ ADDED API KEY
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "QA_ENGINEER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("QA_ENGINEER"))
                .andExpect(jsonPath("$.data.maxRequestsPerHour").value(200));
    }

    @Test
    @DisplayName("E2E: Analyze failure flow")
    void e2eAnalyzeFailure() throws Exception {
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(userId)
                .userRole(UserRole.QA_ENGINEER)
                .content("NullPointerException occurred")
                .testName("testLogin")
                .stackTrace("at com.example.LoginTest.java:42")
                .executionId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/v1/ai/analyze-failure")
                        .header("X-API-Key", apiKey)  // ✅ ADDED API KEY
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "QA_ENGINEER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").exists());
    }

    @Test
    @DisplayName("E2E: Sanitize PII in request")
    void e2eSanitizePII() throws Exception {
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(userId)
                .userRole(UserRole.QA_ENGINEER)
                .content("Test for user john@example.com with phone 555-123-4567")
                .framework("JUnit 5")
                .language("Java")
                .strictMode(false) // Allow with sanitization
                .build();

        mockMvc.perform(post("/api/v1/ai/generate-test")
                        .header("X-API-Key", apiKey)  // ✅ ADDED API KEY
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "QA_ENGINEER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sanitizationApplied").value(true))
                .andExpect(jsonPath("$.data.redactionCount").exists());
    }

    @Test
    @DisplayName("E2E: Reject request without API key")
    void e2eRejectRequestWithoutAPIKey() throws Exception {
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(userId)
                .userRole(UserRole.QA_ENGINEER)
                .content("Test")
                .framework("JUnit 5")
                .language("Java")
                .build();

        mockMvc.perform(post("/api/v1/ai/generate-test")
                        // ❌ NO API KEY
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "QA_ENGINEER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}