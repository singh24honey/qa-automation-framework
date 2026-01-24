package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for upload operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponseDTO {

    private Boolean success;
    private String message;
    private Long specificationId;
    private String specificationName;
    private String version;
    private Integer endpointsExtracted;
    private Integer schemasExtracted;
}