package com.company.qa.model.dto;

import com.company.qa.model.enums.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {
    private String filename;
    private String path;
    private FileType type;
    private Long sizeBytes;
    private Instant createdAt;
    private String executionId;
}