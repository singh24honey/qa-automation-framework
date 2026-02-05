package com.company.qa.service.openapi;

import com.company.qa.model.dto.SecureAIRequest;
import com.company.qa.model.dto.SecureAIResponse;
import com.company.qa.model.entity.ApiEndpoint;
import com.company.qa.model.entity.Test;
import com.company.qa.model.entity.TestGenerationContext;
import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.model.enums.UserRole;
import com.company.qa.repository.TestGenerationContextRepository;
import com.company.qa.repository.TestRepository;
import com.company.qa.service.ai.AIGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Generate AI tests using OpenAPI context
 * CORRECTED VERSION - Uses proper AIGatewayService methods
 */
@Slf4j
@RequiredArgsConstructor
@Service("openAPITestGenerationService")
public class openAPITestGenerationService {

    private final AIGatewayService aiGatewayService;
    private final OpenAPIContextBuilder contextBuilder;
    private final TestRepository testRepository;
    private final TestGenerationContextRepository contextRepository;

    /**
     * Generate test for endpoint with OpenAPI context
     */
    @Transactional
    public GeneratedTestResult generateTestForEndpoint(
            ApiEndpoint endpoint,
            String userPrompt,
            UUID userId,
            String userRole) {

        log.info("Generating test for: {} {}", endpoint.getMethod(), endpoint.getPath());

        // Build enhanced prompt with OpenAPI context
        String enhancedPrompt = contextBuilder.buildEndpointTestPrompt(endpoint, userPrompt);

        log.debug("Enhanced prompt length: {} chars", enhancedPrompt.length());

        // Create secure AI request
        SecureAIRequest aiRequest = SecureAIRequest.builder()
                .userId(userId)
                .userRole(UserRole.valueOf(userRole))
                .content(enhancedPrompt)
                .framework("Selenium")
                .language("Java")
                .targetUrl(endpoint.getSpecification().getBaseUrl())
                .strictMode(false)
                .build();

        // Call AI Gateway (returns SecureAIResponse)
        SecureAIResponse aiResponse = aiGatewayService.generateTest(aiRequest);

        // Check if generation was successful
        if (!aiResponse.isSuccess()) {
            log.error("AI generation failed: {}", aiResponse.getErrorMessage());
            return GeneratedTestResult.builder()
                    .success(false)
                    .errorMessage(aiResponse.getErrorMessage())
                    .build();
        }

        // Extract schemas used
        List<String> schemasUsed = contextBuilder.extractReferencedSchemas(endpoint);

        // Create Test entity with CORRECT fields
        Test test = Test.builder()
                .name(generateTestName(endpoint))
                .description("AI-generated: " + endpoint.getMethod() + " " + endpoint.getPath())
                .framework(TestFramework.SELENIUM)  // ✅ Enum, not String
                .language("Java")  // ✅ Required field
                .priority(Priority.MEDIUM)  // ✅ Set default priority
                .isActive(false)  // Requires approval
                .estimatedDuration(300)  // 5 minutes default
                .build();

        // Note: createdAt is auto-set by BaseEntity @PrePersist

        Test savedTest = testRepository.save(test);
        log.info("Created test ID: {} - {}", savedTest.getId(), savedTest.getName());

        // Save generation context
        TestGenerationContext context = TestGenerationContext.builder()
                .testId(savedTest.getId())
                .specification(endpoint.getSpecification())
                .endpoint(endpoint)
                .promptWithContext(enhancedPrompt)
                .contextType("ENDPOINT")
                .aiModel(extractModelFromResponse(aiResponse))
                .aiCost(BigDecimal.valueOf(aiResponse.getEstimatedCost()))
                .approved(false)
                .build();

        if (!schemasUsed.isEmpty()) {
            context.setSchemasUsed(schemasUsed);
        }

        contextRepository.save(context);
        log.info("Saved generation context for test: {}", savedTest.getId());

        // Return result
        return GeneratedTestResult.builder()
                .success(true)
                .testId(savedTest.getId())
                .testName(savedTest.getName())
                .generatedCode(aiResponse.getContent())
                .contextUsed(enhancedPrompt)
                .aiModel(extractModelFromResponse(aiResponse))
                .aiCost(BigDecimal.valueOf(aiResponse.getEstimatedCost()))
                .tokensUsed(aiResponse.getTokensUsed())
                .contextId(context.getId())
                .build();
    }

    /**
     * Generate test name from endpoint
     */
    private String generateTestName(ApiEndpoint endpoint) {
        String method = endpoint.getMethod().toLowerCase();
        String path = endpoint.getPath()
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_|_$", "");

        return "test_" + method + "_" + path;
    }

    /**
     * Extract model name from response (fallback to default)
     */
    private String extractModelFromResponse(SecureAIResponse response) {
        // The SecureAIResponse doesn't include model name
        // We need to get this from configuration
        return "amazon-nova-lite-v1"; // Default for now
    }

    /**
     * Result of test generation
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GeneratedTestResult {
        private Boolean success;
        private String errorMessage;

        // Test details
        private UUID testId;
        private String testName;
        private String generatedCode;

        // Context
        private String contextUsed;
        private Long contextId;

        // AI metadata
        private String aiModel;
        private BigDecimal aiCost;
        private Integer tokensUsed;
    }
}