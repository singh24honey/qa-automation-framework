package com.company.qa.service.ai;

import com.company.qa.model.dto.BudgetStatusDTO;
import com.company.qa.model.entity.AIBudget;
import com.company.qa.model.entity.AIBudgetAlert;
import com.company.qa.model.enums.BudgetAlertType;
import com.company.qa.model.enums.BudgetScopeType;
import com.company.qa.repository.AIBudgetAlertRepository;
import com.company.qa.repository.AIBudgetRepository;
import com.company.qa.repository.AIUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing AI budgets and alerts.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AIBudgetService {

    private final AIBudgetRepository budgetRepository;
    private final AIBudgetAlertRepository alertRepository;
    private final AIUsageLogRepository usageLogRepository;

    /**
     * Create a new budget.
     */
    @Transactional
    public AIBudget createBudget(AIBudget budget) {
        log.info("Creating budget: scopeType={}, scopeId={}", budget.getScopeType(), budget.getScopeId());

        // Deactivate existing budget for same scope
        Optional<AIBudget> existing = budgetRepository.findByScopeTypeAndScopeIdAndActiveTrue(
                budget.getScopeType(), budget.getScopeId());

        existing.ifPresent(b -> {
            b.setActive(false);
            budgetRepository.save(b);
        });

        return budgetRepository.save(budget);
    }

    /**
     * Get budget status with current spend.
     */
    @Transactional(readOnly = true)
    public BudgetStatusDTO getBudgetStatus(String scopeId) {
        // Get budget (try user-specific, then global)
        Optional<AIBudget> budgetOpt = scopeId != null
                ? budgetRepository.findUserBudget(scopeId)
                : budgetRepository.findGlobalBudget();

        if (budgetOpt.isEmpty()) {
            return BudgetStatusDTO.builder()
                    .status("NO_BUDGET")
                    .hasActiveAlerts(false)
                    .activeAlerts(new ArrayList<>())
                    .build();
        }

        AIBudget budget = budgetOpt.get();
        UUID userId = scopeId != null ? UUID.fromString(scopeId) : null;

        // Calculate current spend
        LocalDate now = LocalDate.now();

        // Daily
        Instant dayStart = now.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant dayEnd = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        BigDecimal dailySpend = calculateSpend(userId, dayStart, dayEnd);

        // Weekly
        LocalDate weekStart = now.minusDays(now.getDayOfWeek().getValue() - 1);
        Instant weekStartInstant = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        BigDecimal weeklySpend = calculateSpend(userId, weekStartInstant, Instant.now());

        // Monthly
        LocalDate monthStart = now.withDayOfMonth(1);
        Instant monthStartInstant = monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        BigDecimal monthlySpend = calculateSpend(userId, monthStartInstant, Instant.now());

        // Calculate percentages and status
        String status = "OK";
        List<String> activeAlerts = new ArrayList<>();

        if (budget.getDailyLimit() != null && dailySpend.compareTo(budget.getDailyLimit()) > 0) {
            status = "EXCEEDED";
            activeAlerts.add("Daily budget exceeded");
        } else if (budget.getWeeklyLimit() != null && weeklySpend.compareTo(budget.getWeeklyLimit()) > 0) {
            status = "EXCEEDED";
            activeAlerts.add("Weekly budget exceeded");
        } else if (budget.getMonthlyLimit() != null && monthlySpend.compareTo(budget.getMonthlyLimit()) > 0) {
            status = "EXCEEDED";
            activeAlerts.add("Monthly budget exceeded");
        } else {
            // Check for warnings
            double dailyPct = calculatePercentage(dailySpend, budget.getDailyLimit());
            double weeklyPct = calculatePercentage(weeklySpend, budget.getWeeklyLimit());
            double monthlyPct = calculatePercentage(monthlySpend, budget.getMonthlyLimit());

            if (dailyPct >= budget.getCriticalThreshold() ||
                    weeklyPct >= budget.getCriticalThreshold() ||
                    monthlyPct >= budget.getCriticalThreshold()) {
                status = "CRITICAL";
            } else if (dailyPct >= budget.getWarningThreshold() ||
                    weeklyPct >= budget.getWarningThreshold() ||
                    monthlyPct >= budget.getWarningThreshold()) {
                status = "WARNING";
            }
        }

        return BudgetStatusDTO.builder()
                .dailySpend(dailySpend)
                .dailyLimit(budget.getDailyLimit())
                .dailyRemaining(calculateRemaining(dailySpend, budget.getDailyLimit()))
                .dailyPercentageUsed(calculatePercentage(dailySpend, budget.getDailyLimit()))
                .weeklySpend(weeklySpend)
                .weeklyLimit(budget.getWeeklyLimit())
                .weeklyRemaining(calculateRemaining(weeklySpend, budget.getWeeklyLimit()))
                .weeklyPercentageUsed(calculatePercentage(weeklySpend, budget.getWeeklyLimit()))
                .monthlySpend(monthlySpend)
                .monthlyLimit(budget.getMonthlyLimit())
                .monthlyRemaining(calculateRemaining(monthlySpend, budget.getMonthlyLimit()))
                .monthlyPercentageUsed(calculatePercentage(monthlySpend, budget.getMonthlyLimit()))
                .status(status)
                .hasActiveAlerts(!activeAlerts.isEmpty())
                .activeAlerts(activeAlerts)
                .build();
    }

    /**
     * Check budget and create alerts if necessary.
     */
    @Transactional
    public void checkBudgetAndAlert(String scopeId) {
        Optional<AIBudget> budgetOpt = scopeId != null
                ? budgetRepository.findUserBudget(scopeId)
                : budgetRepository.findGlobalBudget();

        if (budgetOpt.isEmpty()) {
            return;
        }

        AIBudget budget = budgetOpt.get();
        BudgetStatusDTO status = getBudgetStatus(scopeId);

        // Check daily budget
        if (budget.getDailyLimit() != null) {
            checkAndCreateAlert(budget, "DAILY", status.getDailySpend(),
                    budget.getDailyLimit(), status.getDailyPercentageUsed());
        }

        // Check weekly budget
        if (budget.getWeeklyLimit() != null) {
            checkAndCreateAlert(budget, "WEEKLY", status.getWeeklySpend(),
                    budget.getWeeklyLimit(), status.getWeeklyPercentageUsed());
        }

        // Check monthly budget
        if (budget.getMonthlyLimit() != null) {
            checkAndCreateAlert(budget, "MONTHLY", status.getMonthlySpend(),
                    budget.getMonthlyLimit(), status.getMonthlyPercentageUsed());
        }
    }

    /**
     * Check and create alert if threshold exceeded.
     */
    private void checkAndCreateAlert(AIBudget budget, String periodType,
                                     BigDecimal currentSpend, BigDecimal limit,
                                     Double percentageUsed) {

        if (percentageUsed == null || percentageUsed < budget.getWarningThreshold()) {
            return;
        }

        BudgetAlertType alertType;
        if (percentageUsed >= 100) {
            alertType = BudgetAlertType.EXCEEDED;
        } else if (percentageUsed >= budget.getCriticalThreshold()) {
            alertType = BudgetAlertType.CRITICAL;
        } else {
            alertType = BudgetAlertType.WARNING;
        }

        // Check if alert already exists in last hour
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        boolean alertExists = alertRepository.existsRecentAlert(
                budget.getId(), periodType, alertType, oneHourAgo);

        if (!alertExists) {
            AIBudgetAlert alert = AIBudgetAlert.builder()
                    .budgetId(budget.getId())
                    .alertType(alertType)
                    .periodType(periodType)
                    .currentSpend(currentSpend)
                    .budgetLimit(limit)
                    .percentageUsed(BigDecimal.valueOf(percentageUsed))
                    .notified(false)
                    .build();

            alertRepository.save(alert);

            log.warn("Budget alert created: type={}, period={}, percentage={}%",
                    alertType, periodType, percentageUsed);
        }
    }

    /**
     * Calculate spend for user or global.
     */
    private BigDecimal calculateSpend(UUID userId, Instant start, Instant end) {
        if (userId != null) {
            return usageLogRepository.sumCostByUserAndDateRange(userId, start, end);
        } else {
            return usageLogRepository.sumCostByDateRange(start, end);
        }
    }

    /**
     * Calculate remaining budget.
     */
    private BigDecimal calculateRemaining(BigDecimal spend, BigDecimal limit) {
        if (limit == null) {
            return null;
        }
        BigDecimal remaining = limit.subtract(spend);
        return remaining.max(BigDecimal.ZERO);
    }

    /**
     * Calculate percentage used.
     */
    private Double calculatePercentage(BigDecimal spend, BigDecimal limit) {
        if (limit == null || limit.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return spend.divide(limit, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    @Transactional(readOnly = true)
    public List<AIBudget> getAllActiveBudgets() {
        return budgetRepository.findByActiveTrue();
    }
}