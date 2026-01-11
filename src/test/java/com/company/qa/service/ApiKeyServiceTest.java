package com.company.qa.service;

import com.company.qa.model.dto.ApiKeyDto;
import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.model.entity.ApiKey;
import com.company.qa.repository.ApiKeyRepository;
import com.company.qa.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private CreateApiKeyRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = CreateApiKeyRequest.builder()
                .name("Test API Key")
                .description("Test description")
                .expiresInDays(30)
                .build();
    }

    @Test
    @DisplayName("Should create API key successfully")
    void createApiKey_Success() {
        // Given
        ApiKey savedKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .keyValue("test-key-value")
                .keyHash(SecurityUtils.hashApiKey("test-key-value"))
                .name("Test API Key")
                .isActive(true)
                .build();

        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(savedKey);

        // When
        ApiKeyDto result = apiKeyService.createApiKey(createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getKeyValue()).isEqualTo(ApiKey.builder().keyValue("test-key-value").build().getKeyValue());
    }

    @Test
    @DisplayName("Should return null for inactive API key")
    void validateApiKey_InactiveKey_ReturnsNull() {
        // Given
        String keyValue = "inactive-key";
        ApiKey apiKey = ApiKey.builder()
                .keyValue(keyValue)
                .isActive(false)
                .build();

        when(apiKeyRepository.findByKeyValue(keyValue))
                .thenReturn(Optional.of(apiKey));

        // When
        ApiKey result = apiKeyService.validateApiKey(keyValue);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for expired API key")
    void validateApiKey_ExpiredKey_ReturnsNull() {
        // Given
        String keyValue = "expired-key";
        ApiKey apiKey = ApiKey.builder()
                .keyValue(keyValue)
                .isActive(true)
                .expiresAt(Instant.now().minusSeconds(3600)) // Expired 1 hour ago
                .build();

        when(apiKeyRepository.findByKeyValue(keyValue))
                .thenReturn(Optional.of(apiKey));

        // When
        ApiKey result = apiKeyService.validateApiKey(keyValue);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should revoke API key")
    void revokeApiKey_Success() {
        // Given
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = ApiKey.builder()
                .id(keyId)
                .isActive(true)
                .build();

        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(apiKey);

        // When
        apiKeyService.revokeApiKey(keyId);

        // Then
        verify(apiKeyRepository).save(argThat(key -> !key.getIsActive()));
    }
}