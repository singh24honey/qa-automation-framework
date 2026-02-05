package com.company.qa.model.entity;

import com.company.qa.model.enums.BudgetAlertType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Budget alert history.
 */
@Entity
@Table(name = "ai_budget_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIBudgetAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private BudgetAlertType alertType;

    @Column(name = "period_type", nullable = false, length = 50)
    private String periodType;  // DAILY, WEEKLY, MONTHLY

    @Column(name = "current_spend", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentSpend;

    @Column(name = "budget_limit", nullable = false, precision = 10, scale = 2)
    private BigDecimal budgetLimit;

    @Column(name = "percentage_used", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentageUsed;

    // Notification
    @Column(name = "notified")
    private Boolean notified;

    @Column(name = "notification_sent_at")
    private Instant notificationSentAt;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (notified == null) {
            notified = false;
        }
    }
}