package com.company.qa.event;

import java.time.Instant;
import java.util.UUID;

public record RequestLoggedEvent(
        UUID apiKeyId,
        String endpoint,
        String method,
        String ipAddress,
        String userAgent,
        Integer statusCode,
        Integer responseTimeMs,
        Instant createdAt
) {}