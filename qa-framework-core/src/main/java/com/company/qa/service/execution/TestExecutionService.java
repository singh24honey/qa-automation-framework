package com.company.qa.service.execution;

import com.company.qa.ai.model.AiRecommendation;
import com.company.qa.ai.service.AiRecommendationService;
import com.company.qa.analytics.model.TestAnalyticsSnapshot;
import com.company.qa.analytics.service.TestAnalyticsService;
import com.company.qa.exception.ResourceNotFoundException;
import com.company.qa.execution.context.ExecutionContext;
import com.company.qa.execution.decision.ExecutionMode;
import com.company.qa.execution.decision.ExecutionModeDecider;
import com.company.qa.execution.engine.ExecutionEngine;
import com.company.qa.execution.engine.ExecutionResult;
import com.company.qa.model.agent.AgentConfig;
import com.company.qa.model.agent.AgentGoal;
import com.company.qa.model.dto.ExecutionRequest;
import com.company.qa.model.dto.ExecutionResponse;
import com.company.qa.model.dto.RetryConfig;
import com.company.qa.model.dto.TestScript;
import com.company.qa.model.entity.Test;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.AgentType;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.quality.model.QualityGateResult;
import com.company.qa.quality.service.QualityGateService;
import com.company.qa.repository.TestExecutionRepository;
import com.company.qa.repository.TestRepository;
import com.company.qa.service.agent.AgentOrchestrator;
import com.company.qa.service.playwright.TestIntentParser;
import com.company.qa.service.quality.TestQualityHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.company.qa.service.playwright.TestIntentParser;
import com.company.qa.model.intent.TestIntent;
import com.company.qa.model.intent.TestScenario;
import com.company.qa.model.intent.IntentTestStep;
import com.company.qa.model.dto.TestStep;
import com.company.qa.model.dto.TestScript;
import java.util.ArrayList;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestExecutionService {

    private final TestRepository testRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final SeleniumTestExecutor seleniumTestExecutor;
    private final ObjectMapper objectMapper;
    private final ExecutionCancellationService cancellationService;  // ADD THIS
    private final ExecutionModeDecider executionModeDecider;
    private final ExecutionEngine internalExecutionEngine;
    private final ExecutionEngine delegatedExecutionEngine;
    private final ExecutionEngine playwrightExecutionEngine;  // NEW - Week 11 Day 4
    private final TestAnalyticsService testAnalyticsService;
    private final QualityGateService qualityGateService;
    private final AiRecommendationService aiRecommendationService;
    private final AgentOrchestrator agentOrchestrator;
    private final TestIntentParser testIntentParser;


    @Autowired
    private TestQualityHistoryService qualityHistoryService;  // Inject

    @Autowired
    @Lazy
    private TestExecutionService self;
    @Value("${execution.timeout-minutes:10}")
    private int timeoutMinutes;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecutionResponse startExecution(ExecutionRequest request) {

        TestExecution execution = TestExecution.builder()
                .testId(request.getTestId())
                .status(TestStatus.QUEUED)
                .environment(request.getEnvironment())
                .browser(request.getBrowser())
                .platform("Playwright")
                .triggeredBy("API")
                .executionMode(ExecutionMode.INTERNAL)  // default, may change in async method
                .retryCount(0)
                .externalExecutionRef(UUID.randomUUID().toString())
                .startTime(Instant.now())
                .build();

        execution = testExecutionRepository.save(execution);

        // Fire async execution
        executeAsyncInternal(execution.getId(), request); // ← goes through proxy → truly async

        return toResponse(execution); // ✅ executionId ALWAYS present
    }

    @Async("taskExecutor")
    @Transactional
    public void executeAsyncInternal(UUID executionId, ExecutionRequest request) {

        TestExecution execution = testExecutionRepository.findById(executionId)
                .orElseThrow();

        try {
            // Check cancellation BEFORE starting
            if (cancellationService.isCancelled(executionId)) {
                execution.setStatus(TestStatus.CANCELLED);
                execution.setErrorDetails("Execution cancelled before start");
                execution.setEndTime(Instant.now());
                testExecutionRepository.save(execution);
                return;
            }

            execution.setStatus(TestStatus.RUNNING);
            execution.setStartTime(Instant.now());
            testExecutionRepository.save(execution);

            // After: testExecutionRepository.save(execution);


            if (execution.getStatus() == TestStatus.FAILED
                    || execution.getStatus() == TestStatus.ERROR) {
                autoTriggerHealingIfNeeded(request.getTestId(), execution);
            }

            Test test = testRepository.findById(request.getTestId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Test", request.getTestId().toString()));

            TestScript testScript = parseTestScript(test.getContent());

            boolean headless = request.getHeadless() != null ? request.getHeadless() : true;
            String browser = request.getBrowser() != null ? request.getBrowser() : "CHROME";

            RetryConfig retryConfig = buildRetryConfig(request);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(execution.getId())
                    .testId(test.getId())
                    .framework(test.getFramework())
                    .selector(test.getName()) // or test.getName() for now
                    .environment(request.getEnvironment())
                    .browser(browser)
                    .headless(headless)
                    .retryConfig(retryConfig)
                    .triggeredBy("API")
                    .triggeredAt(Instant.now())
                    .testScript(testScript)
                    .build();

            ExecutionMode mode = executionModeDecider.decide(context);
            execution.setExecutionMode(mode);
            testExecutionRepository.save(execution);

            /*SeleniumTestExecutor.ExecutionResult result =
                    seleniumTestExecutor.execute(
                            executionId.toString(),
                            testScript,
                            browser,
                            headless,
                            retryConfig
                            //timeoutMinutes
                    );*/
// Week 11 Day 4: Select engine based on framework AND mode
            ExecutionResult result;

            if (execution.getExecutionMode() == ExecutionMode.INTERNAL) {
                // Route to appropriate INTERNAL engine based on framework
                if (context.getFramework() == TestFramework.PLAYWRIGHT) {
                    log.debug("Using PlaywrightExecutionEngine for execution: {}", context.getExecutionId());
                    result = playwrightExecutionEngine.execute(context);
                } else {
                    log.debug("Using InternalExecutionEngine for execution: {}", context.getExecutionId());
                    result = internalExecutionEngine.execute(context);  // Selenium
                }
            } else {
                // DELEGATED mode (Cucumber, future CI integration)
                log.debug("Using DelegatedExecutionEngine for execution: {}", context.getExecutionId());
                result = delegatedExecutionEngine.execute(context);
            }

            TestAnalyticsSnapshot analytics =
                    testAnalyticsService.updateAnalytics(execution);

            QualityGateResult gateResult =
                    qualityGateService.evaluate(execution, analytics);

            List<AiRecommendation> recommendations =
                    aiRecommendationService.recommend(execution, analytics);

            execution.setAiRecommendations(
                    recommendations.stream()
                            .map(r -> r.getType() + ": " + r.getReason())
                            .collect(Collectors.joining(" | "))
            );

            testExecutionRepository.save(execution);

            execution.setQualityVerdict(gateResult.getVerdict());
            execution.setQualityReasons(
                    String.join("; ", gateResult.getReasons())
            );

            testExecutionRepository.save(execution);
            if (execution.getExecutionMode() == ExecutionMode.DELEGATED) {
                execution.setStatus(TestStatus.RUNNING);
                execution.setExternalExecutionRef(result.getExternalExecutionRef());
                testExecutionRepository.save(execution);
                return;
            }
            execution.setEndTime(Instant.now());
            execution.setDuration((int) result.getDurationMs());
            execution.setStatus(result.isSuccess() ? TestStatus.PASSED : TestStatus.FAILED);
            execution.setErrorDetails(result.getErrorMessage());
            execution.setLogUrl(result.getLogUrl());
            execution.setRetryCount(
                    result.getFailureHistory() != null ? result.getFailureHistory().size() : 0
            );

            if (result.getScreenshotUrls() != null && !result.getScreenshotUrls().isEmpty()) {
                execution.setScreenshotUrls(result.getScreenshotUrls().toArray(new String[0]));
            }


            testExecutionRepository.save(execution);
            qualityHistoryService.recordExecutionHistory(execution);
            updateTestExecutionMetadata(request.getTestId(), execution);
            cancellationService.clearCancellation(executionId);

            log.info("Execution completed: {} status={}", executionId, execution.getStatus());

        } catch (Exception e) {
            log.error("Execution failed: {}", executionId, e);

            execution.setStatus(TestStatus.ERROR);
            execution.setEndTime(Instant.now());
            execution.setErrorDetails(e.getMessage());
            testExecutionRepository.save(execution);

            cancellationService.clearCancellation(executionId);
        }
    }

    private void updateTestExecutionMetadata(UUID testId, TestExecution execution) {
        try {
            testRepository.findById(testId).ifPresent(test -> {
                test.setLastExecutionStatus(execution.getStatus());
                test.setLastExecutedAt(execution.getEndTime());
                test.setTotalRunCount(
                        (test.getTotalRunCount() == null ? 0 : test.getTotalRunCount()) + 1);

                if (execution.getStatus() == TestStatus.FAILED
                        || execution.getStatus() == TestStatus.ERROR) {
                    test.setLastExecutionError(execution.getErrorDetails()); // full Playwright stack trace
                    test.setConsecutiveFailureCount(
                            (test.getConsecutiveFailureCount() == null ? 0
                                    : test.getConsecutiveFailureCount()) + 1);
                } else if (execution.getStatus() == TestStatus.PASSED) {
                    test.setLastExecutionError(null);      // clear on pass
                    test.setConsecutiveFailureCount(0);    // reset streak
                }
                testRepository.save(test);
            });
        } catch (Exception e) {
            // Non-fatal — execution record already saved, metadata is best-effort
            log.warn("Failed to update test execution metadata for {}: {}", testId, e.getMessage());
        }
    }

    private void autoTriggerHealingIfNeeded(UUID testId, TestExecution execution) {
        try {
            testRepository.findById(testId).ifPresent(test -> {
                int failures = test.getConsecutiveFailureCount() == null
                        ? 0 : test.getConsecutiveFailureCount();

                if (failures >= 2 && execution.getErrorDetails() != null) {
                    log.info("🤖 Auto-triggering SelfHealingAgent for test {} ({} consecutive failures)",
                            testId, failures);

                    AgentGoal goal = AgentGoal.builder()
                            .goalType("FIX_BROKEN_TEST")
                            .parameters(Map.of(
                                    "testId",       testId.toString(),
                                    "errorMessage", execution.getErrorDetails()  // full Playwright stack trace
                            ))
                            .build();

                    AgentConfig config = AgentConfig.builder()
                            .maxIterations(25)
                            .maxAICost(3.0)
                            .build();

                    agentOrchestrator.startAgent(
                            AgentType.SELF_HEALING_TEST_FIXER,
                            goal, config, null, "system-auto-heal");
                }
            });
        } catch (Exception e) {
            log.warn("Failed to auto-trigger healing for {}: {}", testId, e.getMessage());
        }
    }

    /* =========================================================
       CANCELLATION (Week 2 Day 4)
       ========================================================= */

    @Transactional
    public void cancelExecution(UUID executionId) {

        TestExecution execution = testExecutionRepository.findById(executionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Execution", executionId.toString()));

        if (execution.getStatus() == TestStatus.RUNNING ||
                execution.getStatus() == TestStatus.QUEUED) {

            cancellationService.cancelExecution(executionId);

            execution.setStatus(TestStatus.CANCELLED);
            execution.setEndTime(Instant.now());
            execution.setErrorDetails("Execution cancelled by user");
            testExecutionRepository.save(execution);

            log.info("Execution cancelled: {}", executionId);
        } else {
            log.warn("Cannot cancel execution {} in status {}", executionId, execution.getStatus());
        }
    }

    /* =========================================================
       RETRY CONFIG (Week 2 Day 4)
       ========================================================= */

    private RetryConfig buildRetryConfig(ExecutionRequest request) {
        return RetryConfig.builder()
                .enabled(true)
                .maxAttempts(2)
                .delaySeconds(5)
                .retryOnTimeout(true)
                .retryOnNetworkError(true)
                .retryOnAssertionFailure(false)
                .build();
    }

    /*@Async("taskExecutor")
    @Transactional
    public CompletableFuture<ExecutionResponse> executeTestAsyncold(ExecutionRequest request) {
        log.info("Starting async test execution for test: {}", request.getTestId());

        // Create execution record
        TestExecution execution = TestExecution.builder()
                .testId(request.getTestId())
                .status(TestStatus.QUEUED)
                .environment(request.getEnvironment())
                .browser(request.getBrowser())
                .platform("Selenium Grid")
                .triggeredBy("API")
                .retryCount(0)
                .build();

        execution = testExecutionRepository.save(execution);
        UUID executionId = execution.getId();

        try {
            // Update status to RUNNING
            execution.setStatus(TestStatus.RUNNING);
            execution.setStartTime(Instant.now());
            testExecutionRepository.save(execution);

            // Get test
            Test test = testRepository.findById(request.getTestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Test", request.getTestId().toString()));

            // Parse test script from content
            TestScript testScript = parseTestScript(test.getContent());

            // Execute test
            boolean headless = request.getHeadless() != null ? request.getHeadless() : true;
            String browser = request.getBrowser() != null ? request.getBrowser() : "CHROME";

            SeleniumTestExecutor.ExecutionResult result = seleniumTestExecutor.execute(
                    executionId.toString(),
                    testScript,
                    browser,
                    headless
            );

            // Update execution with results
            execution.setEndTime(Instant.now());
            execution.setDuration(result.getDurationMs());
            execution.setStatus(result.isSuccess() ? TestStatus.PASSED : TestStatus.FAILED);
            execution.setErrorDetails(result.getErrorMessage());
            execution.setLogUrl(result.getLogUrl());

            if (result.getScreenshotUrls() != null && !result.getScreenshotUrls().isEmpty()) {
                execution.setScreenshotUrls(result.getScreenshotUrls().toArray(new String[0]));
            }

            testExecutionRepository.save(execution);

            log.info("Test execution completed: {} - Status: {}", executionId, execution.getStatus());

            return CompletableFuture.completedFuture(toResponse(execution));

        } catch (Exception e) {
            log.error("Test execution failed: {}", e.getMessage(), e);

            execution.setStatus(TestStatus.ERROR);
            execution.setEndTime(Instant.now());
            execution.setErrorDetails(e.getMessage());
            testExecutionRepository.save(execution);

            return CompletableFuture.completedFuture(toResponse(execution));
        }
    }
*/
    @Transactional(readOnly = true)
    public ExecutionResponse getExecutionStatus(UUID executionId) {
        TestExecution execution = testExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution", executionId.toString()));

        return toResponse(execution);
    }

    @Transactional(readOnly = true)
    public List<ExecutionResponse> getRecentExecutions(int limit) {
        return testExecutionRepository.findTop10ByOrderByStartTimeDesc().stream()
                .limit(limit)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ExecutionResponse> getExecutions(UUID testId, int limit) {
        if (testId != null) {
            return testExecutionRepository.findByTestId(testId).stream()
                    .limit(limit)
                    .map(this::toResponse)
                    .toList();
        }
        return testExecutionRepository.findTop10ByOrderByStartTimeDesc().stream()
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    private TestScript parseTestScript(String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Test content is empty");
        }

        // Try TestIntent (INTENT_V1) first — this is what all AI-generated tests store
        TestIntentParser.ParseResult result = testIntentParser.parse(content);
        if (result.isSuccess()) {
            return flattenIntentToScript(result.getIntent());
        }

        // Fallback: legacy TestScript format (pre-INTENT_V1 Selenium tests)
        try {
            TestScript script = objectMapper.readValue(content, TestScript.class);
            if (script.getSteps() != null && !script.getSteps().isEmpty()) {
                return script;
            }
        } catch (Exception ignored) {}

        throw new RuntimeException("Content is neither TestIntent nor TestScript: "
                + result.getErrorMessage());
    }

    private TestScript flattenIntentToScript(TestIntent intent) {
        List<TestStep> allSteps = new ArrayList<>();
        for (TestScenario scenario : intent.getScenarios()) {
            if (scenario.getSteps() == null) continue;
            for (IntentTestStep s : scenario.getSteps()) {
                allSteps.add(TestStep.builder()
                        .action(s.getAction().name().toLowerCase())
                        .locator(s.getLocator())
                        .value(s.getValue())
                        .timeout(s.getTimeout())
                        .build());
            }
        }
        log.info("Flattened {} scenarios → {} steps", intent.getScenarios().size(), allSteps.size());
        return TestScript.builder()
                .name(intent.getTestClassName())
                .steps(allSteps)
                .build();
    }

    private ExecutionResponse toResponse(TestExecution execution) {
        return ExecutionResponse.builder()
                .executionId(execution.getId())
                .testId(execution.getTestId())
                .status(execution.getStatus())
                .startTime(execution.getStartTime())
                .endTime(execution.getEndTime())
                .durationMs(execution.getDuration())
                .errorMessage(execution.getErrorDetails())
                .screenshotUrls(execution.getScreenshotUrls() != null ?
                        Arrays.asList(execution.getScreenshotUrls()) : null)
                .logUrl(execution.getLogUrl())
                .externalExecutionRef(execution.getExternalExecutionRef())
                .build();
    }
}