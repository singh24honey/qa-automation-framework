package com.company.qa.controller;

import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.dto.ExecutionRequest;
import com.company.qa.model.dto.ExecutionResponse;
import com.company.qa.service.execution.TestExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@Slf4j
public class TestExecutionController {

    private final TestExecutionService testExecutionService;


   /* @PostMapping
    public ResponseEntity<ApiResponse<ExecutionResponse>> executeTestold(
            @RequestBody ExecutionRequest request) {

        log.info("POST /api/v1/executions - Executing test: {}", request.getTestId());

        ExecutionResponse response =
                testExecutionService.startExecution(request);

        // Wait briefly to see if execution starts
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Get initial status
        //ExecutionResponse response = future.getNow(null);

        if (response == null) {
            // Execution still starting, return queued status
            response = ExecutionResponse.builder()
                    .status(com.company.qa.model.enums.TestStatus.QUEUED)
                    .build();
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response, "Test execution started"));
    }*/

    @PostMapping
    public ResponseEntity<ApiResponse<ExecutionResponse>> executeTest(
            @RequestBody ExecutionRequest request) {

        log.info("POST /api/v1/executions - Executing test: {}", request.getTestId());

        ExecutionResponse response =
                testExecutionService.startExecution(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response, "Test execution started"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExecutionResponse>> getExecutionStatus(
            @PathVariable UUID id) {

        log.info("GET /api/v1/executions/{} - Getting execution status", id);

        ExecutionResponse response = testExecutionService.getExecutionStatus(id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

   /* @GetMapping({"", "/"})
    public ResponseEntity<ApiResponse<List<ExecutionResponse>>> getRecentExecutions(
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/v1/executions - Getting recent executions (limit: {})", limit);

        List<ExecutionResponse> executions = testExecutionService.getRecentExecutions(limit);

        return ResponseEntity.ok(ApiResponse.success(executions));
    }*/

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExecutionResponse>>> getExecutions(
            @RequestParam(required = false) UUID testId,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/v1/executions - testId={}, limit={}", testId, limit);

        List<ExecutionResponse> executions =
                testExecutionService.getExecutions(testId, limit);

        return ResponseEntity.ok(ApiResponse.success(executions));
    }


}