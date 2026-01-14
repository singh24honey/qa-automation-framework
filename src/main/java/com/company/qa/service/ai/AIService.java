package com.company.qa.service.ai;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.AIProvider;

public interface AIService {

    /**
     * Get the provider type for this implementation
     */
    AIProvider getProvider();

    /**
     * Check if the AI service is available and healthy
     */
    boolean isAvailable();

    /**
     * Generate a test from natural language description
     * @param request Test generation request
     * @return AI response with generated test code
     */
    AIResponse generateTest(TestGenerationRequest request);

    /**
     * Analyze a test failure and provide insights
     * @param request Failure analysis request
     * @return AI response with failure analysis
     */
    AIResponse analyzeFailure(FailureAnalysisRequest request);

    /**
     * Suggest fixes for a failing test
     * @param testCode The failing test code
     * @param errorMessage The error message
     * @return AI response with suggested fixes
     */
    AIResponse suggestFix(String testCode, String errorMessage);

    /**
     * Generic AI request for custom tasks
     * @param request Generic AI request
     * @return AI response
     */
    AIResponse execute(AIRequest request);
}