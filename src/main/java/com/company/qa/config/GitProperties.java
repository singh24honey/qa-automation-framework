package com.company.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Git-related configuration properties
 */
@Configuration
@ConfigurationProperties(prefix = "git")
@Data
public class GitProperties {

    /**
     * Local directory for cloning repositories
     */
    private String workingDirectory = "/tmp/qa-framework/git-workspace";

    /**
     * Maximum retry attempts for Git operations
     */
    private int maxRetryAttempts = 3;

    /**
     * Timeout for Git operations (in seconds)
     */
    private int operationTimeout = 300;

    /**
     * Whether to use SSH for authentication
     */
    private boolean useSsh = false;

    /**
     * Whether to enable Git operations (can be disabled for testing)
     */
    private boolean enabled = true;

    /**
     * Whether to clean up local repositories after operations
     */
    private boolean cleanupAfterOperation = true;

    /**
     * AWS Secrets Manager configuration
     */
    private Secrets secrets = new Secrets();

    @Data
    public static class Secrets {
        private boolean enabled = true;
        private String region = "us-east-1";
    }
}