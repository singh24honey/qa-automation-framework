package com.company.qa.service;

import com.company.qa.event.RequestLoggedEvent;
import com.company.qa.model.entity.RequestLog;
import com.company.qa.repository.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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

        RequestLog logEntity = RequestLog.builder()
                .apiKeyId(event.apiKeyId())
                .endpoint(event.endpoint())
                .method(event.method())
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .statusCode(event.statusCode())
                .responseTimeMs(event.responseTimeMs())
                .createdAt(event.createdAt())
                .build();

        requestLogRepository.save(logEntity);
    }

    @Transactional(readOnly = true)
    public long getRequestCount(UUID apiKeyId, int minutes) {
        Instant since = Instant.now().minus(minutes, ChronoUnit.MINUTES);
        return requestLogRepository.countByApiKeyIdAndCreatedAtAfter(apiKeyId, since);
    }
}