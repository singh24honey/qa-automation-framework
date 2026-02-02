package com.company.qa.controller;

import com.company.qa.model.dto.request.TestApprovalRequest;
import com.company.qa.model.dto.request.TestGenerationRequest;
import com.company.qa.model.dto.response.TestApprovalResponse;
import com.company.qa.model.dto.response.TestGenerationResponse;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.service.ai.AITestGenerationService;
import com.company.qa.service.approval.TestApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for AI-powered test generation and approval workflow.
 */
@RestController
@RequestMapping("/api/v1/ai-tests")
@Tag(name = "AI Test Generation", description = "AI-powered test generation from JIRA stories")
@RequiredArgsConstructor
@Slf4j
public class AITestGenerationController {

    private final AITestGenerationService generationService;
    private final TestApprovalService approvalService;

    // ============================================================
    // TEST GENERATION ENDPOINTS
    // ============================================================

    @PostMapping("/generate")
    @Operation(summary = "Generate test from JIRA story")
    public ResponseEntity<TestGenerationResponse> generateTest(
            @Valid @RequestBody TestGenerationRequest request) {

        log.info("REST: Generate test request for story: {}", request.getJiraStoryKey());
        TestGenerationResponse response = generationService.generateTestFromStory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/generate/batch")
    @Operation(summary = "Generate tests for multiple JIRA stories")
    public ResponseEntity<List<TestGenerationResponse>> generateBatchTests(
            @Valid @RequestBody List<TestGenerationRequest> requests) {

        log.info("REST: Batch generate {} tests", requests.size());
        List<TestGenerationResponse> responses = requests.stream()
                .map(generationService::generateTestFromStory)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    // ============================================================
    // APPROVAL WORKFLOW ENDPOINTS
    // ============================================================

    @PostMapping("/approve")
    @Operation(summary = "Approve or reject a generated test")
    public ResponseEntity<TestApprovalResponse> approveTest(
            @Valid @RequestBody TestApprovalRequest request) {

        log.info("REST: Approval request for test ID: {}", request.getTestId());
        TestApprovalResponse response = approvalService.processApproval(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/approve/batch")
    @Operation(summary = "Batch approve/reject multiple tests")
    public ResponseEntity<List<TestApprovalResponse>> batchApprove(
            @Valid @RequestBody List<TestApprovalRequest> requests) {

        log.info("REST: Batch approval for {} tests", requests.size());
        List<TestApprovalResponse> responses = approvalService.batchApprove(requests);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/pending-reviews")
    @Operation(summary = "Get all tests pending review")
    public ResponseEntity<List<AIGeneratedTest>> getPendingReviews() {
        log.info("REST: Fetch pending reviews");
        List<AIGeneratedTest> tests = approvalService.getPendingReviews();
        return ResponseEntity.ok(tests);
    }

    @GetMapping("/reviewer/{reviewerName}")
    @Operation(summary = "Get tests reviewed by specific reviewer")
    public ResponseEntity<List<AIGeneratedTest>> getTestsByReviewer(
            @PathVariable String reviewerName) {

        log.info("REST: Fetch tests reviewed by: {}", reviewerName);
        List<AIGeneratedTest> tests = approvalService.getTestsByReviewer(reviewerName);
        return ResponseEntity.ok(tests);
    }
}