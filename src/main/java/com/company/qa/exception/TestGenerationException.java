package com.company.qa.exception;

/**
 * Exception thrown when test generation fails.
 */
public class TestGenerationException extends RuntimeException {

    public TestGenerationException(String message) {
        super(message);
    }

    public TestGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}