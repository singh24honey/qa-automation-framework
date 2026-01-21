package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.UserRole;
import com.company.qa.service.ai.AIGatewayService;
import com.company.qa.service.ai.AIService;
import com.company.qa.service.security.RateLimiterService;
import com.company.qa.service.security.RateLimiterService.RateLimitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AIController.class)
@DisplayName("AI Controller Tests")
class AIControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AIService aiService;

    @MockBean
    private AIGatewayService aiGatewayService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    @DisplayName("Should generate test successfully through secure gateway")
    void shouldGenerateTestSuccessfully() throws Exception {
        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test login page")
                .framework("JUnit 5")
                .language("Java")
                .targetUrl("https://example.com/login")
                .build();

        SecureAIResponse mockResponse = SecureAIResponse.builder()
                .requestId(UUID.randomUUID())
                .success(true)
                .content("@Test public void testLogin() {}")
                .tokensUsed(500)
                .estimatedCost(0.01)
                .validationPassed(true)
                .timestamp(Instant.now())
                .build();

        when(aiGatewayService.generateTest(any(SecureAIRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/ai/generate-test")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "QA_ENGINEER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.content").exists());
    }

    @Test
    @DisplayName("Should return 429 when rate limit exceeded")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test")
                .build();

        SecureAIResponse mockResponse = SecureAIResponse.builder()
                .requestId(UUID.randomUUID())
                .success(false)
                .rateLimitExceeded(true)
                .errorMessage("Rate limit exceeded")
                .timestamp(Instant.now())
                .build();

        when(aiGatewayService.generateTest(any(SecureAIRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/ai/generate-test")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Should get usage statistics")
    void shouldGetUsageStatistics() throws Exception {
        UUID userId = UUID.randomUUID();

        when(rateLimiterService.getTokenUsage(any(UUID.class))).thenReturn(5000L);
        when(rateLimiterService.getCurrentCost(any(UUID.class))).thenReturn(2.50);
        when(rateLimiterService.checkRateLimit(any(UUID.class), any(UserRole.class)))
                .thenReturn(new RateLimitResult(true, 45, 50, Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/api/v1/ai/usage-stats")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "QA_ENGINEER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokensUsed").value(5000))
                .andExpect(jsonPath("$.data.totalCost").value(2.50));
    }
}