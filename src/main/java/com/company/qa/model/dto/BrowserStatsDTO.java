package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrowserStatsDTO {
    private String browser;
    private Long totalExecutions;
    private Long passed;
    private Long failed;
    private Double passRate;
}