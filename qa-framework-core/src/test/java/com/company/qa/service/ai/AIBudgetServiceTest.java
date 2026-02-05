package com.company.qa.service.ai;

import com.company.qa.model.dto.BudgetStatusDTO;
import com.company.qa.model.entity.AIBudget;
import com.company.qa.model.enums.BudgetScopeType;
import com.company.qa.repository.AIBudgetAlertRepository;
import com.company.qa.repository.AIBudgetRepository;
import com.company.qa.repository.AIUsageLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIBudgetServiceTest {

    @Mock
    private AIBudgetRepository budgetRepository;

    @Mock
    private AIBudgetAlertRepository alertRepository;

    @Mock
    private AIUsageLogRepository usageLogRepository;

    @InjectMocks
    private AIBudgetService budgetService;

    @Test
    void testCreateBudget_Success() {
        // Arrange
        AIBudget budget = AIBudget.builder()
                .scopeType(BudgetScopeType.GLOBAL)
                .monthlyLimit(new BigDecimal("100.00"))
                .build();

        when(budgetRepository.findByScopeTypeAndScopeIdAndActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        when(budgetRepository.save(any(AIBudget.class))).thenReturn(budget);

        // Act
        AIBudget result = budgetService.createBudget(budget);

        // Assert
        assertThat(result).isNotNull();
        verify(budgetRepository).save(budget);
    }

    @Test
    void testCreateBudget_DeactivatesExisting() {
        // Arrange
        AIBudget existingBudget = AIBudget.builder()
                .id(UUID.randomUUID())
                .active(true)
                .build();

        AIBudget newBudget = AIBudget.builder()
                .scopeType(BudgetScopeType.GLOBAL)
                .monthlyLimit(new BigDecimal("200.00"))
                .build();

        when(budgetRepository.findByScopeTypeAndScopeIdAndActiveTrue(any(), any()))
                .thenReturn(Optional.of(existingBudget));
        when(budgetRepository.save(any(AIBudget.class))).thenReturn(newBudget);

        // Act
        budgetService.createBudget(newBudget);

        // Assert
        assertThat(existingBudget.getActive()).isFalse();
        verify(budgetRepository, times(2)).save(any(AIBudget.class));
    }

    @Test
    void testGetBudgetStatus_WithinBudget() {
        // Arrange
        AIBudget budget = AIBudget.builder()
                .id(UUID.randomUUID())
                .scopeType(BudgetScopeType.GLOBAL)
                .dailyLimit(new BigDecimal("10.00"))
                .weeklyLimit(new BigDecimal("50.00"))
                .monthlyLimit(new BigDecimal("200.00"))
                .warningThreshold(80)
                .criticalThreshold(95)
                .build();

        when(budgetRepository.findGlobalBudget()).thenReturn(Optional.of(budget));
        when(usageLogRepository.sumCostByDateRange(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("2.00"));  // Daily
        when(usageLogRepository.sumCostByDateRange(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("15.00")); // Weekly
        when(usageLogRepository.sumCostByDateRange(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("50.00")); // Monthly

        // Act
        BudgetStatusDTO result = budgetService.getBudgetStatus(null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("OK");
        assertThat(result.getHasActiveAlerts()).isFalse();
    }

    @Test
    void testGetBudgetStatus_NoBudget() {
        // Arrange
        when(budgetRepository.findGlobalBudget()).thenReturn(Optional.empty());

        // Act
        BudgetStatusDTO result = budgetService.getBudgetStatus(null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("NO_BUDGET");
    }
}