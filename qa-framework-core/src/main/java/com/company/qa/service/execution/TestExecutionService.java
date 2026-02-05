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
import com.company.qa.model.dto.ExecutionRequest;
import com.company.qa.model.dto.ExecutionResponse;
import com.company.qa.model.dto.RetryConfig;
import com.company.qa.model.dto.TestScript;
import com.company.qa.model.entity.Test;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.quality.model.QualityGateResult;
import com.company.qa.quality.service.QualityGateService;
import com.company.qa.repository.TestExecutionRepository;
import com.company.qa.repository.TestRepository;
import com.company.qa.service.quality.TestQualityHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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

    @Autowired
    private TestQualityHistoryService qualityHistoryService;  // Inject


    @Value("${execution.timeout-minutes:10}")
    private int timeoutMinutes;


    @Transactional
    public ExecutionResponse startExecution(ExecutionRequest request) {

        TestExecution execution = TestExecution.builder()
                .testId(request.getTestId())
                .status(TestStatus.QUEUED)
                .environment(request.getEnvironment())
                .browser(request.getBrowser())
                .platform("Selenium Grid")
                .triggeredBy("API")
                .retryCount(0)
                .externalExecutionRef(UUID.randomUUID().toString())
                .startTime(Instant.now())
                .build();

        execution = testExecutionRepository.save(execution);

        // Fire async execution
        executeAsyncInternal(execution.getId(), request);

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
        try {
            return objectMapper.readValue(content, TestScript.class);
        } catch (Exception e) {
            log.error("Failed to parse test script: {}", e.getMessage());
            throw new RuntimeException("Invalid test script format", e);
        }
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