package com.company.qa.controller;

import com.company.qa.model.dto.ApiKeyDto;
import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/api-keys")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<ApiResponse<ApiKeyDto>> createApiKey(
            @RequestBody CreateApiKeyRequest request) {

        log.info("POST /api/v1/auth/api-keys - Creating API key: {}", request.getName());
        ApiKeyDto created = apiKeyService.createApiKey(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created,
                        "API key created successfully. Save the key value - it won't be shown again!"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyDto>>> getAllApiKeys() {
        log.info("GET /api/v1/auth/api-keys - Fetching all API keys");
        List<ApiKeyDto> apiKeys = apiKeyService.getAllApiKeys();
        return ResponseEntity.ok(ApiResponse.success(apiKeys));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApiKeyDto>> getApiKeyById(@PathVariable UUID id) {
        log.info("GET /api/v1/auth/api-keys/{} - Fetching API key", id);
        ApiKeyDto apiKey = apiKeyService.getApiKeyById(id);
        return ResponseEntity.ok(ApiResponse.success(apiKey));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(@PathVariable UUID id) {
        log.info("DELETE /api/v1/auth/api-keys/{} - Revoking API key", id);
        apiKeyService.revokeApiKey(id);
        return ResponseEntity.ok(ApiResponse.success(null, "API key revoked successfully"));
    }
}