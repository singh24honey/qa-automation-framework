package com.company.qa.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for RestTemplate beans.
 * Required for HTTP client operations (JIRA, Jenkins, etc.).
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Default RestTemplate bean with reasonable timeouts.
     * Used by JiraRestClient and other HTTP clients.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}