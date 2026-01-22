package com.company.qa.service.ai;

import com.company.qa.model.dto.CostAnalyticsDTO;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import com.company.qa.repository.AIUsageLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AICostAnalyticsServiceTest {

    @Mock
    private AIUsageLogRepository usageLogRepository;

    @Mock
    private AIBudgetService budgetService;

    @InjectMocks
    private AICostAnalyticsService costAnalyticsService;

    @Test
    void testGetCostAnalytics_Success() {
        // Arrange
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        when(usageLogRepository.sumCostByDateRange(any(), any()))
                .thenReturn(new BigDecimal("45.50"));
        when(usageLogRepository.countByDateRange(any(), any()))
                .thenReturn(450L);
        when(usageLogRepository.sumTokensByDateRange(any(), any()))
                .thenReturn(2500000L);

        // Provider stats
        List<Object[]> providerStats = Arrays.asList(
                new Object[][]{new Object[]{AIProvider.BEDROCK, new BigDecimal("45.50"), 450L, 2500000L}}
        );
        when(usageLogRepository.findCostByProvider(any(), any()))
                .thenReturn(providerStats);

        // Task type stats
        List<Object[]> taskStats =  Arrays.asList(
                new Object[][]{new Object[]{AITaskType.TEST_GENERATION, new BigDecimal("30.00"), 300L, 5000.0}}
        );
        when(usageLogRepository.findCostByTaskType(any(), any()))
                .thenReturn(taskStats);

        // Top users
        List<Object[]> topUsers =  Arrays.asList(
                new Object[][]{new Object[]{UUID.randomUUID(), "John Doe", new BigDecimal("15.00"), 150L}}
        );
        when(usageLogRepository.findTopUsersByCost(any(), any()))
                .thenReturn(topUsers);

        // Daily trends
        when(usageLogRepository.findDailyCostTrends(any(), any()))
                .thenReturn(Arrays.asList());

        // Act
        CostAnalyticsDTO result = costAnalyticsService.getCostAnalytics(start, end);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalCost()).isEqualByComparingTo(new BigDecimal("45.50"));
        assertThat(result.getTotalRequests()).isEqualTo(450L);
        assertThat(result.getTotalTokens()).isEqualTo(2500000L);
        assertThat(result.getCostByProvider()).hasSize(1);
        assertThat(result.getCostByTaskType()).hasSize(1);
        assertThat(result.getTopUsersByCost()).hasSize(1);
    }

    @Test
    void testGetLast30DaysAnalytics() {
        // Arrange
        when(usageLogRepository.sumCostByDateRange(any(), any()))
                .thenReturn(new BigDecimal("25.00"));
        when(usageLogRepository.countByDateRange(any(), any()))
                .thenReturn(250L);
        when(usageLogRepository.sumTokensByDateRange(any(), any()))
                .thenReturn(1000000L);
        when(usageLogRepository.findCostByProvider(any(), any()))
                .thenReturn(Arrays.asList());
        when(usageLogRepository.findCostByTaskType(any(), any()))
                .thenReturn(Arrays.asList());
        when(usageLogRepository.findTopUsersByCost(any(), any()))
                .thenReturn(Arrays.asList());
        when(usageLogRepository.findDailyCostTrends(any(), any()))
                .thenReturn(Arrays.asList());

        // Act
        CostAnalyticsDTO result = costAnalyticsService.getLast30DaysAnalytics();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalCost()).isNotNull();
    }

    @Test
    void testGetUserCostAnalytics() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        when(usageLogRepository.sumCostByUserAndDateRange(any(), any(), any()))
                .thenReturn(new BigDecimal("10.50"));
        when(usageLogRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Arrays.asList());

        // Act
        CostAnalyticsDTO result = costAnalyticsService.getUserCostAnalytics(userId, start, end);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalCost()).isEqualByComparingTo(new BigDecimal("10.50"));
    }
}