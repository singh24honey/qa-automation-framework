package com.company.qa.service.ai;

import com.company.qa.config.AIConfig;
import com.company.qa.model.dto.*;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;


import java.time.Instant;
import java.util.HashMap;

/**
 * AWS Bedrock AI Service with multi-model support.
 *
 * Supports:
 * - Amazon Nova 2 (Lite, Sonic, Pro, Premier)
 * - Amazon Titan (Text Express, Text Lite)
 * - Anthropic Claude (3 Haiku, 3 Sonnet, 3.5 Sonnet)
 * - AI21 Jurassic
 * - Meta Llama
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "bedrock")
public class BedrockAIService implements AIService {

    private final AIConfig aiConfig;
    private final ObjectMapper objectMapper;

    private BedrockRuntimeClient bedrockClient;
    private ModelType modelType;

    @PostConstruct
    public void init() {
        if (!aiConfig.getBedrock().isEnabled()) {
            log.warn("Bedrock AI is disabled in configuration");
            return;
        }

        String modelId = aiConfig.getBedrock().getModel();
        this.modelType = detectModelType(modelId);

        log.info("Initializing AWS Bedrock AI Service");
        log.info("Region: {}", aiConfig.getBedrock().getRegion());
        log.info("Model: {} (Type: {})", modelId, modelType);

        try {
            AIConfig.BedrockConfig config = aiConfig.getBedrock();

            // Initialize client
            if (config.getAccessKeyId() == null || config.getAccessKeyId().isEmpty()) {
                log.warn("AWS credentials not provided. Using default credential chain.");
                bedrockClient = BedrockRuntimeClient.builder()
                        .region(Region.of(config.getRegion()))
                        .build();
            } else {
                AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                        config.getAccessKeyId(),
                        config.getSecretAccessKey()
                );

                bedrockClient = BedrockRuntimeClient.builder()
                        .region(Region.of(config.getRegion()))
                        .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                        .build();
            }

            log.info("Bedrock client initialized successfully for {}", modelType);

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
            try {
                bedrockClient.close();
                log.info("Closed Bedrock client");
            } catch (Exception e) {
                log.warn("Error closing Bedrock client", e);
            }
        }
    }

    /**
     * Detect model type from model ID
     */
    private ModelType detectModelType(String modelId) {
        if (modelId.contains("nova")) {
            return ModelType.AMAZON_NOVA;
        } else if (modelId.contains("claude")) {
            return ModelType.ANTHROPIC_CLAUDE;
        } else if (modelId.contains("titan")) {
            return ModelType.AMAZON_TITAN;
        } else if (modelId.contains("ai21") || modelId.contains("jamba") || modelId.contains("jurassic")) {
            return ModelType.AI21_JURASSIC;
        } else if (modelId.contains("llama")) {
            return ModelType.META_LLAMA;
        } else {
            log.warn("Unknown model type for: {}. Defaulting to AMAZON_NOVA", modelId);
            return ModelType.AMAZON_NOVA;
        }
    }

    private enum ModelType {
        AMAZON_NOVA,
        AMAZON_TITAN,
        ANTHROPIC_CLAUDE,
        AI21_JURASSIC,
        META_LLAMA,
        UNKNOWN
    }

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
            // Quick health check with minimal tokens
            String testPrompt = "Hello";
            AIResponse response = invokeModel(testPrompt, AITaskType.GENERAL, 10, 0.1);
            return response.isSuccess();
        } catch (Exception e) {
            log.error("Bedrock health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ========== AI Service Interface Methods ==========

    @Override
    public AIResponse generateTest(TestGenerationRequest request) {
        log.info("Bedrock AI [{}]: Generating test for: {}", modelType, request.getDescription());

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildTestGenerationPrompt(request);

            // TestGenerationRequest doesn't have maxTokens/temperature, use config defaults
            AIResponse response = invokeModel(
                    prompt,
                    AITaskType.TEST_GENERATION,
                    aiConfig.getBedrock().getMaxTokens(),
                    aiConfig.getBedrock().getTemperature()
            );

            // ✅ NEW: Extract token breakdown from response metadata
            // The invokeModel() should have already added tokens to metadata
            // But let's ensure we have the breakdown for cost tracking
            if (response.getMetadata() != null) {
                Object promptTokensObj = response.getMetadata().get("promptTokens");
                Object completionTokensObj = response.getMetadata().get("completionTokens");

                if (promptTokensObj != null && completionTokensObj != null) {
                    int promptTokens = ((Number) promptTokensObj).intValue();
                    int completionTokens = ((Number) completionTokensObj).intValue();
                    int totalTokens = promptTokens + completionTokens;

                    // Ensure tokensUsed is set for backward compatibility
                    if (response.getTokensUsed() == null || response.getTokensUsed() == 0) {
                        response.setTokensUsed(totalTokens);
                    }

                    // Also add totalTokens to metadata
                    response.addMetadata("totalTokens", totalTokens);

                    log.debug("Token breakdown - Prompt: {}, Completion: {}, Total: {}",
                            promptTokens, completionTokens, totalTokens);
                }
            }

            response.setDurationMs(System.currentTimeMillis() - startTime);
            response.addMetadata("framework", request.getFramework());
            response.addMetadata("language", request.getLanguage());
            response.addMetadata("targetUrl", request.getTargetUrl());

            return response;

        } catch (Exception e) {
            log.error("Failed to generate test: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to generate test: " + e.getMessage(),
                    AIProvider.BEDROCK,
                    AITaskType.TEST_GENERATION
            );
        }
    }
    @Override
    public AIResponse analyzeFailure(FailureAnalysisRequest request) {
        log.info("Bedrock AI [{}]: Analyzing failure for test: {}", modelType, request.getTestName());

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildFailureAnalysisPrompt(request);

            // FailureAnalysisRequest doesn't have maxTokens/temperature, use config defaults
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
            log.error("Failed to analyze failure: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to analyze failure: " + e.getMessage(),
                    AIProvider.BEDROCK,
                    AITaskType.FAILURE_ANALYSIS
            );
        }
    }

    @Override
    public AIResponse suggestFix(String testCode, String errorMessage) {
        log.info("Bedrock AI [{}]: Suggesting fix for error", modelType);

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
            log.error("Failed to suggest fix: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to suggest fix: " + e.getMessage(),
                    AIProvider.BEDROCK,
                    AITaskType.FIX_SUGGESTION
            );
        }
    }

    @Override
    public AIResponse execute(AIRequest request) {
        log.info("Bedrock AI [{}]: Executing custom task: {}", modelType, request.getTaskType());

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
            log.error("Failed to execute custom task: {}", e.getMessage(), e);
            return AIResponse.error(
                    "Failed to execute task: " + e.getMessage(),
                    AIProvider.BEDROCK,
                    request.getTaskType()
            );
        }
    }

    // ========== Model Invocation ==========

    // 1. UPDATE invokeModel()
    private AIResponse invokeModel(String prompt, AITaskType taskType,
                                   Integer maxTokens, Double temperature) throws Exception {
        if (bedrockClient == null) {
            throw new IllegalStateException("Bedrock client not initialized");
        }

        String requestBody = buildRequestBody(prompt, maxTokens, temperature);

        log.debug("Invoking model: {} with request: {}", aiConfig.getBedrock().getModel(),
                requestBody.substring(0, Math.min(200, requestBody.length())) + "...");

        InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                .modelId(aiConfig.getBedrock().getModel())
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();

        InvokeModelResponse invokeResponse = bedrockClient.invokeModel(invokeRequest);

        String responseBody = invokeResponse.body().asUtf8String();
        log.debug("Response: {}", responseBody.substring(0, Math.min(500, responseBody.length())) + "...");

        AIResponse response = parseResponse(responseBody, taskType);

        // ✅ NEW: Extract and add token usage
        extractAndAddTokenUsage(responseBody, response);

        return response;
    }

// 2. ADD new helper method
    /**
     * Extract token usage from Bedrock response and add to AIResponse metadata.
     */
    private void extractAndAddTokenUsage(String responseBody, AIResponse response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonResponse = mapper.readTree(responseBody);

            int promptTokens = 0;
            int completionTokens = 0;

            // Amazon Nova response structure
            if (jsonResponse.has("usage")) {
                JsonNode usage = jsonResponse.get("usage");
                promptTokens = usage.has("inputTokens") ? usage.get("inputTokens").asInt() : 0;
                completionTokens = usage.has("outputTokens") ? usage.get("outputTokens").asInt() : 0;
            }

            int totalTokens = promptTokens + completionTokens;

            // Add to response metadata
            if (response.getMetadata() == null) {
                response.setMetadata(new HashMap<>());
            }

            response.addMetadata("promptTokens", promptTokens);
            response.addMetadata("completionTokens", completionTokens);
            response.addMetadata("totalTokens", totalTokens);

            // Also set tokensUsed field
            if (response.getTokensUsed() == null || response.getTokensUsed() == 0) {
                response.setTokensUsed(totalTokens);
            }

            log.debug("Token usage - Input: {}, Output: {}, Total: {}",
                    promptTokens, completionTokens, totalTokens);

        } catch (Exception e) {
            log.warn("Could not extract token usage: {}", e.getMessage());
            // Don't fail - tracking service will estimate if needed
        }
    }
    // ========== Request Building (Model-Specific) ==========

    /**
     * Build request body based on model type.
     * Each model family has different request format.
     */
    private String buildRequestBody(String prompt, Integer maxTokens, Double temperature) throws Exception {
        switch (modelType) {
            case AMAZON_NOVA:
                return buildNovaRequest(prompt, maxTokens, temperature);

            case AMAZON_TITAN:
                return buildTitanRequest(prompt, maxTokens, temperature);

            case AI21_JURASSIC:
                return buildAI21Request(prompt, maxTokens, temperature);

            case ANTHROPIC_CLAUDE:
                return buildClaudeRequest(prompt, maxTokens, temperature);

            case META_LLAMA:
                return buildLlamaRequest(prompt, maxTokens, temperature);

            default:
                return buildNovaRequest(prompt, maxTokens, temperature);
        }
    }

    /**
     * Amazon Nova Request Format (Converse API)
     * Used by: amazon.nova-2-lite-v1:0, nova-2-sonic, nova-pro, nova-premier
     */
    private String buildNovaRequest(String prompt, Integer maxTokens, Double temperature) throws Exception {
        ObjectNode requestNode = objectMapper.createObjectNode();

        // Messages array with user message
        ArrayNode messagesNode = requestNode.putArray("messages");
        ObjectNode messageNode = messagesNode.addObject();
        messageNode.put("role", "user");

        ArrayNode contentNode = messageNode.putArray("content");
        ObjectNode textNode = contentNode.addObject();
        textNode.put("text", prompt);

        // Inference configuration
        ObjectNode inferenceConfig = requestNode.putObject("inferenceConfig");
        inferenceConfig.put("max_new_tokens", maxTokens);
        inferenceConfig.put("temperature", temperature);
        inferenceConfig.put("top_p", 0.9);

        return objectMapper.writeValueAsString(requestNode);
    }

    /**
     * Amazon Titan Text Request Format
     */
    private String buildTitanRequest(String prompt, Integer maxTokens, Double temperature) throws Exception {
        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("inputText", prompt);

        ObjectNode configNode = requestNode.putObject("textGenerationConfig");
        configNode.put("maxTokenCount", maxTokens);
        configNode.put("temperature", temperature);
        configNode.put("topP", 0.9);
        configNode.putArray("stopSequences");

        return objectMapper.writeValueAsString(requestNode);
    }

    /**
     * AI21 Jurassic Request Format
     */
    private String buildAI21Request(String prompt, Integer maxTokens, Double temperature) throws Exception {
        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("prompt", prompt);
        requestNode.put("maxTokens", maxTokens);
        requestNode.put("temperature", temperature);
        requestNode.put("topP", 0.9);
        requestNode.putArray("stopSequences");

        return objectMapper.writeValueAsString(requestNode);
    }

    /**
     * Anthropic Claude Request Format (Messages API)
     */
    private String buildClaudeRequest(String prompt, Integer maxTokens, Double temperature) throws Exception {
        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("anthropic_version", "bedrock-2023-05-31");
        requestNode.put("max_tokens", maxTokens);
        requestNode.put("temperature", temperature);

        ArrayNode messagesNode = requestNode.putArray("messages");
        ObjectNode messageNode = messagesNode.addObject();
        messageNode.put("role", "user");
        messageNode.put("content", prompt);

        return objectMapper.writeValueAsString(requestNode);
    }

    /**
     * Meta Llama Request Format
     */
    private String buildLlamaRequest(String prompt, Integer maxTokens, Double temperature) throws Exception {
        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("prompt", prompt);
        requestNode.put("max_gen_len", maxTokens);
        requestNode.put("temperature", temperature);
        requestNode.put("top_p", 0.9);

        return objectMapper.writeValueAsString(requestNode);
    }

    // ========== Response Parsing (Model-Specific) ==========

    /**
     * Parse response based on model type.
     * Each model family has different response format.
     */
    private AIResponse parseResponse(String responseBody, AITaskType taskType) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);

        String content;
        int tokensUsed = 0;

        switch (modelType) {
            case AMAZON_NOVA:
                content = parseNovaResponse(rootNode);
                tokensUsed = extractNovaTokens(rootNode);
                break;

            case AMAZON_TITAN:
                content = parseTitanResponse(rootNode);
                tokensUsed = extractTitanTokens(rootNode);
                break;

            case AI21_JURASSIC:
                content = parseAI21Response(rootNode);
                tokensUsed = extractAI21Tokens(rootNode);
                break;

            case ANTHROPIC_CLAUDE:
                content = parseClaudeResponse(rootNode);
                tokensUsed = extractClaudeTokens(rootNode);
                break;

            case META_LLAMA:
                content = parseLlamaResponse(rootNode);
                tokensUsed = extractLlamaTokens(rootNode);
                break;

            default:
                content = parseNovaResponse(rootNode);
                tokensUsed = extractNovaTokens(rootNode);
        }

        return AIResponse.builder()
                .success(true)
                .content(content)
                .provider(AIProvider.BEDROCK)
                .taskType(taskType)
                .tokensUsed(tokensUsed)
                .generatedAt(Instant.now())
                .build();
    }

    // Nova parsing
    private String parseNovaResponse(JsonNode rootNode) {
        JsonNode outputNode = rootNode.path("output").path("message");
        if (!outputNode.isMissingNode()) {
            JsonNode contentArray = outputNode.path("content");
            if (contentArray.isArray() && contentArray.size() > 0) {
                return contentArray.get(0).path("text").asText("");
            }
        }
        return "";
    }

    private int extractNovaTokens(JsonNode rootNode) {
        JsonNode usageNode = rootNode.path("usage");
        int inputTokens = usageNode.path("inputTokens").asInt(0);
        int outputTokens = usageNode.path("outputTokens").asInt(0);
        return inputTokens + outputTokens;
    }

    // Titan parsing
    private String parseTitanResponse(JsonNode rootNode) {
        JsonNode resultsNode = rootNode.path("results");
        if (resultsNode.isArray() && resultsNode.size() > 0) {
            return resultsNode.get(0).path("outputText").asText("");
        }
        return "";
    }

    private int extractTitanTokens(JsonNode rootNode) {
        return rootNode.path("inputTextTokenCount").asInt(0) +
                rootNode.path("results").path(0).path("tokenCount").asInt(0);
    }

    // AI21 parsing
    private String parseAI21Response(JsonNode rootNode) {
        JsonNode completionsNode = rootNode.path("completions");
        if (completionsNode.isArray() && completionsNode.size() > 0) {
            return completionsNode.get(0).path("data").path("text").asText("");
        }
        return "";
    }

    private int extractAI21Tokens(JsonNode rootNode) {
        return rootNode.path("prompt").path("tokens").size() +
                rootNode.path("completions").path(0).path("data").path("tokens").size();
    }

    // Claude parsing
    private String parseClaudeResponse(JsonNode rootNode) {
        JsonNode contentNode = rootNode.path("content");
        if (contentNode.isArray() && contentNode.size() > 0) {
            return contentNode.get(0).path("text").asText("");
        }
        return "";
    }

    private int extractClaudeTokens(JsonNode rootNode) {
        JsonNode usageNode = rootNode.path("usage");
        return usageNode.path("input_tokens").asInt(0) +
                usageNode.path("output_tokens").asInt(0);
    }

    // Llama parsing
    private String parseLlamaResponse(JsonNode rootNode) {
        return rootNode.path("generation").asText("");
    }

    private int extractLlamaTokens(JsonNode rootNode) {
        return rootNode.path("prompt_token_count").asInt(0) +
                rootNode.path("generation_token_count").asInt(0);
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