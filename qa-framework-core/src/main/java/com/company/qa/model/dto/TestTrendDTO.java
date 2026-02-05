package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestTrendDTO {
    private LocalDate date;
    private Long totalExecutions;
    private Long passed;
    private Long failed;
    private Double passRate;
}