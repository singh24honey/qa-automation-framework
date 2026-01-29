package com.company.qa.integration.jira;

import com.company.qa.model.entity.JiraApiAuditLog;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.repository.JiraApiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async logger for JIRA API audit trail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JiraApiAuditLogger {

    private final JiraApiAuditLogRepository auditLogRepository;

    /**
     * Logs a JIRA API call asynchronously.
     * Does NOT block the API call.
     */
    @Async
    public void logApiCall(
            JiraConfiguration config,
            String endpoint,
            String httpMethod,
            Integer statusCode,
            String requestId,
            Integer durationMs,
            String errorMessage,
            Boolean rateLimited,
            Boolean retried,
            String userId
    ) {
        try {
            JiraApiAuditLog auditLog = JiraApiAuditLog.builder()
                    .config(config)
                    .endpoint(endpoint)
                    .httpMethod(httpMethod)
                    .statusCode(statusCode)
                    .requestId(requestId)
                    .durationMs(durationMs)
                    .errorMessage(errorMessage)
                    .rateLimited(rateLimited)
                    .retried(retried)
                    .createdBy(userId)
                    .build();

            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            log.error("Failed to log JIRA API call (non-blocking)", e);
        }
    }
}