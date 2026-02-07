package com.company.qa.model.playwright;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a page in the Element Registry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageInfo {

    /**
     * Page name/identifier
     */
    private String pageName;

    /**
     * URL pattern
     */
    private String url;

    /**
     * Page Object class (if exists)
     */
    private String pageObjectClass;

    /**
     * Page description
     */
    private String description;

    /**
     * Elements on this page
     * Key: element name, Value: ElementLocator
     */
    private Map<String, ElementLocator> elements;

    /**
     * Count of elements on this page
     */
    public int getElementCount() {
        return elements != null ? elements.size() : 0;
    }
}