package com.company.qa.service.ai;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAIService implements AIService {

    @Override
    public AIProvider getProvider() {
        return AIProvider.MOCK;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AIResponse generateTest(TestGenerationRequest request) {
        log.info("Mock AI: Generating test for: {}", request.getDescription());

        long startTime = System.currentTimeMillis();

        // Simulate processing time
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String generatedCode = generateMockTestCode(request);

        AIResponse response = AIResponse.builder()
                .success(true)
                .content(generatedCode)
                .taskType(AITaskType.TEST_GENERATION)
                .provider(AIProvider.MOCK)
                .tokensUsed(100)
                .durationMs(System.currentTimeMillis() - startTime)
                .generatedAt(Instant.now())
                .build();

        response.addMetadata("framework", request.getFramework());
        response.addMetadata("language", request.getLanguage());

        return response;
    }

    @Override
    public AIResponse analyzeFailure(FailureAnalysisRequest request) {
        log.info("Mock AI: Analyzing failure for test: {}", request.getTestName());

        long startTime = System.currentTimeMillis();

        // Simulate processing time
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String analysis = generateMockFailureAnalysis(request);

        return AIResponse.builder()
                .success(true)
                .content(analysis)
                .taskType(AITaskType.FAILURE_ANALYSIS)
                .provider(AIProvider.MOCK)
                .tokensUsed(150)
                .durationMs(System.currentTimeMillis() - startTime)
                .generatedAt(Instant.now())
                .build();
    }

    @Override
    public AIResponse suggestFix(String testCode, String errorMessage) {
        log.info("Mock AI: Suggesting fix for error: {}",
                errorMessage.substring(0, Math.min(50, errorMessage.length())));

        long startTime = System.currentTimeMillis();

        // Simulate processing time
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String suggestion = generateMockFixSuggestion(errorMessage);

        return AIResponse.builder()
                .success(true)
                .content(suggestion)
                .taskType(AITaskType.FIX_SUGGESTION)
                .provider(AIProvider.MOCK)
                .tokensUsed(120)
                .durationMs(System.currentTimeMillis() - startTime)
                .generatedAt(Instant.now())
                .build();
    }

    @Override
    public AIResponse execute(AIRequest request) {
        log.info("Mock AI: Executing custom task: {}", request.getTaskType());

        long startTime = System.currentTimeMillis();

        // Simulate processing time
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String content = "Mock AI response for task: " + request.getTaskType() +
                "\nPrompt: " + request.getPrompt();

        return AIResponse.builder()
                .success(true)
                .content(content)
                .taskType(request.getTaskType())
                .provider(AIProvider.MOCK)
                .tokensUsed(80)
                .durationMs(System.currentTimeMillis() - startTime)
                .generatedAt(Instant.now())
                .build();
    }

    // ========== Private Helper Methods ==========

    private String generateMockTestCode(TestGenerationRequest request) {
        return String.format("""
                // Mock Generated Test
                // Description: %s
                // Framework: %s
                // Language: %s
                
                @Test
                public void testGenerated() {
                    // Navigate to %s
                    driver.get("%s");
                    
                    // Mock test implementation
                    // TODO: Replace with actual test steps
                    
                    // This is a MOCK response from Mock AI Provider
                    // Real AI providers (Bedrock/Ollama) will generate actual test code
                    
                    System.out.println("Mock test executed successfully");
                }
                """,
                request.getDescription(),
                request.getFramework(),
                request.getLanguage(),
                request.getTargetUrl(),
                request.getTargetUrl()
        );
    }

    private String generateMockFailureAnalysis(FailureAnalysisRequest request) {
        return String.format("""
                # Failure Analysis (Mock AI)
                
                ## Test Information
                - Test: %s
                - Execution ID: %s
                
                ## Error Summary
                %s
                
                ## Probable Causes
                1. Element locator might have changed
                2. Page load timing issue
                3. Network connectivity problem
                
                ## Recommendations
                1. Verify element selector is still valid
                2. Add explicit waits before element interaction
                3. Check if page structure has changed
                4. Review recent application changes
                
                ## Confidence Level
                Medium (This is a mock analysis)
                
                Note: This is a MOCK analysis. Real AI providers will provide detailed insights.
                """,
                request.getTestName(),
                request.getExecutionId(),
                request.getErrorMessage()
        );
    }

    private String generateMockFixSuggestion(String errorMessage) {
        String suggestion = "Mock Fix Suggestions:\n\n";

        if (errorMessage.toLowerCase().contains("timeout")) {
            suggestion += "1. Increase timeout duration\n";
            suggestion += "2. Add explicit wait for element\n";
            suggestion += "3. Check network conditions\n";
        } else if (errorMessage.toLowerCase().contains("element not found")) {
            suggestion += "1. Verify element selector is correct\n";
            suggestion += "2. Check if element is in iframe\n";
            suggestion += "3. Wait for element to be visible\n";
        } else {
            suggestion += "1. Review error message carefully\n";
            suggestion += "2. Check test execution logs\n";
            suggestion += "3. Verify test environment setup\n";
        }

        suggestion += "\nNote: These are mock suggestions. Real AI will provide detailed fixes.";

        return suggestion;
    }
}