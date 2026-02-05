package com.company.qa.service.secrets;

import com.company.qa.config.GitProperties;
import com.company.qa.exception.QaFrameworkException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import java.util.Map;

/**
 * Generic service for retrieving secrets from AWS Secrets Manager
 * Supports both Git and JIRA credentials
 */
@Service
@Slf4j
public class SecretsService {

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;
    private final boolean secretsEnabled;
    private final String region;

    public SecretsService(GitProperties gitProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.secretsEnabled = gitProperties.getSecrets().isEnabled();
        this.region = gitProperties.getSecrets().getRegion();

        if (secretsEnabled) {
            this.secretsManagerClient = SecretsManagerClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            log.info("AWS Secrets Manager enabled for Git operations - Region: {}", region);
        } else {
            this.secretsManagerClient = null;
            log.warn("AWS Secrets Manager DISABLED for Git operations - Using mock mode");
        }
    }

    /**
     * Get secret value from AWS Secrets Manager
     * Returns the raw secret string (could be plain text or JSON)
     *
     * @param secretKey The secret key/ARN to retrieve
     * @return The secret value as string
     */
    @Cacheable(value = "gitSecrets", key = "#secretKey", unless = "#result == null")
    public String getSecret(String secretKey) {
        log.debug("Fetching secret from AWS Secrets Manager: {}", maskSecretKey(secretKey));

        if (!secretsEnabled) {
            return getMockSecret(secretKey);
        }

        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretKey)
                    .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretString = response.secretString();

            log.info("Successfully retrieved secret: {}", maskSecretKey(secretKey));
            return secretString;

        } catch (SecretsManagerException e) {
            log.error("Failed to retrieve secret from AWS: {}", secretKey, e);
            throw new QaFrameworkException(
                    "Failed to retrieve secret: " + e.awsErrorDetails().errorMessage(), e
            );
        }
    }

    /**
     * Get secret value and parse as JSON
     * Useful for secrets stored as JSON objects
     *
     * @param secretKey The secret key/ARN to retrieve
     * @param fieldName The field name to extract from JSON
     * @return The field value from the JSON secret
     */
    @Cacheable(value = "gitSecrets", key = "#secretKey + ':' + #fieldName", unless = "#result == null")
    public String getSecretField(String secretKey, String fieldName) {
        String secretString = getSecret(secretKey);

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> secretMap = objectMapper.readValue(secretString, Map.class);

            String value = secretMap.get(fieldName);
            if (value == null) {
                throw new QaFrameworkException(
                        "Field '" + fieldName + "' not found in secret: " + maskSecretKey(secretKey)
                );
            }

            return value;

        } catch (Exception e) {
            log.error("Failed to parse secret JSON: {}", secretKey, e);
            throw new QaFrameworkException("Invalid secret format for: " + maskSecretKey(secretKey), e);
        }
    }

    /**
     * Get mock secret for testing/development
     */
    private String getMockSecret(String secretKey) {
        log.warn("Using MOCK secret for: {} - NOT FOR PRODUCTION", maskSecretKey(secretKey));

        // Return mock GitHub token
        if (secretKey.contains("git") || secretKey.contains("github")) {
            return "ghp_mock_token_for_development_do_not_use_in_production";
        }

        // Return mock GitLab token
        if (secretKey.contains("gitlab")) {
            return "glpat-mock_token_for_development";
        }

        // Default mock
        return "mock-secret-value-" + secretKey.hashCode();
    }

    /**
     * Mask secret key for logging
     */
    private String maskSecretKey(String secretKey) {
        if (secretKey == null || secretKey.length() < 8) {
            return "***";
        }

        // Show first 4 and last 4 characters
        int length = secretKey.length();
        return secretKey.substring(0, 4) + "***" + secretKey.substring(length - 4);
    }

    /**
     * Check if secrets are enabled
     */
    public boolean isSecretsEnabled() {
        return secretsEnabled;
    }

    /**
     * Get configured region
     */
    public String getRegion() {
        return region;
    }
}