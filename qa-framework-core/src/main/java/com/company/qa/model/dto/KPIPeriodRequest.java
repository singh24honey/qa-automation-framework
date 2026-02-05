package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KPIPeriodRequest {

    private String periodType; // DAILY, WEEKLY, MONTHLY, QUARTERLY
    private Instant startDate;
    private Instant endDate;
    private Boolean includePartial;
}