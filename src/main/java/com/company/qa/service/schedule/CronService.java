package com.company.qa.service.schedule;

import com.company.qa.exception.QaFrameworkException;
import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class CronService {

    private final CronParser parser;
    private final CronDescriptor descriptor;

    public CronService() {
        // Spring cron: second minute hour day month weekday
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING);
        this.parser = new CronParser(cronDefinition);
        this.descriptor = CronDescriptor.instance(Locale.ENGLISH);
    }

    /**
     * Validate a cron expression
     */
    public boolean isValidCronExpression(String cronExpression) {
        try {
            parser.parse(cronExpression);
            return true;
        } catch (Exception e) {
            log.debug("Invalid cron expression: {} - {}", cronExpression, e.getMessage());
            return false;
        }
    }

    /**
     * Validate cron expression and throw exception if invalid
     */
    public void validateCronExpression(String cronExpression) {
        if (!isValidCronExpression(cronExpression)) {
            throw new QaFrameworkException("Invalid cron expression: " + cronExpression +
                    ". Expected format: 'second minute hour day month weekday' (e.g., '0 0 9 * * MON-FRI')");
        }
    }

    /**
     * Get human-readable description of cron expression
     */
    public String describeCron(String cronExpression) {
        try {
            Cron cron = parser.parse(cronExpression);
            return descriptor.describe(cron);
        } catch (Exception e) {
            log.warn("Failed to describe cron: {}", e.getMessage());
            return "Custom schedule";
        }
    }

    /**
     * Calculate next execution time from now
     */
    public Optional<Instant> getNextExecutionTime(String cronExpression, String timezone) {
        return getNextExecutionTime(cronExpression, timezone, Instant.now());
    }

    /**
     * Calculate next execution time from a given instant
     */
    public Optional<Instant> getNextExecutionTime(String cronExpression, String timezone, Instant from) {
        try {
            Cron cron = parser.parse(cronExpression);
            ExecutionTime executionTime = ExecutionTime.forCron(cron);

            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime fromDateTime = from.atZone(zoneId);

            Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(fromDateTime);

            return nextExecution.map(ZonedDateTime::toInstant);

        } catch (Exception e) {
            log.error("Failed to calculate next execution: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Common cron expression examples
     */
    public static class CronExamples {
        public static final String EVERY_MINUTE = "0 * * * * *";
        public static final String EVERY_HOUR = "0 0 * * * *";
        public static final String EVERY_DAY_AT_9AM = "0 0 9 * * *";
        public static final String EVERY_DAY_AT_MIDNIGHT = "0 0 0 * * *";
        public static final String WEEKDAYS_AT_9AM = "0 0 9 * * MON-FRI";
        public static final String EVERY_MONDAY_AT_9AM = "0 0 9 * * MON";
        public static final String FIRST_DAY_OF_MONTH = "0 0 9 1 * *";
        public static final String EVERY_15_MINUTES = "0 */15 * * * *";
        public static final String EVERY_30_MINUTES = "0 */30 * * * *";
    }
}