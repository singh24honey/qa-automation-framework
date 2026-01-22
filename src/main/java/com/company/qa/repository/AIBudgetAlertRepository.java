package com.company.qa.repository;

import com.company.qa.model.entity.AIBudgetAlert;
import com.company.qa.model.enums.BudgetAlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AIBudgetAlertRepository extends JpaRepository<AIBudgetAlert, UUID> {

    // Find by budget ID
    List<AIBudgetAlert> findByBudgetIdOrderByCreatedAtDesc(UUID budgetId);

    // Find unnotified alerts
    List<AIBudgetAlert> findByNotifiedFalseOrderByCreatedAtDesc();

    // Find by alert type
    List<AIBudgetAlert> findByAlertTypeOrderByCreatedAtDesc(BudgetAlertType alertType);

    // Find recent alerts for budget
    @Query("SELECT a FROM AIBudgetAlert a WHERE a.budgetId = :budgetId " +
            "AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<AIBudgetAlert> findRecentAlerts(
            @Param("budgetId") UUID budgetId,
            @Param("since") Instant since);

    // Check if alert exists for budget and period
    @Query("SELECT COUNT(a) > 0 FROM AIBudgetAlert a " +
            "WHERE a.budgetId = :budgetId " +
            "AND a.periodType = :periodType " +
            "AND a.alertType = :alertType " +
            "AND a.createdAt > :since")
    boolean existsRecentAlert(
            @Param("budgetId") UUID budgetId,
            @Param("periodType") String periodType,
            @Param("alertType") BudgetAlertType alertType,
            @Param("since") Instant since);
}