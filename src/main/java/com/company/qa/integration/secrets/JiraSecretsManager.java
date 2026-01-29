package com.company.qa.integration.secrets;

import com.company.qa.config.JiraProperties;
import com.company.qa.exception.QaFrameworkException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import java.util.Map;

/**
 * Service for retrieving JIRA credentials from AWS Secrets Manager.
 * Credentials are cached to minimize API calls.
 */
@Slf4j
@Component
public class JiraSecretsManager {

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;
    private final JiraProperties jiraProperties;

    public JiraSecretsManager(JiraProperties jiraProperties, ObjectMapper objectMapper) {
        this.jiraProperties = jiraProperties;
        this.objectMapper = objectMapper;

        if (jiraProperties.getSecrets().isEnabled()) {
            this.secretsManagerClient = SecretsManagerClient.builder()
                    .region(Region.of(jiraProperties.getSecrets().getRegion()))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            log.info("AWS Secrets Manager enabled for region: {}",
                    jiraProperties.getSecrets().getRegion());
        } else {
            this.secretsManagerClient = null;
            log.warn("AWS Secrets Manager DISABLED - using mock credentials");
        }
    }

    @Cacheable(value = "jiraSecrets", key = "#secretArn", unless = "#result == null")
    public JiraCredentials getCredentials(String secretArn) {
        log.debug("Fetching JIRA credentials from secret: {}", secretArn);

        if (!jiraProperties.getSecrets().isEnabled()) {
            return getMockCredentials();
        }

        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretArn)
                    .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretString = response.secretString();

            @SuppressWarnings("unchecked")
            Map<String, String> secretMap = objectMapper.readValue(secretString, Map.class);

            String email = secretMap.get("email");
            String apiToken = secretMap.get("apiToken");
            String cloudId = secretMap.get("cloudId");

            if (email == null || apiToken == null) {
                throw new QaFrameworkException(
                        "Invalid secret format: missing 'email' or 'apiToken'"
                );
            }

            log.info("Successfully retrieved JIRA credentials for: {}", maskEmail(email));

            return JiraCredentials.builder()
                    .email(email)
                    .apiToken(apiToken)
                    .cloudId(cloudId)
                    .build();

        } catch (SecretsManagerException e) {
            log.error("Failed to retrieve secret from AWS: {}", secretArn, e);
            throw new QaFrameworkException(
                    "Failed to retrieve JIRA credentials: " + e.awsErrorDetails().errorMessage(), e
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to parse secret JSON: {}", secretArn, e);
            throw new QaFrameworkException("Invalid JIRA secret format", e);
        }
    }

    private JiraCredentials getMockCredentials() {
        log.warn("Using MOCK JIRA credentials - NOT FOR PRODUCTION");
        return JiraCredentials.builder()
                .email("test@example.com")
                .apiToken("mock-api-token-12345")
                .cloudId("mock-cloud-id")
                .build();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***@***.***";
        }
        String[] parts = email.split("@");
        return parts[0].charAt(0) + "***@" + parts[1];
    }
}