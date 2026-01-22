package com.company.qa.service.ai;

import com.company.qa.model.entity.AIUsageLog;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import com.company.qa.repository.AIUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for tracking AI usage and costs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AIUsageTrackingService {

    private final AIUsageLogRepository usageLogRepository;
    private final AIPricingService pricingService;

    /**
     * Record AI usage for a request.
     * Uses REQUIRES_NEW to commit independently and @Async to not block.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUsage(AIUsageRequest request) {
        log.info("📊 Recording AI usage: provider={}, taskType={}, userId={}, tokens={}",
                request.getProvider(), request.getTaskType(), request.getUserId(),
                request.getTotalTokens());

        try {
            // Calculate costs
            BigDecimal promptCostPerToken = pricingService.getPromptCostPerToken(request.getProvider());
            BigDecimal completionCostPerToken = pricingService.getCompletionCostPerToken(request.getProvider());
            BigDecimal totalCost = pricingService.calculateCost(
                    request.getProvider(),
                    request.getPromptTokens(),
                    request.getCompletionTokens());

            // Create usage log
            AIUsageLog usageLog = AIUsageLog.builder()
                    .requestId(request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString())
                    .userId(request.getUserId())
                    .userName(request.getUserName())
                    .userRole(request.getUserRole())
                    .provider(request.getProvider())
                    .modelName(request.getModelName() != null ? request.getModelName() : "default")
                    .taskType(request.getTaskType())
                    .promptTokens(request.getPromptTokens())
                    .completionTokens(request.getCompletionTokens())
                    .totalTokens(request.getTotalTokens())
                    .promptCostPerToken(promptCostPerToken)
                    .completionCostPerToken(completionCostPerToken)
                    .totalCost(totalCost)
                    .currency("USD")
                    .requestContentLength(request.getRequestContentLength())
                    .responseContentLength(request.getResponseContentLength())
                    .processingTimeMs(request.getProcessingTimeMs())
                    .success(request.getSuccess() != null ? request.getSuccess() : true)
                    .errorMessage(request.getErrorMessage())
                    .approvalRequestId(request.getApprovalRequestId())
                    .testId(request.getTestId())
                    .executionId(request.getExecutionId())
                    .build();

            usageLog = usageLogRepository.save(usageLog);

            log.info("✅ AI usage recorded: id={}, cost=${}", usageLog.getId(), totalCost);

        } catch (Exception e) {
            log.error("❌ Failed to record AI usage: {}", e.getMessage(), e);
            // Don't rethrow
        }
    }

    /**
     * Record AI usage with auto-calculated total tokens.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUsage(
            AIProvider provider,
            AITaskType taskType,
            int promptTokens,
            int completionTokens,
            UUID userId,
            String userName) {

        AIUsageRequest request = AIUsageRequest.builder()
                .provider(provider)
                .taskType(taskType)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .userId(userId)
                .userName(userName)
                .success(true)
                .build();

        recordUsage(request);
    }

    /**
     * DTO for AI usage requests.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AIUsageRequest {
        private String requestId;
        private UUID userId;
        private String userName;
        private String userRole;

        private AIProvider provider;
        private String modelName;
        private AITaskType taskType;

        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;

        private Integer requestContentLength;
        private Integer responseContentLength;
        private Long processingTimeMs;

        private Boolean success;
        private String errorMessage;

        private UUID approvalRequestId;
        private UUID testId;
        private UUID executionId;
    }
}