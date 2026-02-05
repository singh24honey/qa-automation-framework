package com.company.qa.model.dto;

import com.company.qa.model.enums.TrendDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetricsDTO {

    private UUID testId;
    private String testName;
    private Double averageDuration; // seconds
    private Double minDuration;
    private Double maxDuration;
    private Double medianDuration;
    private Double standardDeviation;
    private TrendDirection trend;
    private List<DurationDataPoint> durationHistory;
    private String performanceRating; // FAST, NORMAL, SLOW

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DurationDataPoint {
        private String date;
        private Double duration;
        private String status;
    }
}