package com.company.qa.repository;

import com.company.qa.model.entity.ExecutiveAlert;
import com.company.qa.model.entity.ExecutiveKPICache;
import com.company.qa.model.entity.QualityTrendAnalysis;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ExecutiveRepositoriesTest {

    @Autowired
    private ExecutiveKPICacheRepository kpiRepository;

    @Autowired
    private QualityTrendAnalysisRepository trendRepository;

    @Autowired
    private ExecutiveAlertRepository alertRepository;

    @Test
    void testSaveAndFindKPICache() {
        // Given
        Instant now = Instant.now();
        ExecutiveKPICache kpi = ExecutiveKPICache.builder()
                .periodType("DAILY")
                .periodStart(now.minus(1, ChronoUnit.DAYS))
                .periodEnd(now)
                .overallPassRate(BigDecimal.valueOf(85.50))
                .trendDirection("IMPROVING")
                .totalExecutions(100L)
                .build();

        // When
        ExecutiveKPICache saved = kpiRepository.save(kpi);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOverallPassRate()).isEqualByComparingTo(BigDecimal.valueOf(85.50));

        Optional<ExecutiveKPICache> found = kpiRepository.findByPeriodTypeAndPeriodStartAndPeriodEnd(
                "DAILY", kpi.getPeriodStart(), kpi.getPeriodEnd());

        assertThat(found).isPresent();
        assertThat(found.get().getTrendDirection()).isEqualTo("IMPROVING");
    }

    @Test
    void testSaveAndFindTrendAnalysis() {
        // Given
        LocalDate today = LocalDate.now();
        QualityTrendAnalysis trend = QualityTrendAnalysis.builder()
                .analysisDate(today)
                .suiteId(1L)
                .passRate7dAvg(BigDecimal.valueOf(88.00))
                .passRate30dAvg(BigDecimal.valueOf(85.00))
                .passRateTrend("IMPROVING_SLOW")
                .flakinessScore(BigDecimal.valueOf(12.50))
                .build();

        // When
        QualityTrendAnalysis saved = trendRepository.save(trend);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPassRateTrend()).isEqualTo("IMPROVING_SLOW");

        Optional<QualityTrendAnalysis> found = trendRepository.findByAnalysisDateAndSuiteId(today, 1L);
        assertThat(found).isPresent();
    }

    @Test
    void testSaveAndFindAlert() {
        // Given
        ExecutiveAlert alert = ExecutiveAlert.builder()
                .alertType("QUALITY_DEGRADATION")
                .severity("CRITICAL")
                .title("Pass rate below threshold")
                .description("Overall pass rate dropped to 65%")
                .metricName("overall_pass_rate")
                .currentValue(BigDecimal.valueOf(65.00))
                .thresholdValue(BigDecimal.valueOf(70.00))
                .status("ACTIVE")
                .build();

        // When
        ExecutiveAlert saved = alertRepository.save(alert);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");

        List<ExecutiveAlert> activeAlerts = alertRepository.findByStatusOrderByDetectedAtDesc("ACTIVE");
        assertThat(activeAlerts).hasSize(1);
        assertThat(activeAlerts.get(0).getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void testFindKPIsByPeriodType() {
        // Given
        Instant now = Instant.now();
        kpiRepository.save(ExecutiveKPICache.builder()
                .periodType("DAILY")
                .periodStart(now.minus(2, ChronoUnit.DAYS))
                .periodEnd(now.minus(1, ChronoUnit.DAYS))
                .overallPassRate(BigDecimal.valueOf(80.00))
                .build());

        kpiRepository.save(ExecutiveKPICache.builder()
                .periodType("DAILY")
                .periodStart(now.minus(1, ChronoUnit.DAYS))
                .periodEnd(now)
                .overallPassRate(BigDecimal.valueOf(85.00))
                .build());

        // When
        List<ExecutiveKPICache> dailyKPIs = kpiRepository.findByPeriodTypeOrderByPeriodStartDesc("DAILY");

        // Then
        assertThat(dailyKPIs).hasSize(2);
        assertThat(dailyKPIs.get(0).getOverallPassRate()).isEqualByComparingTo(BigDecimal.valueOf(85.00));
    }

    @Test
    void testCountAlertsBySeverity() {
        // Given
        alertRepository.save(ExecutiveAlert.builder()
                .alertType("QUALITY_DEGRADATION")
                .severity("CRITICAL")
                .title("Critical alert")
                .description("Test")
                .status("ACTIVE")
                .build());

        alertRepository.save(ExecutiveAlert.builder()
                .alertType("COST_SPIKE")
                .severity("HIGH")
                .title("High alert")
                .description("Test")
                .status("ACTIVE")
                .build());

        // When
        Long criticalCount = alertRepository.countByStatusAndSeverity("ACTIVE", "CRITICAL");
        Long highCount = alertRepository.countByStatusAndSeverity("ACTIVE", "HIGH");

        // Then
        assertThat(criticalCount).isEqualTo(1);
        assertThat(highCount).isEqualTo(1);
    }
}