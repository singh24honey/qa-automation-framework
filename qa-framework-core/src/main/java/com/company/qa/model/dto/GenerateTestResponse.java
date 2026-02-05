package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response after test generation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTestResponse {

    private UUID testId;
    private String testName;
    private String status; // PENDING_APPROVAL, GENERATED, FAILED

    private UUID approvalRequestId;
    private String approvalStatus;

    // Context info
    private Long endpointId;
    private String endpointPath;
    private String httpMethod;

    // Generation metadata
    private String aiModel;
    private BigDecimal estimatedCost;
    private Integer promptTokens;
    private Integer responseTokens;

    private Instant generatedAt;

    // Preview (first 200 chars of generated code)
    private String codePreview;
}