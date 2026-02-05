package com.company.qa.service.analytics;

import com.company.qa.model.dto.ExecutiveDashboardDTO;
import com.company.qa.model.dto.ExecutiveAlertDTO;
import com.company.qa.model.dto.QualityTrendDTO;
import com.company.qa.model.dto.QualityTrendDTO2;
import com.company.qa.model.entity.ExecutiveKPICache;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ExecutiveAnalyticsService {

    /**
     * Get comprehensive executive dashboard
     */
    ExecutiveDashboardDTO getExecutiveDashboard();

    /**
     * Get KPIs for a specific period
     */
    ExecutiveKPICache getKPIsForPeriod(String periodType, Instant periodStart, Instant periodEnd);

    /**
     * Get quality trends for date range
     */
    List<QualityTrendDTO2> getQualityTrends(LocalDate startDate, LocalDate endDate);

    /**
     * Get quality trends for specific suite
     */
    List<QualityTrendDTO2> getQualityTrendsForSuite(Long suiteId, LocalDate startDate, LocalDate endDate);

    /**
     * Get active alerts
     */
    List<ExecutiveAlertDTO> getActiveAlerts();

    /**
     * Get alerts by severity
     */
    List<ExecutiveAlertDTO> getAlertsBySeverity(String severity);

    /**
     * Acknowledge an alert
     */
    ExecutiveAlertDTO acknowledgeAlert(Long alertId, String acknowledgedBy);

    /**
     * Resolve an alert
     */
    ExecutiveAlertDTO resolveAlert(Long alertId);

    /**
     * Manually refresh KPI cache
     */
    void refreshKPICache();
}