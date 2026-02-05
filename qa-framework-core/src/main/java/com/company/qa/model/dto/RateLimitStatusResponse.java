package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response containing rate limit status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitStatusResponse {

    /**
     * Is user currently within rate limits?
     */
    private boolean allowed;

    /**
     * Remaining requests in current window
     */
    private int remainingRequests;

    /**
     * Maximum requests allowed per hour
     */
    private int maxRequests;

    /**
     * When the rate limit window resets
     */
    private Instant resetTime;

    /**
     * User's role
     */
    private String role;

    /**
     * Response timestamp
     */
    private Instant timestamp;
}