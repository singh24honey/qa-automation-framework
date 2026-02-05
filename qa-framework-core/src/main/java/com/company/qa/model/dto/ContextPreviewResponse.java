package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Preview the context that will be sent to AI
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextPreviewResponse {

    private Long endpointId;
    private String method;
    private String path;
    private String summary;

    // Parameters info
    private Integer pathParamCount;
    private Integer queryParamCount;
    private Integer headerParamCount;

    // Schema info
    private Boolean hasRequestBody;
    private Boolean hasResponseBody;
    private List<String> referencedSchemas;

    // Full context preview
    private String contextPreview;

    // Estimated token usage
    private Integer estimatedTokens;
}