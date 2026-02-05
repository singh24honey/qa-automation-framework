package com.company.qa.service.schedule;

import com.company.qa.model.entity.TestSchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleExecutorTask {

    private final ScheduleService scheduleService;

    /**
     * Check for due schedules every minute
     */
    @Scheduled(fixedRate = 60000)  // Every 60 seconds
    public void checkAndExecuteSchedules() {
        log.debug("Checking for scheduled tests...");

        try {
            List<TestSchedule> dueSchedules = scheduleService.getSchedulesDueForExecution();

            if (!dueSchedules.isEmpty()) {
                log.info("Found {} schedules due for execution", dueSchedules.size());
            }

            for (TestSchedule schedule : dueSchedules) {
                try {
                    log.info("Executing scheduled test: {} ({})",
                            schedule.getName(), schedule.getId());
                    scheduleService.processScheduledExecution(schedule);
                } catch (Exception e) {
                    log.error("Failed to execute schedule {}: {}",
                            schedule.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error checking schedules: {}", e.getMessage(), e);
        }
    }
}