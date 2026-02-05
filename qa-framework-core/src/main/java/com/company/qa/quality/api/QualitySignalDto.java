package com.company.qa.quality.api;

import com.company.qa.quality.model.QualityVerdict;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QualitySignalDto {

    private final QualityVerdict verdict;
    private final String reasons;
}