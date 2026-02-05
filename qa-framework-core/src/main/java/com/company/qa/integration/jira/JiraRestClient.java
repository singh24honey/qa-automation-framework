package com.company.qa.integration.jira;

import com.company.qa.config.JiraProperties;
import com.company.qa.exception.QaFrameworkException;
import com.company.qa.integration.secrets.JiraCredentials;
import com.company.qa.integration.secrets.JiraSecretsManager;
import com.company.qa.model.dto.RetryConfig;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.service.execution.RetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;


/**
 * JIRA Cloud REST API client with retry logic and simple rate limiting.
 * CORRECTED: Uses actual RetryService API from Week 2.
 */
@Slf4j
@Component
public class JiraRestClient {

    private final RestTemplate restTemplate;
    private final JiraSecretsManager secretsManager;
    private final RetryService retryService;
    private final JiraProperties jiraProperties;
    private final JiraApiAuditLogger apiAuditLogger;

    // Simple token bucket rate limiter per config
    private final ConcurrentHashMap<String, TokenBucket> rateLimiters = new ConcurrentHashMap<>();

    public JiraRestClient(
            RestTemplate restTemplate,
            JiraSecretsManager secretsManager,
            RetryService retryService,
            JiraProperties jiraProperties,
            JiraApiAuditLogger apiAuditLogger
    ) {
        this.restTemplate = restTemplate;
        this.secretsManager = secretsManager;
        this.retryService = retryService;
        this.jiraProperties = jiraProperties;
        this.apiAuditLogger = apiAuditLogger;
    }

    /**
     * Execute a GET request to JIRA API.
     */
    public String get(
            JiraConfiguration config,
            String endpoint,
            Map<String, String> queryParams,
            String userId
    ) {
        return executeWithRetry(config, endpoint, HttpMethod.GET, null, queryParams, userId);
    }

    /**
     * Execute a POST request to JIRA API.
     */
    public String post(
            JiraConfiguration config,
            String endpoint,
            Object requestBody,
            String userId
    ) {
        return executeWithRetry(config, endpoint, HttpMethod.POST, requestBody, null, userId);
    }

    /**
     * Check JIRA API health.
     */
    public boolean checkHealth(JiraConfiguration config) {
        try {
            String response = get(config, "/rest/api/3/serverInfo", null, "system");
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            log.error("JIRA health check failed: {}", config.getConfigName(), e);
            return false;
        }
       // return  true;
    }

    /**
     * Execute request with retry and rate limiting.
     * CORRECTED: Uses actual RetryService API with RetryConfig.
     */
    private String executeWithRetry(
            JiraConfiguration config,
            String endpoint,
            HttpMethod method,
            Object requestBody,
            Map<String, String> queryParams,
            String userId
    ) {
        String requestId = generateRequestId();


        // Simple rate limiting
        TokenBucket bucket = rateLimiters.computeIfAbsent(
                config.getId().toString(),
                k -> new TokenBucket(config.getMaxRequestsPerMinute())
        );

        if (!bucket.tryAcquire()) {
            log.warn("Rate limited for JIRA config: {}", config.getConfigName());
            apiAuditLogger.logApiCall(
                    config, endpoint, method.name(), 429, requestId, 0,
                    "Rate limited", true, false, userId
            );
            throw new QaFrameworkException("JIRA API rate limit exceeded");
        }

        boolean isHealthCheck = endpoint.equals("/rest/api/3/myself")
                || endpoint.equals("/rest/api/3/serverInfo");
        // Build retry config from JiraProperties
        RetryConfig retryConfig = RetryConfig.builder()
                .enabled(true)
                .maxAttempts(jiraProperties.getApi().getMaxRetries())
                .delaySeconds(jiraProperties.getApi().getRetryBackoffMs() / 1000)
                .retryOnTimeout(true)
                .retryOnNetworkError(true)
                .retryOnAssertionFailure(false)
                .build();

        // Execute with retry using actual RetryService API
        RetryService.RetryResult<String> result = retryService.executeWithRetry(
                () -> executeRequest(config, endpoint, method, requestBody, queryParams, requestId, userId),
                retryConfig,
                "JIRA-" + method + "-" + endpoint
        );

        if (!result.isSuccess()) {
            log.error("JIRA API call failed after {} attempts", result.getAttempts());
            throw new QaFrameworkException(
                    "JIRA API call failed: " + endpoint,
                    new RuntimeException(result.getLastFailure() != null
                            ? result.getLastFailure().getErrorMessage()
                            : "Unknown error")
            );
        }

        return result.getResult();
    }

    private String executeRequestDirect(
            JiraConfiguration config,
            String endpoint,
            Map<String, String> queryParams,
            JiraCredentials credentials
    ) throws Exception {

        String fullUrl = buildUrl(config.getJiraUrl(), endpoint, queryParams);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(fullUrl);

            // Add headers
            httpGet.setHeader("Authorization", credentials.toBasicAuthHeader());
            httpGet.setHeader("Accept", "application/json");
            httpGet.setHeader("Content-Type", "application/json");

            log.info("🔍 DEBUG: Making direct HTTP call to: {}", fullUrl);

            return httpClient.execute(httpGet, response -> {
                int statusCode = response.getCode();
                String body = EntityUtils.toString(response.getEntity());

                log.info("🔍 DEBUG: Direct HTTP Status: {}", statusCode);
                log.info("🔍 DEBUG: Direct HTTP Body Length: {}", body.length());
                log.info("🔍 DEBUG: Direct HTTP Body (first 500): {}",
                        body.substring(0, Math.min(500, body.length())));

                return body;
            });
        }
    }

    /**
     * Execute the actual HTTP request.
     */
    private String executeRequest(
            JiraConfiguration config,
            String endpoint,
            HttpMethod method,
            Object requestBody,
            Map<String, String> queryParams,
            String requestId,
            String userId
    ) {
        Instant startTime = Instant.now();

        try {
            // Get credentials from AWS Secrets Manager
            JiraCredentials credentials = secretsManager.getCredentials(config.getSecretArn());

            // Build URL
            String fullUrl = buildUrl(config.getJiraUrl(), endpoint, queryParams);

            // ✅ USE DIRECT HTTP CLIENT (since RestTemplate.exchange() is broken)
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

                if (method == HttpMethod.GET) {
                    HttpGet httpGet = new HttpGet(fullUrl);
                    httpGet.setHeader("Authorization", credentials.toBasicAuthHeader());
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-Type", "application/json");
                    httpGet.setHeader("X-Request-ID", requestId);

                    log.debug("JIRA API {} {}", method, fullUrl);

                    String body = httpClient.execute(httpGet, response -> {
                        int statusCode = response.getCode();
                        String responseBody = EntityUtils.toString(response.getEntity());

                        // Validate response
                        if (responseBody == null || responseBody.trim().length() < 5) {
                            throw new RuntimeException(
                                    "JIRA API returned empty or truncated response for " + endpoint
                            );
                        }

                        // Log success
                        long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                        apiAuditLogger.logApiCall(
                                config, endpoint, method.name(), statusCode,
                                requestId, (int) durationMs, null, false, false, userId
                        );

                        return responseBody;
                    });

                    return body;

                } else if (method == HttpMethod.POST) {
                    HttpPost httpPost = new HttpPost(fullUrl);
                    httpPost.setHeader("Authorization", credentials.toBasicAuthHeader());
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-Type", "application/json");
                    httpPost.setHeader("X-Request-ID", requestId);

                    // Add request body if present
                    if (requestBody != null) {
                        String jsonBody = new ObjectMapper().writeValueAsString(requestBody);
                        httpPost.setEntity(new StringEntity(jsonBody,
                                org.apache.hc.core5.http.ContentType.APPLICATION_JSON));
                    }

                    log.debug("JIRA API {} {}", method, fullUrl);

                    String body = httpClient.execute(httpPost, response -> {
                        int statusCode = response.getCode();
                        String responseBody = EntityUtils.toString(response.getEntity());

                        // Log success
                        long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                        apiAuditLogger.logApiCall(
                                config, endpoint, method.name(), statusCode,
                                requestId, (int) durationMs, null, false, false, userId
                        );

                        return responseBody;
                    });

                    return body;
                } else {
                    throw new UnsupportedOperationException("HTTP method not supported: " + method);
                }
            }

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            apiAuditLogger.logApiCall(
                    config, endpoint, method.name(), e.getRawStatusCode(),
                    requestId, (int) durationMs, e.getMessage(), false,
                    shouldRetryHttpError(e), userId
            );
            throw new RuntimeException("JIRA API error: " + e.getMessage(), e);

        } catch (Exception e) {
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            apiAuditLogger.logApiCall(
                    config, endpoint, method.name(), null,
                    requestId, (int) durationMs, e.getMessage(), false,
                    true, userId
            );
            throw new RuntimeException("JIRA API error: " + e.getMessage(), e);
        }
    }

    /**
     * Determine if HTTP error should be retried.
     * Note: RetryService will use this info via FailureAnalyzer.
     */
    private boolean shouldRetryHttpError(Exception ex) {
        if (ex instanceof HttpServerErrorException) {
            int status = ((HttpServerErrorException) ex).getRawStatusCode();
            return status >= 500 && status != 501;
        }
        if (ex instanceof HttpClientErrorException) {
            int status = ((HttpClientErrorException) ex).getRawStatusCode();
            return status == 429 || status == 408;
        }
        return false;
    }

    private static String buildUrl(String baseUrl, String endpoint, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path(endpoint);

        if (queryParams != null) {
            queryParams.forEach(builder::queryParam);
        }

        return builder.toUriString();
    }

    private String generateRequestId() {
        return "jira-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Simple token bucket for rate limiting.
     */
    private static class TokenBucket {
        private final int maxTokens;
        private int tokens;
        private long lastRefill;

        TokenBucket(int tokensPerMinute) {
            this.maxTokens = tokensPerMinute;
            this.tokens = tokensPerMinute;
            this.lastRefill = System.currentTimeMillis();
        }

        synchronized boolean tryAcquire() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefill;
            if (elapsed >= 60000) { // 1 minute
                tokens = maxTokens;
                lastRefill = now;
            }
        }



    }
}