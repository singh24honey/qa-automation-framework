package com.company.qa.execution.engine;

import com.company.qa.execution.context.ExecutionContext;
import com.company.qa.service.execution.SeleniumTestExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InternalExecutionEngine implements ExecutionEngine {

    private final SeleniumTestExecutor seleniumTestExecutor;

    @Override
    public ExecutionResult execute(ExecutionContext context) {

        SeleniumTestExecutor.ExecutionResult result =
                seleniumTestExecutor.execute(
                        context.getExecutionId().toString(),
                        context.getTestScript(),
                        context.getBrowser(),
                        context.isHeadless(),
                        context.getRetryConfig()
                );

        return ExecutionResult.builder()
                .success(result.isSuccess())
                .durationMs(result.getDurationMs())
                .errorMessage(result.getErrorMessage())
                .logUrl(result.getLogUrl())
                .screenshotUrls(result.getScreenshotUrls())
                .build();
    }
}