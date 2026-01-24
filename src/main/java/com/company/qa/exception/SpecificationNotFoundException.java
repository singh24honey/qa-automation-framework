package com.company.qa.exception;

/**
 * Exception thrown when specification is not found
 */
public class SpecificationNotFoundException extends RuntimeException {

    public SpecificationNotFoundException(Long id) {
        super("Specification not found: " + id);
    }

    public SpecificationNotFoundException(String name, String version) {
        super("Specification not found: " + name + " v" + version);
    }
}