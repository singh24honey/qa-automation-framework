package com.company.qa.model.dto;

import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIUsageLogDTO {
    private UUID id;
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

    private BigDecimal totalCost;
    private String currency;

    private Long processingTimeMs;
    private Boolean success;
    private String errorMessage;

    private Instant createdAt;

    private UUID approvalRequestId;
    private UUID testId;
    private UUID executionId;
}