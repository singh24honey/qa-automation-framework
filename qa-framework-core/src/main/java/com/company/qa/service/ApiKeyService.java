package com.company.qa.service;

import com.company.qa.event.ApiKeyCreatedEvent;
import com.company.qa.exception.ResourceNotFoundException;
import com.company.qa.model.dto.ApiKeyDto;
import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.model.entity.ApiKey;
import com.company.qa.repository.ApiKeyRepository;
import com.company.qa.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;


    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ApiKeyDto createApiKey(CreateApiKeyRequest request) {
        log.info("Creating new API key: {}", request.getName());

        // Generate unique API key
        String keyValue = SecurityUtils.generateApiKey();
        String keyHash = SecurityUtils.hashApiKey(keyValue);

        // Calculate expiration
        Instant expiresAt = null;
        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            expiresAt = Instant.now().plus(request.getExpiresInDays(), ChronoUnit.DAYS);
        }

        ApiKey apiKey = ApiKey.builder()
                .keyValue(keyValue)
                .keyHash(keyHash)
                .name(request.getName())
                .description(request.getDescription())
                .isActive(true)
                .expiresAt(expiresAt)
                .createdBy("system") // TODO: Get from security context
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("Created API key with id: {}", saved.getId());

        eventPublisher.publishEvent(
                new ApiKeyCreatedEvent(saved.getId()));
        // Return DTO with the actual key value (only time it's shown)
        return toDto(saved, true);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto> getAllApiKeys() {
        return apiKeyRepository.findAll().stream()
                .map(key -> toDto(key, false))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiKeyDto getApiKeyById(UUID id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API Key", id.toString()));
        return toDto(apiKey, false);
    }

    @Transactional
    public void revokeApiKey(UUID id) {
        log.info("Revoking API key: {}", id);
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API Key", id.toString()));

        apiKey.setIsActive(false);
        apiKeyRepository.save(apiKey);
        log.info("Revoked API key: {}", id);
    }

    /**
     * Update lastUsedAt using a direct SQL UPDATE.
     *
     * The old findById → save pattern caused ObjectOptimisticLockingFailureException
     * when concurrent requests (UI polling + agent API calls) all tried to update the
     * same ApiKey row simultaneously — the @Version field rejected all but one.
     *
     * Fix: direct JPQL UPDATE skips the version check entirely. We also only update
     * if the key hasn't been used in the last 30 seconds, eliminating redundant writes.
     */
    @Transactional
    public void updateLastUsed(UUID apiKeyId) {
        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(30); // Only update if >30s since last use
        apiKeyRepository.updateLastUsedAt(apiKeyId, now, threshold);
    }

    @Transactional(readOnly = true)
    public ApiKey validateApiKey(String keyValue) {
        if (keyValue == null || keyValue.trim().isEmpty()) {
            return null;
        }

        return apiKeyRepository.findByKeyValue(keyValue)
                .filter(ApiKey::isValid)
                .orElse(null);
    }

    private ApiKeyDto toDto(ApiKey apiKey, boolean includeKeyValue) {
        return ApiKeyDto.builder()
                .id(apiKey.getId())
                .keyValue(includeKeyValue ? apiKey.getKeyValue() : null)
                .name(apiKey.getName())
                .description(apiKey.getDescription())
                .isActive(apiKey.getIsActive())
                .lastUsedAt(apiKey.getLastUsedAt())
                .createdAt(apiKey.getCreatedAt())
                .expiresAt(apiKey.getExpiresAt())
                .build();
    }
}