package com.company.qa.quality.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class QualityGateResult {

    private final QualityVerdict verdict;
    private final List<String> reasons;
}