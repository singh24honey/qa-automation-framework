package com.company.qa.ai.api;

import com.company.qa.ai.model.AiRecommendationType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiRecommendationDto {

    private final AiRecommendationType type;
    private final String reason;
}