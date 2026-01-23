package com.company.qa.service.analytics;

import com.company.qa.model.dto.ExecutiveDashboardDTO;
import com.company.qa.model.dto.ExecutiveAlertDTO;
import com.company.qa.model.dto.QualityTrendDTO;
import com.company.qa.model.dto.QualityTrendDTO2;
import com.company.qa.model.entity.*;
import com.company.qa.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutiveAnalyticsServiceImpl implements ExecutiveAnalyticsService {

    private final ExecutiveKPICacheRepository kpiCacheRepository;
    private final QualityTrendAnalysisRepository trendAnalysisRepository;
    private final ExecutiveAlertRepository alertRepository;
    private final TestQualitySnapshotRepository snapshotRepository;

    @Override
    @Transactional(readOnly = true)
    public ExecutiveDashboardDTO getExecutiveDashboard() {
        log.info("Generating executive dashboard");

        Instant now = Instant.now();

        // Get or create today's KPIs
        ExecutiveKPICache todayKPIs = getOrCreateDailyKPIs(now);

        // Get quality trends for last 30 days
        LocalDate startDate = LocalDate.now().minusDays(30);
        List<ExecutiveDashboardDTO.TrendDataPoint> trends = buildTrendDataPoints(startDate);

        // Get critical alerts (top 5)
        List<ExecutiveAlertDTO> criticalAlerts = getAlertsBySeverity("CRITICAL")
                .stream()
                .limit(5)
                .collect(Collectors.toList());

        return ExecutiveDashboardDTO.builder()
                .currentKPIs(buildKPISummary(todayKPIs))
                .qualityTrends(trends)
                .criticalAlerts(criticalAlerts.stream()
                        .map(this::toAlertSummary)
                        .collect(Collectors.toList()))
                .businessImpact(buildBusinessImpact(todayKPIs))
                .aiInsights(buildAIInsights(todayKPIs))
                .generatedAt(now)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ExecutiveKPICache getKPIsForPeriod(String periodType, Instant periodStart, Instant periodEnd) {
        log.info("Getting KPIs for period: {} from {} to {}", periodType, periodStart, periodEnd);

        return kpiCacheRepository.findByPeriodTypeAndPeriodStartAndPeriodEnd(periodType, periodStart, periodEnd)
                .orElseGet(() -> {
                    log.info("KPI cache miss, calculating...");
                    return calculateAndCacheKPIs(periodType, periodStart, periodEnd);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<QualityTrendDTO2> getQualityTrends(LocalDate startDate, LocalDate endDate) {
        log.info("Getting quality trends from {} to {}", startDate, endDate);

        List<QualityTrendAnalysis> trends = trendAnalysisRepository
                .findByAnalysisDateBetweenOrderByAnalysisDate(startDate, endDate);

        return trends.stream()
                .map(this::toTrendDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<QualityTrendDTO2> getQualityTrendsForSuite(Long suiteId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting quality trends for suite {} from {} to {}", suiteId, startDate, endDate);

        List<QualityTrendAnalysis> trends = trendAnalysisRepository
                .findBySuiteIdAndAnalysisDateBetweenOrderByAnalysisDate(suiteId, startDate, endDate);

        return trends.stream()
                .map(this::toTrendDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutiveAlertDTO> getActiveAlerts() {
        log.info("Getting active alerts");

        List<ExecutiveAlert> alerts = alertRepository.findByStatusOrderByDetectedAtDesc("ACTIVE");
        return alerts.stream()
                .map(this::toAlertDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutiveAlertDTO> getAlertsBySeverity(String severity) {
        log.info("Getting alerts with severity: {}", severity);

        List<ExecutiveAlert> alerts = alertRepository
                .findByStatusAndSeverityOrderByDetectedAtDesc("ACTIVE", severity);

        return alerts.stream()
                .map(this::toAlertDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ExecutiveAlertDTO acknowledgeAlert(Long alertId, String acknowledgedBy) {
        log.info("Acknowledging alert {} by {}", alertId, acknowledgedBy);

        ExecutiveAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(Instant.now());

        ExecutiveAlert saved = alertRepository.save(alert);
        return toAlertDTO(saved);
    }

    @Override
    @Transactional
    public ExecutiveAlertDTO resolveAlert(Long alertId) {
        log.info("Resolving alert {}", alertId);

        ExecutiveAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        alert.setStatus("RESOLVED");
        alert.setResolvedAt(Instant.now());

        ExecutiveAlert saved = alertRepository.save(alert);
        return toAlertDTO(saved);
    }

    @Override
    @Transactional
    public void refreshKPICache() {
        log.info("Manually refreshing KPI cache");

        Instant now = Instant.now();
        getOrCreateDailyKPIs(now);

        log.info("KPI cache refresh complete");
    }

    // ============ PRIVATE HELPER METHODS ============

    private ExecutiveKPICache getOrCreateDailyKPIs(Instant timestamp) {
        LocalDate date = LocalDate.ofInstant(timestamp, ZoneId.systemDefault());
        Instant dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant dayEnd = date.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();

        return kpiCacheRepository.findByPeriodTypeAndPeriodStartAndPeriodEnd("DAILY", dayStart, dayEnd)
                .orElseGet(() -> calculateAndCacheKPIs("DAILY", dayStart, dayEnd));
    }

    private ExecutiveKPICache calculateAndCacheKPIs(String periodType, Instant periodStart, Instant periodEnd) {
        log.info("Calculating KPIs for period: {} from {} to {}", periodType, periodStart, periodEnd);

        // Get daily snapshots in period
        LocalDate start = LocalDate.ofInstant(periodStart, ZoneId.systemDefault());
        LocalDate end = LocalDate.ofInstant(periodEnd, ZoneId.systemDefault());

        List<TestQualitySnapshot> snapshots = snapshotRepository
                .findBySnapshotDateBetweenOrderBySnapshotDateAsc(start, end);

        if (snapshots.isEmpty()) {
            log.warn("No snapshots found for period, using defaults");
            return createDefaultKPI(periodType, periodStart, periodEnd);
        }

        // Calculate averages
        long totalExecs = snapshots.stream()
                .mapToLong(TestQualitySnapshot::getTotalExecutions)
                .sum();

        long passedExecs = snapshots.stream()
                .mapToLong(TestQualitySnapshot::getStableTests)
                .sum();

        BigDecimal passRate = totalExecs > 0
                ? BigDecimal.valueOf(passedExecs * 100.0 / totalExecs).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long avgExecTime = snapshots.isEmpty() ? 0 : snapshots.stream()
                .mapToLong(s -> s.getAvgExecutionTimeMs() != null ? s.getAvgExecutionTimeMs() : 0)
                .sum() / snapshots.size();

        // Build and save KPI cache
        ExecutiveKPICache kpi = ExecutiveKPICache.builder()
                .periodType(periodType)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .overallPassRate(passRate)
                .trendDirection("STABLE")
                .qualityScore(passRate)
                .totalExecutions(totalExecs)
                .avgExecutionTimeMs(avgExecTime)
                .flakyTestCount(0)
                .aiAccuracyRate(BigDecimal.ZERO)
                .aiCostTotal(BigDecimal.ZERO)
                .aiCostPerFix(BigDecimal.ZERO)
                .aiSuggestionsAccepted(0)
                .aiSuggestionsRejected(0)
                .estimatedTimeSavedHours(BigDecimal.ZERO)
                .estimatedCostSavedUsd(BigDecimal.ZERO)
                .isFinal(Instant.now().isAfter(periodEnd))
                .build();

        return kpiCacheRepository.save(kpi);
    }

    private ExecutiveKPICache createDefaultKPI(String periodType, Instant periodStart, Instant periodEnd) {
        return ExecutiveKPICache.builder()
                .periodType(periodType)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .overallPassRate(BigDecimal.ZERO)
                .trendDirection("STABLE")
                .qualityScore(BigDecimal.ZERO)
                .totalExecutions(0L)
                .avgExecutionTimeMs(0L)
                .flakyTestCount(0)
                .aiAccuracyRate(BigDecimal.ZERO)
                .aiCostTotal(BigDecimal.ZERO)
                .aiCostPerFix(BigDecimal.ZERO)
                .aiSuggestionsAccepted(0)
                .aiSuggestionsRejected(0)
                .estimatedTimeSavedHours(BigDecimal.ZERO)
                .estimatedCostSavedUsd(BigDecimal.ZERO)
                .isFinal(false)
                .build();
    }

    private List<ExecutiveDashboardDTO.TrendDataPoint> buildTrendDataPoints(LocalDate startDate) {
        LocalDate endDate = LocalDate.now();
        List<TestQualitySnapshot> snapshots = snapshotRepository
                .findBySnapshotDateBetweenOrderBySnapshotDateAsc(startDate, endDate);

        return snapshots.stream()
                .map(snapshot -> ExecutiveDashboardDTO.TrendDataPoint.builder()
                        .timestamp(snapshot.getSnapshotDate().atStartOfDay(ZoneId.systemDefault()).toInstant())
                        .passRate(snapshot.getAvgPassRate())
                        .trendIndicator(determineTrendIndicator(snapshot.getAvgPassRate()))
                        .build())
                .collect(Collectors.toList());
    }

    private String determineTrendIndicator(BigDecimal passRate) {
        if (passRate.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "EXCELLENT";
        } else if (passRate.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return "GOOD";
        } else if (passRate.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }

    private ExecutiveDashboardDTO.KPISummary buildKPISummary(ExecutiveKPICache kpi) {
        return ExecutiveDashboardDTO.KPISummary.builder()
                .overallPassRate(kpi.getOverallPassRate())
                .trendDirection(kpi.getTrendDirection())
                .qualityScore(kpi.getQualityScore())
                .totalExecutions(kpi.getTotalExecutions())
                .avgExecutionTimeMs(kpi.getAvgExecutionTimeMs())
                .flakyTestCount(kpi.getFlakyTestCount())
                .build();
    }

    private ExecutiveDashboardDTO.BusinessImpact buildBusinessImpact(ExecutiveKPICache kpi) {
        BigDecimal netSavings = kpi.getEstimatedCostSavedUsd().subtract(kpi.getAiCostTotal());
        BigDecimal roi = kpi.getAiCostTotal().compareTo(BigDecimal.ZERO) > 0
                ? netSavings.divide(kpi.getAiCostTotal(), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return ExecutiveDashboardDTO.BusinessImpact.builder()
                .estimatedTimeSavedHours(kpi.getEstimatedTimeSavedHours())
                .estimatedCostSavedUsd(kpi.getEstimatedCostSavedUsd())
                .aiCostTotal(kpi.getAiCostTotal())
                .netSavings(netSavings)
                .roi(roi)
                .build();
    }

    private ExecutiveDashboardDTO.AIInsights buildAIInsights(ExecutiveKPICache kpi) {
        int totalSuggestions = kpi.getAiSuggestionsAccepted() + kpi.getAiSuggestionsRejected();
        BigDecimal acceptanceRate = totalSuggestions > 0
                ? BigDecimal.valueOf(kpi.getAiSuggestionsAccepted() * 100.0 / totalSuggestions)
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return ExecutiveDashboardDTO.AIInsights.builder()
                .aiAccuracyRate(kpi.getAiAccuracyRate())
                .aiCostPerFix(kpi.getAiCostPerFix())
                .aiSuggestionsAccepted(kpi.getAiSuggestionsAccepted())
                .aiSuggestionsRejected(kpi.getAiSuggestionsRejected())
                .acceptanceRate(acceptanceRate)
                .build();
    }

    private ExecutiveDashboardDTO.AlertSummary toAlertSummary(ExecutiveAlertDTO alert) {
        return ExecutiveDashboardDTO.AlertSummary.builder()
                .id(alert.getId())
                .alertType(alert.getAlertType())
                .severity(alert.getSeverity())
                .title(alert.getTitle())
                .description(alert.getDescription())
                .detectedAt(alert.getDetectedAt())
                .build();
    }

    private QualityTrendDTO2 toTrendDTO(QualityTrendAnalysis analysis) {
        String healthStatus = determineHealthStatus(analysis.getPassRate30dAvg(),
                analysis.getFlakinessScore());

        String recommendation = generateRecommendation(analysis);

        return QualityTrendDTO2.builder()
                .id(analysis.getId())
                .analysisDate(analysis.getAnalysisDate())
                .suiteId(analysis.getSuiteId())
                .suiteName(resolveSuiteName(analysis.getSuiteId())) // ✅ FIX
                .passRate7dAvg(analysis.getPassRate7dAvg())
                .passRate30dAvg(analysis.getPassRate30dAvg())
                .passRateTrend(analysis.getPassRateTrend())
                .passRateVolatility(analysis.getPassRateVolatility())
                .flakinessScore(analysis.getFlakinessScore())
                .newFailuresCount(analysis.getNewFailuresCount())
                .recurringFailuresCount(analysis.getRecurringFailuresCount())
                .resolvedFailuresCount(analysis.getResolvedFailuresCount())
                .aiFixedFailures(analysis.getAiFixedFailures())
                .aiPreventedFailures(analysis.getAiPreventedFailures())
                .predictedNext7dPassRate(analysis.getPredictedNext7dPassRate())
                .confidenceScore(analysis.getConfidenceScore())
                .healthStatus(healthStatus)
                .recommendation(recommendation)
                .build();
    }
    private String resolveSuiteName(Long suiteId) {
        if (suiteId == null) {
            return "ALL TESTS"; // explicit, UI-safe
        }
        return "Suite-" + suiteId; // placeholder until test_suites exists
    }

    private int defaultInt(Integer value) {
        return value != null ? value : 0;
    }

    private String determineHealthStatus(BigDecimal passRate, BigDecimal flakinessScore) {
        if (passRate == null) return "UNKNOWN";

        if (passRate.compareTo(BigDecimal.valueOf(95)) >= 0 &&
                (flakinessScore == null || flakinessScore.compareTo(BigDecimal.valueOf(10)) < 0)) {
            return "EXCELLENT";
        } else if (passRate.compareTo(BigDecimal.valueOf(85)) >= 0) {
            return "GOOD";
        } else if (passRate.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "FAIR";
        } else if (passRate.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "POOR";
        } else {
            return "CRITICAL";
        }
    }

    private String generateRecommendation(QualityTrendAnalysis analysis) {
        if ("DECLINING_FAST".equals(analysis.getPassRateTrend())) {
            return "Immediate attention required: quality declining rapidly.";
        } else if ("DECLINING_SLOW".equals(analysis.getPassRateTrend())) {
            return "Monitor closely: quality showing downward trend.";
        } else if (analysis.getFlakinessScore() != null &&
                analysis.getFlakinessScore().compareTo(BigDecimal.valueOf(30)) > 0) {
            return "High flakiness detected. Review and stabilize tests.";
        } else if ("IMPROVING_FAST".equals(analysis.getPassRateTrend())) {
            return "Great progress! Maintain current improvements.";
        } else {
            return "Quality is stable. Continue monitoring.";
        }
    }

    private ExecutiveAlertDTO toAlertDTO(ExecutiveAlert alert) {
        long ageInHours = ChronoUnit.HOURS.between(alert.getDetectedAt(), Instant.now());
        String priorityLevel = calculatePriorityLevel(alert.getSeverity(), ageInHours);

        BigDecimal deviationPct = null;
        if (alert.getThresholdValue() != null && alert.getCurrentValue() != null &&
                alert.getThresholdValue().compareTo(BigDecimal.ZERO) != 0) {
            deviationPct = alert.getCurrentValue()
                    .subtract(alert.getThresholdValue())
                    .divide(alert.getThresholdValue(), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return ExecutiveAlertDTO.builder()
                .id(alert.getId())
                .alertType(alert.getAlertType())
                .severity(alert.getSeverity())
                .title(alert.getTitle())
                .description(alert.getDescription())
                .metricName(alert.getMetricName())
                .currentValue(alert.getCurrentValue())
                .thresholdValue(alert.getThresholdValue())
                .deviationPct(deviationPct)
                .affectedEntityType(alert.getAffectedEntityType())
                .affectedEntityId(alert.getAffectedEntityId())
                .status(alert.getStatus())
                .acknowledgedBy(alert.getAcknowledgedBy())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .resolvedAt(alert.getResolvedAt())
                .detectedAt(alert.getDetectedAt())
                .ageInHours(ageInHours)
                .priorityLevel(priorityLevel)
                .build();
    }

    private String calculatePriorityLevel(String severity, long ageInHours) {
        return switch (severity) {
            case "CRITICAL" -> ageInHours > 4 ? "URGENT" : "HIGH";
            case "HIGH" -> ageInHours > 24 ? "HIGH" : "MEDIUM";
            case "MEDIUM" -> ageInHours > 48 ? "MEDIUM" : "LOW";
            default -> "LOW";
        };
    }
}