package com.company.qa.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRequest {

    @NotBlank(message = "Schedule name is required")
    private String name;

    private String description;

    @NotNull(message = "Test ID is required")
    private UUID testId;

    @NotBlank(message = "Cron expression is required")
    private String cronExpression;

    @Builder.Default
    private String timezone = "UTC";

    @Builder.Default
    private String browser = "CHROME";

    private String environment;

    @Builder.Default
    private Boolean headless = true;

    @Builder.Default
    private Boolean enabled = true;
}