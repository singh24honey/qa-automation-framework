package com.company.qa.service.ai;

import com.company.qa.config.AIConfig;
import com.company.qa.model.dto.*;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Ollama AI Service - Local, free AI integration
 *
 * Supports local models like CodeLlama, Llama 3, etc.
 * Runs entirely on your machine - no cloud costs!
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "ollama")
public class OllamaAIService implements AIService {

    private final AIConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        if (!aiConfig.getOllama().isEnabled()) {
            log.warn("Ollama AI is disabled in configuration");
            return;
        }

        log.info("Initializing Ollama AI Service");
        log.info("Base URL: {}", aiConfig.getOllama().getBaseUrl());
        log.info("Model: {}", aiConfig.getOllama().getModel());

        // Verify Ollama is accessible
        if (isAvailable()) {
            log.info("Ollama AI service is ready");
        } else {
            log.warn("Ollama AI service is not accessible. Make sure Ollama is running.");
            log.warn("Start with: ollama serve");
        }
    }

    @Override
    public AIProvider getProvider() {
        return AIProvider.OLLAMA;
    }

    @Override
    public boolean isAvailable() {
        try {
            String url = aiConfig.getOllama().getBaseUrl() + "/api/tags";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.debug("Ollama health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ========== AI Service Interface Methods ==========

    @Override
    public AIResponse generateTest(TestGenerationRequest request) {
        log.info("Ollama AI: Generating test for: {}", request.getDescription());

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildTestGenerationPrompt(request);

            AIResponse response = invokeModel(
                    prompt,
                    AITaskType.TEST_GENERATION,
                    aiConfig.getOllama().getMaxTokens(),
                    aiConfig.getOllama().getTemperature()
            );

            response.setDurationMs(System.currentTimeMillis() - startTime);
            response.addMetadata("framework", request.getFramework());
            response.addMetadata("language", request.getLanguage());
            response.addMetadata("targetUrl", request.getTargetUrl());

            return response;

        } catch (Exception e) {
            log.error("Failed to generate test: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to generate test: " + e.getMessage(),
                    AIProvider.OLLAMA,
                    AITaskType.TEST_GENERATION
            );
        }
    }

    @Override
    public AIResponse analyzeFailure(FailureAnalysisRequest request) {
        log.info("Ollama AI: Analyzing failure for test: {}", request.getTestName());

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildFailureAnalysisPrompt(request);

            AIResponse response = invokeModel(
                    prompt,
                    AITaskType.FAILURE_ANALYSIS,
                    aiConfig.getOllama().getMaxTokens(),
                    aiConfig.getOllama().getTemperature()
            );

            response.setDurationMs(System.currentTimeMillis() - startTime);
            response.addMetadata("testName", request.getTestName());
            response.addMetadata("executionId", request.getExecutionId());

            return response;

        } catch (Exception e) {
            log.error("Failed to analyze failure: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to analyze failure: " + e.getMessage(),
                    AIProvider.OLLAMA,
                    AITaskType.FAILURE_ANALYSIS
            );
        }
    }

    @Override
    public AIResponse suggestFix(String testCode, String errorMessage) {
        log.info("Ollama AI: Suggesting fix for error");

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildFixSuggestionPrompt(testCode, errorMessage);

            AIResponse response = invokeModel(
                    prompt,
                    AITaskType.FIX_SUGGESTION,
                    aiConfig.getOllama().getMaxTokens(),
                    aiConfig.getOllama().getTemperature()
            );

            response.setDurationMs(System.currentTimeMillis() - startTime);

            return response;

        } catch (Exception e) {
            log.error("Failed to suggest fix: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to suggest fix: " + e.getMessage(),
                    AIProvider.OLLAMA,
                    AITaskType.FIX_SUGGESTION
            );
        }
    }

    @Override
    public AIResponse execute(AIRequest request) {
        log.info("Ollama AI: Executing custom task: {}", request.getTaskType());

        long startTime = System.currentTimeMillis();

        try {
            Integer maxTokens = request.getMaxTokens() != null ?
                    request.getMaxTokens() : aiConfig.getOllama().getMaxTokens();

            Double temperature = request.getTemperature() != null ?
                    request.getTemperature() : aiConfig.getOllama().getTemperature();

            AIResponse response = invokeModel(
                    request.getPrompt(),
                    request.getTaskType(),
                    maxTokens,
                    temperature
            );

            response.setDurationMs(System.currentTimeMillis() - startTime);

            if (request.getContext() != null) {
                request.getContext().forEach(response::addMetadata);
            }

            return response;

        } catch (Exception e) {
            log.error("Failed to execute custom task: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to execute task: " + e.getMessage(),
                    AIProvider.OLLAMA,
                    request.getTaskType()
            );
        }
    }

    // ========== Ollama API Integration ==========

    private AIResponse invokeModel(String prompt, AITaskType taskType,
                                   Integer maxTokens, Double temperature) throws Exception {

        // Build request
        OllamaRequest ollamaRequest = OllamaRequest.create(
                aiConfig.getOllama().getModel(),
                prompt,
                maxTokens,
                temperature
        );

        String requestJson = objectMapper.writeValueAsString(ollamaRequest);
        log.debug("Ollama request: {}", requestJson.substring(0, Math.min(200, requestJson.length())));

        // Set up HTTP request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        // Call Ollama API
        String url = aiConfig.getOllama().getBaseUrl() + "/api/generate";

        ResponseEntity<String> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Ollama API returned: " + responseEntity.getStatusCode());
        }

        // Parse response
        OllamaResponse ollamaResponse = objectMapper.readValue(
                responseEntity.getBody(),
                OllamaResponse.class
        );

        log.debug("Ollama response: {}", ollamaResponse.getGeneratedText()
                .substring(0, Math.min(200, ollamaResponse.getGeneratedText().length())));

        // Build AI response
        return AIResponse.builder()
                .success(true)
                .content(ollamaResponse.getGeneratedText())
                .provider(AIProvider.OLLAMA)
                .taskType(taskType)
                .tokensUsed(ollamaResponse.getTotalTokens())
                .generatedAt(Instant.now())
                .build();
    }

    // ========== Prompt Building ==========

    private String buildTestGenerationPrompt(TestGenerationRequest request) {
        return String.format("""
                Generate a %s test in %s for the following scenario:
                
                Description: %s
                Target URL: %s
                
                Requirements:
                1. Use %s framework
                2. Include proper page object pattern
                3. Add assertions
                4. Handle waits and synchronization
                5. Include error handling
                
                Generate only the test code, no explanations.
                """,
                request.getFramework(),
                request.getLanguage(),
                request.getDescription(),
                request.getTargetUrl(),
                request.getFramework()
        );
    }

    private String buildFailureAnalysisPrompt(FailureAnalysisRequest request) {
        return String.format("""
                Analyze this test failure:
                
                Test Name: %s
                Error Message: %s
                Stack Trace:
                %s
                
                Test Code:
                %s
                
                Provide:
                1. Root cause analysis
                2. Affected components
                3. Recommended fixes
                4. Prevention strategies
                
                Be specific and actionable.
                """,
                request.getTestName(),
                request.getErrorMessage(),
                request.getStackTrace(),
                request.getTestCode()
        );
    }

    private String buildFixSuggestionPrompt(String testCode, String errorMessage) {
        return String.format("""
                A test is failing with the following error:
                
                Error: %s
                
                Test Code:
                %s
                
                Provide:
                1. Immediate fix suggestions (code changes)
                2. Alternative approaches
                3. Best practices to prevent similar issues
                
                Be specific and provide code examples where applicable.
                """,
                errorMessage,
                testCode
        );
    }
}