package com.company.qa.service.ai;

import com.company.qa.config.AIConfig;
import com.company.qa.model.dto.*;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;

/**
 * AWS Bedrock AI Service Implementation
 *
 * Provides AI capabilities using AWS Bedrock with Claude models.
 * Implements the AIService interface for seamless provider switching.
 *
 * Required configuration:
 * - ai.provider=bedrock
 * - ai.bedrock.enabled=true
 * - AWS credentials (via environment or explicit config)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "bedrock")
public class BedrockAIService implements AIService {

    private final AIConfig aiConfig;
    private final ObjectMapper objectMapper;

    private BedrockRuntimeClient bedrockClient;

    // ========== Lifecycle Methods ==========

    @PostConstruct
    public void init() {
        if (!aiConfig.getBedrock().isEnabled()) {
            log.warn("Bedrock AI is disabled in configuration");
            return;
        }

        log.info("Initializing AWS Bedrock AI Service");
        log.info("Region: {}", aiConfig.getBedrock().getRegion());
        log.info("Model: {}", aiConfig.getBedrock().getModel());

        try {
            AIConfig.BedrockConfig config = aiConfig.getBedrock();

            // Check if credentials are provided
            if (config.getAccessKeyId() == null || config.getAccessKeyId().isEmpty()) {
                log.info("AWS credentials not provided. Using default credential chain.");

                // Use default credential chain (IAM role, environment variables, etc.)
                bedrockClient = BedrockRuntimeClient.builder()
                        .region(Region.of(config.getRegion()))
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .overrideConfiguration(c -> c
                                .apiCallTimeout(Duration.ofSeconds(config.getTimeout()))
                                .apiCallAttemptTimeout(Duration.ofSeconds(config.getTimeout())))
                        .build();
            } else {
                log.info("Using explicit AWS credentials");

                // Use explicit credentials
                AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                        config.getAccessKeyId(),
                        config.getSecretAccessKey()
                );

                bedrockClient = BedrockRuntimeClient.builder()
                        .region(Region.of(config.getRegion()))
                        .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                        .overrideConfiguration(c -> c
                                .apiCallTimeout(Duration.ofSeconds(config.getTimeout()))
                                .apiCallAttemptTimeout(Duration.ofSeconds(config.getTimeout())))
                        .build();
            }

            log.info("Bedrock client initialized successfully");

            // Test connection
            if (isAvailable()) {
                log.info("Bedrock AI service is ready");
            } else {
                log.warn("Bedrock AI service health check failed");
            }

        } catch (Exception e) {
            log.error("Failed to initialize Bedrock client: {}", e.getMessage(), e);
            bedrockClient = null;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (bedrockClient != null) {
            log.info("Closing Bedrock client");
            bedrockClient.close();
        }
    }

    // ========== AIService Interface Implementation ==========

    @Override
    public AIProvider getProvider() {
        return AIProvider.BEDROCK;
    }

    @Override
    public boolean isAvailable() {
        if (bedrockClient == null) {
            return false;
        }

        try {
            // Simple health check - try to invoke with minimal prompt
            String testPrompt = "Reply with OK";
            BedrockRequest request = BedrockRequest.createUserMessage(testPrompt, 10, 0.1);

            String requestBody = objectMapper.writeValueAsString(request);
            InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                    .modelId(aiConfig.getBedrock().getModel())
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(invokeRequest);
            return response.sdkHttpResponse().isSuccessful();

        } catch (Exception e) {
            log.debug("Bedrock health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public AIResponse generateTest(TestGenerationRequest request) {
        log.info("Bedrock AI: Generating test for: {}", request.getDescription());

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildTestGenerationPrompt(request);

            AIResponse response = invokeModel(
                    prompt,
                    AITaskType.TEST_GENERATION,
                    aiConfig.getBedrock().getMaxTokens(),
                    aiConfig.getBedrock().getTemperature()
            );

            response.setDurationMs(System.currentTimeMillis() - startTime);
            response.addMetadata("framework", request.getFramework());
            response.addMetadata("language", request.getLanguage());
            response.addMetadata("targetUrl", request.getTargetUrl());

            return response;

        } catch (Exception e) {
            log.error("Failed to generate test with Bedrock: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to generate test: " + e.getMessage(),
                    AIProvider.BEDROCK,
                    AITaskType.TEST_GENERATION
            );
        }
    }

    @Override
    public AIResponse analyzeFailure(FailureAnalysisRequest request) {
        log.info("Bedrock AI: Analyzing failure for test: {}", request.getTestName());

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildFailureAnalysisPrompt(request);

            AIResponse response = invokeModel(
                    prompt,
                    AITaskType.FAILURE_ANALYSIS,
                    aiConfig.getBedrock().getMaxTokens(),
                    aiConfig.getBedrock().getTemperature()
            );

            response.setDurationMs(System.currentTimeMillis() - startTime);
            response.addMetadata("testName", request.getTestName());
            response.addMetadata("executionId", request.getExecutionId());

            return response;

        } catch (Exception e) {
            log.error("Failed to analyze failure with Bedrock: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to analyze failure: " + e.getMessage(),
                    AIProvider.BEDROCK,
                    AITaskType.FAILURE_ANALYSIS
            );
        }
    }

    @Override
    public AIResponse suggestFix(String testCode, String errorMessage) {
        log.info("Bedrock AI: Suggesting fix for error");

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildFixSuggestionPrompt(testCode, errorMessage);

            AIResponse response = invokeModel(
                    prompt,
                    AITaskType.FIX_SUGGESTION,
                    aiConfig.getBedrock().getMaxTokens(),
                    aiConfig.getBedrock().getTemperature()
            );

            response.setDurationMs(System.currentTimeMillis() - startTime);

            return response;

        } catch (Exception e) {
            log.error("Failed to suggest fix with Bedrock: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to suggest fix: " + e.getMessage(),
                    AIProvider.BEDROCK,
                    AITaskType.FIX_SUGGESTION
            );
        }
    }

    @Override
    public AIResponse execute(AIRequest request) {
        log.info("Bedrock AI: Executing custom task: {}", request.getTaskType());

        long startTime = System.currentTimeMillis();

        try {
            Integer maxTokens = request.getMaxTokens() != null ?
                    request.getMaxTokens() : aiConfig.getBedrock().getMaxTokens();

            Double temperature = request.getTemperature() != null ?
                    request.getTemperature() : aiConfig.getBedrock().getTemperature();

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
            log.error("Failed to execute custom task with Bedrock: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to execute task: " + e.getMessage(),
                    AIProvider.BEDROCK,
                    request.getTaskType()
            );
        }
    }

    // ========== Private Helper Methods ==========

    private AIResponse invokeModel(String prompt, AITaskType taskType,
                                   Integer maxTokens, Double temperature) throws Exception {

        if (bedrockClient == null) {
            throw new IllegalStateException("Bedrock client not initialized");
        }

        // Build request
        BedrockRequest bedrockRequest = BedrockRequest.createUserMessage(
                prompt, maxTokens, temperature);

        String requestBody = objectMapper.writeValueAsString(bedrockRequest);

        log.debug("Invoking Bedrock model: {}", aiConfig.getBedrock().getModel());
        log.debug("Request: {}", requestBody);

        // Invoke model
        InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                .modelId(aiConfig.getBedrock().getModel())
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();

        InvokeModelResponse invokeResponse = bedrockClient.invokeModel(invokeRequest);

        // Parse response
        String responseBody = invokeResponse.body().asUtf8String();
        log.debug("Response: {}", responseBody);

        BedrockResponse bedrockResponse = objectMapper.readValue(
                responseBody, BedrockResponse.class);

        // Build AI response
        return AIResponse.builder()
                .success(true)
                .content(bedrockResponse.getTextContent())
                .taskType(taskType)
                .provider(AIProvider.BEDROCK)
                .tokensUsed(bedrockResponse.getTotalTokens())
                .generatedAt(Instant.now())
                .build();
    }

    private String buildTestGenerationPrompt(TestGenerationRequest request) {
        return String.format("""
                You are an expert test automation engineer.
                Generate a %s test in %s for the following scenario:
                
                Description: %s
                Target URL: %s
                
                Requirements:
                - Use Page Object Model pattern
                - Include proper waits and assertions
                - Add meaningful comments
                - Handle potential failures gracefully
                - Follow best practices for %s
                
                Return ONLY the code, no explanations.
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
            You are an expert test automation engineer analyzing a test failure.
            
            Test Name: %s
            Error Message: %s
            Stack Trace: %s
            
            Test Code:
            %s
            
            Please provide:
            1. Root cause analysis
            2. Probable causes (ranked by likelihood)
            3. Specific recommendations to fix
            4. Prevention strategies for future
            
            Format your response as a structured analysis with clear sections.
            """,
                request.getTestName(),
                request.getErrorMessage(),
                request.getStackTrace() != null ? request.getStackTrace() : "Not available",
                request.getTestCode() != null ? request.getTestCode() : "Not available"
        );
    }

    private String buildFixSuggestionPrompt(String testCode, String errorMessage) {
        return String.format("""
                You are an expert test automation engineer.
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