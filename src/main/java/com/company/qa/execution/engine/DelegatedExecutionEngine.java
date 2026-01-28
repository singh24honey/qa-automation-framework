package com.company.qa.execution.engine;

import com.company.qa.execution.context.ExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DelegatedExecutionEngine implements ExecutionEngine {

    private final CiTriggerClient ciTriggerClient;

    @Override
    public ExecutionResult execute(ExecutionContext context) {

        String externalRef = ciTriggerClient.triggerExecution(context);

        return ExecutionResult.builder()
                .success(false)
                .durationMs(0)
                .errorMessage(null)
                .logUrl(null)
                .screenshotUrls(null)
                .externalExecutionRef(externalRef)
                .build();
    }
}