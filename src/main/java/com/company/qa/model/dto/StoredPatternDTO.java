package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for stored failure pattern (persisted)
 * Different from Week 3 FailurePatternDTO (calculated on-demand)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredPatternDTO {

    private Long id;
    private String testName;
    private String patternType;
    private String errorSignature;
    private Integer occurrences;
    private Instant firstDetectedAt;
    private Instant lastDetectedAt;
    private Boolean isResolved;
    private Instant resolvedAt;
    private List<String> affectedBrowsers;
    private Double impactScore;
}