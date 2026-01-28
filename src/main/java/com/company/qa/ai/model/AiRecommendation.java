package com.company.qa.ai.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiRecommendation {

    private final AiRecommendationType type;
    private final String reason;

}