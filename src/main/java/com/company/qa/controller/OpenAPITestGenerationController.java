package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.model.entity.ApiEndpoint;
import com.company.qa.model.entity.ApprovalRequest;
import com.company.qa.model.entity.TestGenerationContext;
import com.company.qa.model.enums.ApprovalRequestType;
import com.company.qa.model.enums.ApprovalStatus;
import com.company.qa.repository.ApiEndpointRepository;
import com.company.qa.repository.ApprovalRequestRepository;
import com.company.qa.repository.TestGenerationContextRepository;
import com.company.qa.service.openapi.OpenAPIContextBuilder;
import com.company.qa.service.openapi.openAPITestGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for OpenAPI-based AI test generation
 * CORRECTED VERSION - Uses proper entity fields and existing services
 */
@RestController
@RequestMapping("/api/openapi")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "OpenAPI Test Generation", description = "Generate tests using OpenAPI context")
public class OpenAPITestGenerationController {

    private final ApiEndpointRepository endpointRepository;
    private final openAPITestGenerationService testGenerationService;
    private final OpenAPIContextBuilder contextBuilder;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final TestGenerationContextRepository contextRepository;

    /**
     * Generate test for specific endpoint
     */
    /**
     * Generate test for specific endpoint
     */
    @PostMapping("/endpoints/{id}/generate-test")
    @Operation(summary = "Generate test for endpoint",
            description = "Generate AI test using OpenAPI endpoint context")
    public ResponseEntity<GenerateTestResponse> generateTest(
            @PathVariable Long id,
            @Valid @RequestBody GenerateTestRequest request) {

        log.info("Generate test request for endpoint: {}", id);

        // Find endpoint
        ApiEndpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found: " + id));

        try {
            // ✅ Call service with proper parameters
            openAPITestGenerationService.GeneratedTestResult result =
                    testGenerationService.generateTestForEndpoint(
                            endpoint,
                            request.getUserPrompt(),
                            request.getRequesterId(),
                            "QA_ENGINEER"
                    );

            // Check if generation was successful
            if (!result.getSuccess()) {
                GenerateTestResponse errorResponse = GenerateTestResponse.builder()
                        .status("FAILED")
                        .endpointId(id)
                        .endpointPath(endpoint.getPath())
                        .httpMethod(endpoint.getMethod())
                        .generatedAt(Instant.now())
                        .build();

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorResponse);
            }

            // ✅ Create approval request with proper UUID handling
            ApprovalRequest approvalRequest = createApprovalRequest(
                    result,
                    endpoint,
                    request
            );

            ApprovalRequest savedApproval = approvalRequestRepository.save(approvalRequest);
            log.info("Created approval request: {}", savedApproval.getId());

            // ✅ FIX: Update context with approval request ID (both are UUID)
            contextRepository.findById(result.getContextId())  // Use contextId (Long)
                    .ifPresent(context -> {
                        context.setApprovalRequestId(savedApproval.getId());  // UUID
                        contextRepository.save(context);
                    });

            // ✅ Build response
            GenerateTestResponse response = buildGenerateResponse(
                    result,
                    endpoint,
                    savedApproval
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to generate test for endpoint: {}", id, e);

            GenerateTestResponse errorResponse = GenerateTestResponse.builder()
                    .status("FAILED")
                    .endpointId(id)
                    .endpointPath(endpoint.getPath())
                    .httpMethod(endpoint.getMethod())
                    .generatedAt(Instant.now())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * ✅ FIXED: Build response - handle type conversions properly
     */
    private GenerateTestResponse buildGenerateResponse(
            openAPITestGenerationService.GeneratedTestResult result,
            ApiEndpoint endpoint,
            ApprovalRequest approval) {

        return GenerateTestResponse.builder()
                .testId(result.getTestId())  // Long from GeneratedTestResult
                .testName(result.getTestName())
                .status("PENDING_APPROVAL")
                .approvalRequestId(approval.getId())  // Convert UUID to String
                .approvalStatus(approval.getStatus().name())
                .endpointId(endpoint.getId())
                .endpointPath(endpoint.getPath())
                .httpMethod(endpoint.getMethod())
                .aiModel(result.getAiModel())
                .estimatedCost(result.getAiCost())
                .promptTokens(result.getTokensUsed())
                .responseTokens(result.getTokensUsed())
                .generatedAt(Instant.now())
                .codePreview(result.getGeneratedCode() != null ?
                        result.getGeneratedCode().substring(
                                0,
                                Math.min(200, result.getGeneratedCode().length())
                        ) : "")
                .build();
    }
    /**
     * Preview context before generation
     */
    @GetMapping("/endpoints/{id}/context")
    @Operation(summary = "Preview context",
            description = "See what context will be sent to AI")
    public ResponseEntity<ContextPreviewResponse> previewContext(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Generate a comprehensive test") String userPrompt) {

        log.info("Context preview for endpoint: {}", id);

        ApiEndpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found: " + id));

        // Build context
        String fullContext = contextBuilder.buildEndpointTestPrompt(endpoint, userPrompt);
        List<String> schemas = contextBuilder.extractReferencedSchemas(endpoint);

        // Count parameters
        int pathParams = countParams(endpoint.getPathParameters());
        int queryParams = countParams(endpoint.getQueryParameters());
        int headerParams = countParams(endpoint.getHeaderParameters());

        ContextPreviewResponse response = ContextPreviewResponse.builder()
                .endpointId(id)
                .method(endpoint.getMethod())
                .path(endpoint.getPath())
                .summary(endpoint.getSummary())
                .pathParamCount(pathParams)
                .queryParamCount(queryParams)
                .headerParamCount(headerParams)
                .hasRequestBody(endpoint.getRequestSchema() != null)
                .hasResponseBody(endpoint.getResponseSchema() != null)
                .referencedSchemas(schemas)
                .contextPreview(fullContext)
                .estimatedTokens(fullContext.length() / 4) // Rough estimate
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Batch generate tests for multiple endpoints
     */
    @PostMapping("/batch-generate")
    @Operation(summary = "Batch generate tests",
            description = "Generate tests for multiple endpoints")
    public ResponseEntity<List<GenerateTestResponse>> batchGenerate(
            @Valid @RequestBody BatchGenerateTestRequest request) {

        log.info("Batch generate for {} endpoints", request.getEndpointIds().size());

        List<GenerateTestResponse> responses = new ArrayList<>();

        for (Long endpointId : request.getEndpointIds()) {
            try {
                ApiEndpoint endpoint = endpointRepository.findById(endpointId)
                        .orElseThrow(() -> new IllegalArgumentException("Endpoint not found: " + endpointId));

                // Build user prompt
                String prompt = String.format(
                        "Generate a %s test in %s for %s %s",
                        request.getFramework(),
                        request.getLanguage(),
                        endpoint.getMethod(),
                        endpoint.getPath()
                );

                openAPITestGenerationService.GeneratedTestResult result =
                        testGenerationService.generateTestForEndpoint(
                                endpoint,
                                prompt,
                                request.getRequesterId(),
                                "QA_ENGINEER"
                        );

                if (!result.getSuccess()) {
                    responses.add(GenerateTestResponse.builder()
                            .endpointId(endpointId)
                            .status("FAILED")
                            .generatedAt(Instant.now())
                            .build());
                    continue;
                }

                // Create approval request
                GenerateTestRequest singleRequest = GenerateTestRequest.builder()
                        .userPrompt(prompt)
                        .framework(request.getFramework())
                        .language(request.getLanguage())
                        .requesterId(request.getRequesterId())
                        .priority(request.getPriority())
                        .justification(request.getJustification())
                        .build();

                ApprovalRequest approval = createApprovalRequest(
                        result,
                        endpoint,
                        singleRequest
                );

                ApprovalRequest savedApproval = approvalRequestRepository.save(approval);

                GenerateTestResponse response = buildGenerateResponse(
                        result,
                        endpoint,
                        savedApproval
                );

                responses.add(response);

            } catch (Exception e) {
                log.error("Failed to generate test for endpoint: {}", endpointId, e);

                GenerateTestResponse errorResponse = GenerateTestResponse.builder()
                        .endpointId(endpointId)
                        .status("FAILED")
                        .generatedAt(Instant.now())
                        .build();

                responses.add(errorResponse);
            }
        }

        return ResponseEntity.ok(responses);
    }

    /**
     * View generation history
     */
    @GetMapping("/generation-history")
    @Operation(summary = "View generation history",
            description = "See all AI-generated tests with context")
    public ResponseEntity<List<TestGenerationContext>> getGenerationHistory(
            @RequestParam(required = false) Long specId,
            @RequestParam(required = false) Boolean approved,
            @RequestParam(defaultValue = "50") int limit) {

        log.info("Fetching generation history");

        List<TestGenerationContext> history;

        if (specId != null) {
            history = contextRepository.findBySpecificationId(specId);
        } else if (approved != null) {
            history = contextRepository.findByApproved(approved);
        } else {
            history = contextRepository.findAll();
        }

        // Limit results
        if (history.size() > limit) {
            history = history.subList(0, limit);
        }

        return ResponseEntity.ok(history);
    }

    // ===== Helper Methods =====

    /**
     * ✅ CORRECT: Create approval request using proper entity fields
     */
    private ApprovalRequest createApprovalRequest(
            openAPITestGenerationService.GeneratedTestResult result,
            ApiEndpoint endpoint,
            GenerateTestRequest request) {

        String description = String.format(
                "AI-generated test for %s %s using OpenAPI context",
                endpoint.getMethod(),
                endpoint.getPath()
        );

        Instant now = Instant.now();

        return ApprovalRequest.builder()
                .requestType(ApprovalRequestType.TEST_GENERATION)
                .status(ApprovalStatus.PENDING_APPROVAL)
                .generatedContent(result.getGeneratedCode())  // ✅ Correct field name
                .testName(result.getTestName())
                .testFramework(request.getFramework())  // ✅ Correct field name
                .testLanguage(request.getLanguage())  // ✅ Correct field name
                .targetUrl(endpoint.getSpecification().getBaseUrl())
                .requestedById(UUID.fromString(request.getRequesterId().toString()))  // ✅ Convert to UUID
                .requestedByName("System")  // TODO: Get from user context
                .createdAt(now)  // ✅ Correct field name
                .expiresAt(now.plus(7, ChronoUnit.DAYS))  // Expires in 7 days
                .autoExecuteOnApproval(false)  // Manual execution
                .build();
    }

    /**
     * ✅ CORRECT: Build response from GeneratedTestResult
     */
    /*private GenerateTestResponse buildGenerateResponse(
            AITestGenerationService.GeneratedTestResult result,
            ApiEndpoint endpoint,
            ApprovalRequest approval) {

        return GenerateTestResponse.builder()
                .testId(result.getTestId())
                .testName(result.getTestName())
                .status("PENDING_APPROVAL")
                .approvalRequestId(approval.getId().toString())  // Convert UUID to String
                .approvalStatus(approval.getStatus().name())
                .endpointId(endpoint.getId())
                .endpointPath(endpoint.getPath())
                .httpMethod(endpoint.getMethod())
                .aiModel(result.getAiModel())
                .estimatedCost(result.getAiCost())
                .promptTokens(result.getTokensUsed())  // Reuse tokensUsed for now
                .responseTokens(result.getTokensUsed())
                .generatedAt(Instant.now())
                .codePreview(result.getGeneratedCode().substring(
                        0,
                        Math.min(200, result.getGeneratedCode().length())
                ))
                .build();
    }*/

    /**
     * Count parameters in JSON array
     */
    private int countParams(String paramsJson) {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return 0;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode params =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(paramsJson);
            return params.isArray() ? params.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}