package com.company.qa.integration;

import com.company.qa.model.dto.AIResponse;
import com.company.qa.model.dto.TestGenerationRequest;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.repository.ApiKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests AI provider switching functionality.
 *
 * Tests can be run with different AI_PROVIDER environment variable:
 * - AI_PROVIDER=mock (default, always works)
 * - AI_PROVIDER=ollama (requires ollama running)
 * - AI_PROVIDER=bedrock (requires AWS credentials)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AIProviderSwitchingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private String validApiKey;
    private String currentProvider;

    @BeforeEach
    void setUp() throws Exception {
        // Get current provider from environment or default to mock
        currentProvider = System.getenv().getOrDefault("AI_PROVIDER", "mock");

        // Clean up and create API key
        apiKeyRepository.deleteAll();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Provider Test Key\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        validApiKey = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("keyValue")
                .asText();

        System.out.println("=".repeat(80));
        System.out.println("Testing with AI Provider: " + currentProvider.toUpperCase());
        System.out.println("=".repeat(80));
    }

    @Test
    @Order(1)
    @DisplayName("Provider Switching - Test generation works with current provider")
    void testGeneration_WorksWithCurrentProvider() throws Exception {
        // Given
        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test user registration with email and password")
                .targetUrl("https://example.com/register")
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
                .andReturn();

        // Then
        AIResponse aiResponse = extractAIResponse(result.getResponse().getContentAsString());

        System.out.println("Provider: " + aiResponse.getProvider());
        System.out.println("Content Length: " + aiResponse.getContent().length());
        System.out.println("Tokens Used: " + aiResponse.getTokensUsed());
        System.out.println("Duration: " + aiResponse.getDurationMs() + "ms");

        // Verify provider matches
        assertThat(aiResponse.getProvider().name().toLowerCase())
                .isEqualTo(currentProvider.toLowerCase());
        assertThat(aiResponse.getContent()).isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("Provider Switching - Response quality varies by provider")
    void responseQuality_VariesByProvider() throws Exception {
        // Given
        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test login with invalid credentials")
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
                .andReturn();

        // Then
        AIResponse aiResponse = extractAIResponse(result.getResponse().getContentAsString());

        System.out.println("=".repeat(80));
        System.out.println("Quality Comparison for: " + aiResponse.getProvider());
        System.out.println("-".repeat(80));
        System.out.println(aiResponse.getContent());
        System.out.println("=".repeat(80));

        // Different providers have different characteristics
        switch (aiResponse.getProvider()) {
            case MOCK:
                // Mock should return template-based response quickly
                assertThat(aiResponse.getDurationMs()).isLessThan(1000);
                break;

            case OLLAMA:
                // Ollama should return actual code, may take longer
                assertThat(aiResponse.getContent()).contains("WebDriver");
                break;

            case BEDROCK:
                // Bedrock should return high-quality code
                assertThat(aiResponse.getContent()).isNotEmpty();
                assertThat(aiResponse.getTokensUsed()).isGreaterThan(0);
                break;
        }
    }

    @Test
    @Order(3)
    @DisplayName("Provider Switching - Performance benchmarks")
    void performanceBenchmark_ForCurrentProvider() throws Exception {
        // Run 3 requests and measure performance
        long totalDuration = 0;
        int iterations = 3;

        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test checkout process")
                .targetUrl("https://example.com/checkout")
                .framework("SELENIUM")
                .language("java")
                .build();

        System.out.println("=".repeat(80));
        System.out.println("Performance Benchmark - " + currentProvider.toUpperCase());
        System.out.println("-".repeat(80));

        for (int i = 1; i <= iterations; i++) {
            long startTime = System.currentTimeMillis();

            MvcResult result = mockMvc.perform(post("/api/v1/ai/generate-test")
                            .header("X-API-Key", validApiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            long endTime = System.currentTimeMillis();
            long requestDuration = endTime - startTime;
            totalDuration += requestDuration;

            AIResponse aiResponse = extractAIResponse(result.getResponse().getContentAsString());

            System.out.printf("Request %d: %dms (AI Processing: %dms, Tokens: %d)%n",
                    i, requestDuration, aiResponse.getDurationMs(), aiResponse.getTokensUsed());
        }

        double avgDuration = totalDuration / (double) iterations;
        System.out.println("-".repeat(80));
        System.out.printf("Average Response Time: %.2fms%n", avgDuration);
        System.out.println("=".repeat(80));

        // Performance assertions based on provider
        switch (currentProvider.toLowerCase()) {
            case "mock":
                assertThat(avgDuration).isLessThan(2000); // Mock should be very fast
                break;
            case "ollama":
                assertThat(avgDuration).isLessThan(15000); // Ollama can be slower
                break;
            case "bedrock":
                assertThat(avgDuration).isLessThan(10000); // Bedrock should be reasonable
                break;
        }
    }

    // ========== Helper Methods ==========

    private AIResponse extractAIResponse(String responseBody) throws Exception {
        var jsonNode = objectMapper.readTree(responseBody);
        var dataNode = jsonNode.get("data");
        return objectMapper.treeToValue(dataNode, AIResponse.class);
    }
}