package com.company.qa.quality.service;

import com.company.qa.analytics.model.TestAnalyticsSnapshot;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.quality.model.QualityGateResult;
import com.company.qa.quality.model.QualityVerdict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QualityGateService {

    public QualityGateResult evaluate(
            TestExecution execution,
            TestAnalyticsSnapshot analytics) {

        List<String> reasons = new ArrayList<>();

        if (execution.getStatus() == TestStatus.FAILED) {
            reasons.add("Execution failed");
            return QualityGateResult.builder()
                    .verdict(QualityVerdict.FAIL)
                    .reasons(reasons)
                    .build();
        }

        if (analytics.getFlakinessScore() > 0.30) {
            reasons.add("High flakiness detected");
        }

        if (analytics.getConfidenceScore() < 0.70) {
            reasons.add("Low confidence score");
        }

        QualityVerdict verdict =
                reasons.isEmpty()
                        ? QualityVerdict.PASS
                        : QualityVerdict.WARN;

        return QualityGateResult.builder()
                .verdict(verdict)
                .reasons(reasons)
                .build();
    }
}