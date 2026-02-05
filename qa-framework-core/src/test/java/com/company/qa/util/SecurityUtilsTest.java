package com.company.qa.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @Test
    @DisplayName("Should generate unique API keys")
    void generateApiKey_ProducesUniqueKeys() {
        // When
        String key1 = SecurityUtils.generateApiKey();
        String key2 = SecurityUtils.generateApiKey();


        // Then
        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1).hasSize(43); // Base64 encoded 32 bytes
        assertThat(key2).hasSize(43);
    }

    @Test
    @DisplayName("Should hash API key consistently")
    void hashApiKey_ProducesConsistentHash() {
        // Given
        String apiKey = "test-api-key-12345";

        // When
        String hash1 = SecurityUtils.hashApiKey(apiKey);
        String hash2 = SecurityUtils.hashApiKey(apiKey);

        System.out.println(hash1 + " " + hash2);
        // Then
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    @DisplayName("Should verify API key against hash")
    void verifyApiKey_WithValidKey_ReturnsTrue() {
        // Given
        String apiKey = "test-api-key-12345";
        String hash = SecurityUtils.hashApiKey(apiKey);

        // When
        boolean isValid = SecurityUtils.verifyApiKey(apiKey, hash);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid API key")
    void verifyApiKey_WithInvalidKey_ReturnsFalse() {
        // Given
        String apiKey = "test-api-key-12345";
        String hash = SecurityUtils.hashApiKey(apiKey);
        String wrongKey = "wrong-key";

        // When
        boolean isValid = SecurityUtils.verifyApiKey(wrongKey, hash);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should extract IP from X-Forwarded-For")
    void getClientIpAddress_WithXForwardedFor_ReturnsFirstIP() {
        // Given
        String xForwardedFor = "203.0.113.1, 198.51.100.1";
        String remoteAddr = "192.0.2.1";

        // When
        String ip = SecurityUtils.getClientIpAddress(xForwardedFor, remoteAddr);

        // Then
        assertThat(ip).isEqualTo("203.0.113.1");
    }

    @Test
    @DisplayName("Should fall back to remote address")
    void getClientIpAddress_WithoutXForwardedFor_ReturnsRemoteAddr() {
        // Given
        String xForwardedFor = null;
        String remoteAddr = "192.0.2.1";

        // When
        String ip = SecurityUtils.getClientIpAddress(xForwardedFor, remoteAddr);

        // Then
        assertThat(ip).isEqualTo("192.0.2.1");
    }
}