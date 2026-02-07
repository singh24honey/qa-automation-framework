package com.company.qa.model.playwright;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about a Page Object class discovered by scanning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageObjectInfo {

    /**
     * Fully qualified class name
     */
    private String className;

    /**
     * Simple class name
     */
    private String simpleName;

    /**
     * Package name
     */
    private String packageName;

    /**
     * Page URL pattern (if available)
     */
    private String pageUrl;

    /**
     * Public methods available in this Page Object
     */
    private List<PageObjectMethod> methods;

    /**
     * File path where this Page Object is located
     */
    private String filePath;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageObjectMethod {
        /**
         * Method name
         */
        private String name;

        /**
         * Return type
         */
        private String returnType;

        /**
         * Parameters
         */
        private List<MethodParameter> parameters;

        /**
         * Method signature for AI context
         */
        private String signature;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodParameter {
        /**
         * Parameter type
         */
        private String type;

        /**
         * Parameter name
         */
        private String name;
    }
}