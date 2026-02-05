package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleUpdateRequest {

    private String name;
    private String description;
    private String cronExpression;
    private String timezone;
    private String browser;
    private String environment;
    private Boolean headless;
    private Boolean enabled;
}