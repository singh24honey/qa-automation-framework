package com.company.qa.ai.service;

import com.company.qa.ai.model.AiRecommendation;
import com.company.qa.ai.model.AiRecommendationType;
import com.company.qa.analytics.model.TestAnalyticsSnapshot;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.quality.model.QualityVerdict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiRecommendationService {

    public List<AiRecommendation> recommend(
            TestExecution execution,
            TestAnalyticsSnapshot analytics) {

        List<AiRecommendation> recommendations = new ArrayList<>();

        if (execution.getStatus() == TestStatus.PASSED
                && analytics.getConfidenceScore() >= 0.85) {

            recommendations.add(AiRecommendation.builder()
                    .type(AiRecommendationType.SAFE_TO_APPROVE)
                    .reason("High confidence and successful execution")
                    .build());
        }

        if (execution.getStatus() == TestStatus.FAILED
                && analytics.getFlakinessScore() > 0.30) {

            recommendations.add(AiRecommendation.builder()
                    .type(AiRecommendationType.FLAKY_TEST_SUSPECTED)
                    .reason("Multiple inconsistent failures detected")
                    .build());

            recommendations.add(AiRecommendation.builder()
                    .type(AiRecommendationType.RERUN_RECOMMENDED)
                    .reason("Failure likely due to flakiness")
                    .build());
        }

        if (execution.getStatus() == TestStatus.FAILED
                && analytics.getFlakinessScore() <= 0.30) {

            recommendations.add(AiRecommendation.builder()
                    .type(AiRecommendationType.INVESTIGATE_FAILURE)
                    .reason("Consistent failure pattern detected")
                    .build());
        }

        if (execution.getQualityVerdict() == QualityVerdict.WARN) {

            recommendations.add(AiRecommendation.builder()
                    .type(AiRecommendationType.MANUAL_REVIEW_REQUIRED)
                    .reason("Quality gate warning triggered")
                    .build());
        }

        if (analytics.getConfidenceScore() < 0.50) {

            recommendations.add(AiRecommendation.builder()
                    .type(AiRecommendationType.RERUN_RECOMMENDED)
                    .reason("Low confidence in historical stability")
                    .build());
        }

        return recommendations;
    }
}