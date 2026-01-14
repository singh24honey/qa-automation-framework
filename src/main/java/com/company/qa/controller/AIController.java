package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.service.ai.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AIController {

    private final AIService aiService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AIStatusDTO>> getStatus() {
        log.info("Getting AI service status");

        AIStatusDTO status = AIStatusDTO.builder()
                .provider(aiService.getProvider().name())
                .available(aiService.isAvailable())
                .message(aiService.isAvailable() ?
                        "AI service is ready" :
                        "AI service is unavailable")
                .build();

        return ResponseEntity.ok(ApiResponse.success(status, "AI status retrieved"));
    }

    @PostMapping("/generate-test")
    public ResponseEntity<ApiResponse<AIResponse>> generateTest(
            @RequestBody TestGenerationRequest request) {

        log.info("Generating test from description: {}", request.getDescription());

        if (!aiService.isAvailable()) {
            return ResponseEntity.ok(ApiResponse.<AIResponse>error(
                    "AI service is not available"  // ✅ FIXED
            ));
        }

        AIResponse response = aiService.generateTest(request);

        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Test generated successfully"
        ));
    }

    @PostMapping("/analyze-failure")
    public ResponseEntity<ApiResponse<AIResponse>> analyzeFailure(
            @RequestBody FailureAnalysisRequest request) {

        log.info("Analyzing failure for test: {}", request.getTestName());

        if (!aiService.isAvailable()) {
            return ResponseEntity.ok(ApiResponse.<AIResponse>error(
                    "AI service is not available"  // ✅ FIXED
            ));
        }

        AIResponse response = aiService.analyzeFailure(request);

        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Failure analyzed successfully"
        ));
    }

    @PostMapping("/suggest-fix")
    public ResponseEntity<ApiResponse<AIResponse>> suggestFix(
            @RequestBody FixSuggestionRequest request) {

        log.info("Suggesting fix for error");

        if (!aiService.isAvailable()) {
            return ResponseEntity.ok(ApiResponse.<AIResponse>error(
                    "AI service is not available"  // ✅ FIXED
            ));
        }

        AIResponse response = aiService.suggestFix(
                request.getTestCode(),
                request.getErrorMessage()
        );

        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Fix suggestions generated"
        ));
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<AIResponse>> execute(
            @RequestBody AIRequest request) {

        log.info("Executing custom AI task: {}", request.getTaskType());

        if (!aiService.isAvailable()) {
            return ResponseEntity.ok(ApiResponse.<AIResponse>error(
                    "AI service is not available"  // ✅ FIXED
            ));
        }

        AIResponse response = aiService.execute(request);

        return ResponseEntity.ok(ApiResponse.success(
                response,
                "AI task executed successfully"
        ));
    }
}