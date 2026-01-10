package com.company.qa.model.dto;

import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestDto {

    private UUID id;
    private String name;
    private String description;
    private TestFramework framework;
    private String language;
    private Priority priority;
    private Integer estimatedDuration;
    private Boolean isActive;
}