package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryConfig {

    @Builder.Default
    private Integer maxAttempts = 2;

    @Builder.Default
    private Integer delaySeconds = 5;

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    private Boolean retryOnTimeout = true;

    @Builder.Default
    private Boolean retryOnNetworkError = true;

    @Builder.Default
    private Boolean retryOnAssertionFailure = false;
}