package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response containing user's AI usage statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatsResponse {

    /**
     * Total tokens used this month
     */
    private long tokensUsed;

    /**
     * Total cost this month (USD)
     */
    private double totalCost;

    /**
     * Requests made this hour
     */
    private int requestsThisHour;

    /**
     * Maximum requests per hour (based on role)
     */
    private int maxRequestsPerHour;

    /**
     * Remaining requests this hour
     */
    private int remainingRequests;

    /**
     * When rate limit resets
     */
    private Instant rateLimitResetTime;

    /**
     * User's role
     */
    private String role;

    /**
     * Statistics timestamp
     */
    private Instant timestamp;
}