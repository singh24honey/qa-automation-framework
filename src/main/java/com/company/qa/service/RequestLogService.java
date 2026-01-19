package com.company.qa.service;

import com.company.qa.event.RequestLoggedEvent;
import com.company.qa.model.entity.RequestLog;
import com.company.qa.repository.RequestLogRepository;
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
    private final ApplicationEventPublisher eventPublisher;

    @Value("${logging.request.mode:postgres}")
    private String mode;

    /**
     * Persist request log to database
     */
    @Transactional
    public void logToDatabase(RequestLog entity) {
        requestLogRepository.save(entity);
    }

    /**
     * Publish domain event (no DB dependency)
     */
    public void publishEvent(RequestLoggedEvent event) {
        eventPublisher.publishEvent(event);
    }
   // @Async
    //@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logRequest(RequestLoggedEvent event) {
       /* RequestLog requestLog = RequestLog.builder()
                .apiKeyId(apiKeyId)
                .method(method)
                .endpoint(endpoint)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .statusCode(statusCode)
                .responseTimeMs(responseTimeMs)
                .build();

        requestLogRepository.save(requestLog);*/

       /* RequestLog logEntity = RequestLog.builder()
                .apiKeyId(event.apiKeyId())
                .endpoint(event.endpoint())
                .method(event.method())
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .statusCode(event.statusCode())
                .responseTimeMs(event.responseTimeMs())
                .createdAt(event.createdAt())
                .build();*/

        //requestLogRepository.save(logEntity);

    }
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(RequestLoggedEvent event) {

        switch (mode) {

            case "postgres" -> saveToDatabase(event);

            case "eventbridge" -> {
                // later: send to EventBridge
                log.info("EventBridge logging enabled, skipping DB");
            }

            case "hybrid" -> {
                saveToDatabase(event);
                log.info("EventBridge logging enabled");
            }

            default -> {
                log.warn("Unknown mode {}, defaulting to postgres", mode);
                saveToDatabase(event);
            }
        }
    }


    @Async
    void saveToDatabase(RequestLoggedEvent event) {
        try {
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
        } catch (Exception e) {
            log.error("Failed to persist request log", e);
        }
    }


    @Transactional(readOnly = true)
    public long getRequestCount(UUID apiKeyId, int minutes) {
        Instant since = Instant.now().minus(minutes, ChronoUnit.MINUTES);
        return requestLogRepository.countByApiKeyIdAndCreatedAtAfter(apiKeyId, since);
    }
}