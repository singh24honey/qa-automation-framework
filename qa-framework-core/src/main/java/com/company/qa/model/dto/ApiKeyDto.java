package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyDto {

    private UUID id;
    private String keyValue;  // Only shown on creation
    private String name;
    private String description;
    private Boolean isActive;
    private Instant lastUsedAt;
    private Instant createdAt;
    private Instant expiresAt;
}