package com.company.qa.service;

import com.company.qa.config.RequestLoggingProperties;
import com.company.qa.event.RequestLoggedEvent;
import com.company.qa.model.entity.RequestLog;
import com.company.qa.repository.RequestLogRepository;
import com.company.qa.service.eventbridge.EventBridgePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestLogService {

    private final RequestLogRepository requestLogRepository;
    private final EventBridgePublisher eventBridgePublisher;
    private final RequestLoggingProperties props;



    /**
     * Single async entry point for request logging
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(RequestLoggedEvent event) {

        try {
            switch (props.getMode()) {

                case "postgres" -> persist(event);

                case "eventbridge" -> {
                    // STEP 3: real EventBridge publisher
                    publish(event);
                    log.info("[EventBridge] Publishing request event {}", event.apiKeyId());
                }

                case "hybrid" -> {
                    persist(event);
                    publish(event);
                    log.info("[EventBridge] Publishing request event {}", event.apiKeyId());
                }

                default -> {
                    log.warn("Unknown logging mode '{}', defaulting to postgres", props.getMode());
                    persist(event);
                }
            }
        } catch (Exception e) {
            // IMPORTANT: logging must never break requests
            log.error("Request logging failed", e);
        }
    }

    /**
     * DB persistence (SYNC, transactional)
     */
    private void persist(RequestLoggedEvent event) {

        RequestLog entity = RequestLog.builder()
                .apiKeyId(event.apiKeyId())
                .endpoint(event.endpoint())
                .method(event.method())
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .statusCode(event.statusCode())
                .responseTimeMs(event.responseTimeMs())
                .createdAt(event.createdAt())
                .build();

        requestLogRepository.save(entity);
        log.debug("Request log persisted");
    }

    /**
     * Used by rate limiter — MUST stay synchronous
     */
    @Transactional(readOnly = true)
    public long getRequestCount(UUID apiKeyId, int minutes) {
        Instant since = Instant.now().minus(minutes, ChronoUnit.MINUTES);
        return requestLogRepository.countByApiKeyIdAndCreatedAtAfter(apiKeyId, since);
    }

    private void publish(RequestLoggedEvent event) {
       // eventBridgePublisher.ifPresent(p -> p.publish(event));
         eventBridgePublisher.publish(event);
    }
}