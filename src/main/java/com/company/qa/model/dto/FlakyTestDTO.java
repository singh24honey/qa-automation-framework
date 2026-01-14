package com.company.qa.model.dto;

import com.company.qa.model.enums.TestStability;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlakyTestDTO {

    private UUID testId;
    private String testName;
    private TestStability stability;
    private Double passRate;
    private Integer totalExecutions;
    private Integer passedCount;
    private Integer failedCount;
    private Instant lastFailure;
    private List<String> commonErrors;
    private Double flakinessScore; // 0-100, higher = more flaky
    private String recommendation;
}