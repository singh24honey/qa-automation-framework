package com.company.qa.config;

import com.company.qa.scheduler.ExecutiveDataScheduler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Test configuration to provide mock beans that are not needed in integration tests.
 * This prevents "bean not found" errors in test context.
 */
@TestConfiguration
public class TestConfig {

    /**
     * Mock ExecutiveDataScheduler for tests that don't need actual scheduling.
     */
    @Bean
    @Primary
    public ExecutiveDataScheduler executiveDataScheduler() {
        return mock(ExecutiveDataScheduler.class);
    }
}