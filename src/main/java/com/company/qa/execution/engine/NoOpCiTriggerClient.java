package com.company.qa.execution.engine;

import com.company.qa.execution.context.ExecutionContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class NoOpCiTriggerClient implements CiTriggerClient {

    @Override
    public String triggerExecution(ExecutionContext context) {
        return "local-execution-" + System.currentTimeMillis();
    }
}
