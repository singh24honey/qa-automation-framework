package com.company.qa.service.execution;

import com.company.qa.exception.ResourceNotFoundException;
import com.company.qa.model.dto.ExecutionRequest;
import com.company.qa.model.dto.ExecutionResponse;
import com.company.qa.model.dto.TestScript;
import com.company.qa.model.entity.Test;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.repository.TestExecutionRepository;
import com.company.qa.repository.TestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            execution.setStatus(TestStatus.RUNNING);
            testExecutionRepository.save(execution);

            Test test = testRepository.findById(request.getTestId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Test", request.getTestId().toString()));

            TestScript testScript = parseTestScript(test.getContent());

            boolean headless = request.getHeadless() != null ? request.getHeadless() : true;
            String browser = request.getBrowser() != null ? request.getBrowser() : "CHROME";

            SeleniumTestExecutor.ExecutionResult result =
                    seleniumTestExecutor.execute(
                            executionId.toString(),
                            testScript,
                            browser,
                            headless
                    );

            execution.setEndTime(Instant.now());
            execution.setDuration(result.getDurationMs());
            execution.setStatus(result.isSuccess() ? TestStatus.PASSED : TestStatus.FAILED);
            execution.setErrorDetails(result.getErrorMessage());
            execution.setLogUrl(result.getLogUrl());

            if (result.getScreenshotUrls() != null) {
                execution.setScreenshotUrls(result.getScreenshotUrls().toArray(new String[0]));
            }

            testExecutionRepository.save(execution);

        } catch (Exception e) {
            execution.setStatus(TestStatus.ERROR);
            execution.setEndTime(Instant.now());
            execution.setErrorDetails(e.getMessage());
            testExecutionRepository.save(execution);
        }
    }

    @Async("taskExecutor")
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
                .build();
    }
}