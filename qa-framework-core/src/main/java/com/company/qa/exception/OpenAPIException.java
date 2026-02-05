package com.company.qa.exception;

/**
 * Exception for OpenAPI parsing and processing errors
 */
public class OpenAPIException extends RuntimeException {

    public OpenAPIException(String message) {
        super(message);
    }

    public OpenAPIException(String message, Throwable cause) {
        super(message, cause);
    }
}