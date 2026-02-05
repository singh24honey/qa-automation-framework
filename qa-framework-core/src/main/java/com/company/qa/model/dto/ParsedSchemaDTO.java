package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for parsed schema component
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedSchemaDTO {

    private String schemaName;
    private String schemaType;  // object, array, string, etc.
    private String description;
    private String schemaDefinition;  // Full JSON schema

    private Boolean isEnum;
    private List<String> enumValues;

    // References
    private List<String> referencedBy;  // Which schemas reference this
    private List<String> references;    // Which schemas this references
}