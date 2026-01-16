package com.company.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI Configuration for multiple providers.
 *
 * Supports:
 * - Mock (for development)
 * - AWS Bedrock (Amazon Titan, AI21, Claude)
 * - Ollama (local AI)
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIConfig {

    /**
     * Enable/disable AI features globally.
     */
    private boolean enabled = true;

    /**
     * Active AI provider: mock, bedrock, or ollama
     */
    private String provider = "mock";

    /**
     * AWS Bedrock configuration
     */
    private BedrockConfig bedrock = new BedrockConfig();

    /**
     * Ollama configuration (local AI)
     */
    private OllamaConfig ollama = new OllamaConfig();

    @Data
    public static class BedrockConfig {
        private boolean enabled = false;
        private String region = "us-east-1";
        private String accessKeyId;
        private String secretAccessKey;

        /**
         * Model ID to use. Supported models:
         *
         * Amazon Titan (RECOMMENDED - no marketplace issues):
         * - amazon.titan-text-express-v1 (Balanced - DEFAULT)
         * - amazon.titan-text-lite-v1 (Cheapest)
         *
         * AI21 Jurassic:
         * - ai21.j2-ultra-v1 (Best quality)
         * - ai21.j2-mid-v1 (Balanced)
         *
         * Anthropic Claude (requires marketplace subscription):
         * - anthropic.claude-3-sonnet-20240229-v1:0
         * - anthropic.claude-3-haiku-20240307-v1:0
         */
        private String model = "us.amazon.nova-micro-v1:0"; // Changed default from Claude to Titan

        private Integer maxTokens = 4096;
        private Double temperature = 0.7;
        private Integer timeout = 120; // seconds
    }

    @Data
    public static class OllamaConfig {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:11434";
        private String model = "codellama";
        private Integer maxTokens = 4096;
        private Double temperature = 0.7;
        private Integer timeout = 120; // seconds
    }
}