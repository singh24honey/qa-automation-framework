package com.company.qa.execution.decision;

import com.company.qa.execution.context.ExecutionContext;
import com.company.qa.model.enums.TestFramework;
import org.springframework.stereotype.Component;

@Component
public class ExecutionModeDecider {

    public ExecutionMode decide(ExecutionContext context) {

        TestFramework framework = context.getFramework();

        if (framework == TestFramework.SELENIUM) {
            return ExecutionMode.INTERNAL;
        }

        if (framework == TestFramework.CUCUMBER_TESTNG) {
            return ExecutionMode.DELEGATED;
        }

        if (framework == TestFramework.PLAYWRIGHT) {
            return ExecutionMode.DELEGATED;
        }

        // Safe default
        return ExecutionMode.INTERNAL;
    }
}