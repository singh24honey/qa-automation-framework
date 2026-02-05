package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestStep {

    private String action;      // navigate, click, sendKeys, assertText, etc.
    private String locator;     // CSS selector, ID, XPath
    private String value;       // For sendKeys or assertions
    private Integer timeout;    // Optional timeout in seconds

    public Object LocatorValue;
}