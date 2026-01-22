package com.company.qa.model.entity;

import com.company.qa.model.enums.BudgetScopeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * AI spending budget configuration.
 */
@Entity
@Table(name = "ai_budgets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 50)
    private BudgetScopeType scopeType;

    @Column(name = "scope_id", length = 100)
    private String scopeId;

    @Column(name = "scope_name", length = 100)
    private String scopeName;

    // Budget Limits
    @Column(name = "daily_limit", precision = 10, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "weekly_limit", precision = 10, scale = 2)
    private BigDecimal weeklyLimit;

    @Column(name = "monthly_limit", precision = 10, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(name = "currency", length = 3)
    private String currency;

    // Alert Thresholds
    @Column(name = "warning_threshold")
    private Integer warningThreshold;

    @Column(name = "critical_threshold")
    private Integer criticalThreshold;

    // Status
    @Column(name = "active")
    private Boolean active;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (currency == null) {
            currency = "USD";
        }
        if (warningThreshold == null) {
            warningThreshold = 80;
        }
        if (criticalThreshold == null) {
            criticalThreshold = 95;
        }
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}