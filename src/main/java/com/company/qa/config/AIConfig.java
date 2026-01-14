package com.company.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AIConfig {

    private String provider = "mock"; // default to mock
    private BedrockConfig bedrock = new BedrockConfig();
    private OllamaConfig ollama = new OllamaConfig();
    private boolean enabled = true;

    @Data
    public static class BedrockConfig {
        private boolean enabled = false;
        private String region = "us-east-1";
        private String model = "anthropic.claude-3-sonnet-20240229-v1:0";
        private Integer maxTokens = 4096;
        private Double temperature = 0.7;
        private Integer timeout = 120; // seconds
    }

    @Data
    public static class OllamaConfig {
        private boolean enabled = true;
        private String url = "http://localhost:11434";
        private String model = "llama3";
        private Integer timeout = 120; // seconds
        private Double temperature = 0.7;
    }
}