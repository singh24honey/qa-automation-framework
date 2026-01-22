package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetStatusDTO {

    private BigDecimal dailySpend;
    private BigDecimal dailyLimit;
    private BigDecimal dailyRemaining;
    private Double dailyPercentageUsed;

    private BigDecimal weeklySpend;
    private BigDecimal weeklyLimit;
    private BigDecimal weeklyRemaining;
    private Double weeklyPercentageUsed;

    private BigDecimal monthlySpend;
    private BigDecimal monthlyLimit;
    private BigDecimal monthlyRemaining;
    private Double monthlyPercentageUsed;

    private Boolean hasActiveAlerts;
    private List<String> activeAlerts;

    private String status;  // OK, WARNING, CRITICAL, EXCEEDED
}