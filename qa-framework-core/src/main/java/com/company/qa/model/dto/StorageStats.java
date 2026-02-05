package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageStats {
    private Long totalFiles;
    private Long totalSizeBytes;
    private String totalSizeFormatted;
    private Map<String, Long> filesByType;
    private Map<String, Long> sizeByType;
    private Integer oldestFileDays;
}