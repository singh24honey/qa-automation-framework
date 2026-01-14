package com.company.qa.service.schedule;

import com.company.qa.exception.QaFrameworkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronServiceTest {

    private CronService cronService;

    @BeforeEach
    void setUp() {
        cronService = new CronService();
    }

    @Test
    @DisplayName("Should validate correct cron expression")
    void isValidCronExpression_ValidExpression_ReturnsTrue() {
        assertThat(cronService.isValidCronExpression("0 0 9 * * MON-FRI")).isTrue();
        assertThat(cronService.isValidCronExpression("0 */15 * * * *")).isTrue();
        assertThat(cronService.isValidCronExpression("0 0 0 * * *")).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid cron expression")
    void isValidCronExpression_InvalidExpression_ReturnsFalse() {
        assertThat(cronService.isValidCronExpression("invalid")).isFalse();
        assertThat(cronService.isValidCronExpression("")).isFalse();
    }

    @Test
    @DisplayName("Should describe cron expression")
    void describeCron_ValidExpression_ReturnsDescription() {
        String description = cronService.describeCron("0 0 9 * * MON-FRI");
        assertThat(description).isNotEmpty();
    }

    @Test
    @DisplayName("Should calculate next execution time")
    void getNextExecutionTime_ValidExpression_ReturnsNextTime() {
        Optional<Instant> nextTime = cronService.getNextExecutionTime(
                "0 0 9 * * *", "UTC");

        assertThat(nextTime).isPresent();
        assertThat(nextTime.get()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should throw for invalid expression on validate")
    void validateCronExpression_Invalid_ThrowsException() {
        assertThatThrownBy(() -> cronService.validateCronExpression("invalid"))
                .isInstanceOf(QaFrameworkException.class)
                .hasMessageContaining("Invalid cron expression");
    }
}