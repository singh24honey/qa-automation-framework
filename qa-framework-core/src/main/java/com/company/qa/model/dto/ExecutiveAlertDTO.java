package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveAlertDTO {

    private Long id;
    private String alertType;
    private String severity;
    private String title;
    private String description;

    private String metricName;
    private BigDecimal currentValue;
    private BigDecimal thresholdValue;
    private BigDecimal deviationPct;

    private String affectedEntityType;
    private Long affectedEntityId;

    private String status;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private Instant detectedAt;

    private Long ageInHours;
    private String priorityLevel;
}