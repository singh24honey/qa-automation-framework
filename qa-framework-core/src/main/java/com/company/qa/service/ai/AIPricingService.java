package com.company.qa.service.ai;

import com.company.qa.model.enums.AIProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for AI provider pricing calculations.
 *
 * Pricing as of January 2025:
 * - AWS Bedrock (Amazon Nova Micro): $0.000035/1K input tokens, $0.00014/1K output tokens
 * - Ollama: Free (self-hosted)
 */
@Service
@Slf4j
public class AIPricingService {

    // AWS Bedrock Pricing (Amazon Nova Micro)
    private static final BigDecimal BEDROCK_INPUT_COST_PER_1K = new BigDecimal("0.000035");
    private static final BigDecimal BEDROCK_OUTPUT_COST_PER_1K = new BigDecimal("0.00014");

    // Ollama Pricing (Free)
    private static final BigDecimal OLLAMA_COST = BigDecimal.ZERO;

    /**
     * Get cost per token for prompt (input).
     */
    public BigDecimal getPromptCostPerToken(AIProvider provider) {
        return switch (provider) {
            case BEDROCK -> BEDROCK_INPUT_COST_PER_1K.divide(
                    BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            case OLLAMA, MOCK -> BigDecimal.ZERO;
        };
    }

    /**
     * Get cost per token for completion (output).
     */
    public BigDecimal getCompletionCostPerToken(AIProvider provider) {
        return switch (provider) {
            case BEDROCK -> BEDROCK_OUTPUT_COST_PER_1K.divide(
                    BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            case OLLAMA, MOCK -> BigDecimal.ZERO;
        };
    }

    /**
     * Calculate total cost for a request.
     */
    public BigDecimal calculateCost(AIProvider provider, int promptTokens, int completionTokens) {
        BigDecimal promptCost = getPromptCostPerToken(provider)
                .multiply(BigDecimal.valueOf(promptTokens));

        BigDecimal completionCost = getCompletionCostPerToken(provider)
                .multiply(BigDecimal.valueOf(completionTokens));

        return promptCost.add(completionCost).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Estimate cost for a given number of total tokens.
     * Assumes 60% input, 40% output ratio.
     */
    public BigDecimal estimateCost(AIProvider provider, int totalTokens) {
        int estimatedPromptTokens = (int) (totalTokens * 0.6);
        int estimatedCompletionTokens = (int) (totalTokens * 0.4);
        return calculateCost(provider, estimatedPromptTokens, estimatedCompletionTokens);
    }

    /**
     * Get display name for provider.
     */
    public String getProviderDisplayName(AIProvider provider) {
        return provider.getDisplayName();
    }

    /**
     * Check if provider has costs.
     */
    public boolean hasProviderCost(AIProvider provider) {
        return provider.hasCost();
    }
}