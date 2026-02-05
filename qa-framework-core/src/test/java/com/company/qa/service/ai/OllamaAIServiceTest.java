package com.company.qa.service.ai;

import com.company.qa.config.AIConfig;
import com.company.qa.model.dto.AIResponse;
import com.company.qa.model.dto.TestGenerationRequest;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OllamaAIService
 *
 * Tests run only if Ollama is running locally.
 * Start Ollama with: ollama serve
 */
class OllamaAIServiceTest {

    private AIConfig aiConfig;
    private ObjectMapper objectMapper;
    private OllamaAIService aiService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        aiConfig = new AIConfig();
        aiConfig.setProvider("ollama");

        AIConfig.OllamaConfig ollamaConfig = new AIConfig.OllamaConfig();
        ollamaConfig.setEnabled(true);
        ollamaConfig.setBaseUrl("http://localhost:11434");
        ollamaConfig.setModel("llama3.2:latest");
        ollamaConfig.setMaxTokens(2048);
        ollamaConfig.setTemperature(0.7);

        aiConfig.setOllama(ollamaConfig);

        aiService = new OllamaAIService(aiConfig, objectMapper);
        aiService.init();
    }

    @Test
    @DisplayName("Should return OLLAMA as provider")
    void getProvider_ReturnsOllama() {
        // When
        AIProvider provider = aiService.getProvider();

        // Then
        assertThat(provider).isEqualTo(AIProvider.OLLAMA);
    }

    @Test
    @DisplayName("Service should compile and instantiate")
    void service_Compiles() {
        // Given/When/Then
        assertThat(aiService).isNotNull();
        assertThat(aiService.getProvider()).isEqualTo(AIProvider.OLLAMA);
    }

    @Test
    @DisplayName("Should check availability when Ollama is running")
    void isAvailable_WithOllama_ReturnsTrue() {
        // When
        boolean available = aiService.isAvailable();

        // Then
        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("Should generate test with Ollama")
    void generateTest_WithOllama_ReturnsCode() {
        // Given
        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test login functionality")
                .targetUrl("https://example.com/login")
                .framework("SELENIUM")
                .language("java")
                .build();

        // When
        AIResponse response = aiService.generateTest(request);

        // Then
        System.out.println("=".repeat(80));
        System.out.println("Ollama Test Generation Result:");
        System.out.println("Success: " + response.isSuccess());
        System.out.println("Tokens: " + response.getTokensUsed());
        System.out.println("Duration: " + response.getDurationMs() + "ms");
        System.out.println("=".repeat(80));
        System.out.println(response.getContent());
        System.out.println("=".repeat(80));

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo(AIProvider.OLLAMA);
        assertThat(response.getTaskType()).isEqualTo(AITaskType.TEST_GENERATION);
    }

    /**
     * Helper method to check if Ollama is running
     */
    boolean isOllamaRunning() {
        return aiService.isAvailable();
    }
}