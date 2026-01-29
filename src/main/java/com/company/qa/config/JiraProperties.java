package com.company.qa.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * JIRA integration configuration properties.
 * Binds to 'jira.*' properties in application.yml.
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

    private SecretsConfig secrets = new SecretsConfig();
    private ApiConfig api = new ApiConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private HealthConfig health = new HealthConfig();
    private SecurityConfig security = new SecurityConfig();

    @Data
    public static class SecretsConfig {
        private boolean enabled = true;

        @NotBlank
        private String region = "us-east-1";

        @Min(60)
        private long cacheTtlSeconds = 3600;
    }

    @Data
    public static class ApiConfig {
        @Min(1000)
        private int connectTimeoutMs = 5000;

        @Min(1000)
        private int readTimeoutMs = 10000;

        @Min(0)
        private int maxRetries = 3;

        @Min(100)
        private int retryBackoffMs = 1000;
    }

    @Data
    public static class RateLimitConfig {
        @Min(1)
        private int requestsPerMinute = 60;

        @Min(1)
        private int burstCapacity = 10;
    }

    @Data
    public static class HealthConfig {
        private boolean enabled = true;

        @Min(30)
        private int intervalSeconds = 300;
    }

    @Data
    public static class SecurityConfig {
        private boolean validateSsl = true;

        @NotEmpty
        private List<String> allowedDomains = List.of("*.atlassian.net");
    }
}