package com.company.qa.exception;

/**
 * Exception thrown when approval workflow operations fail.
 */
public class ApprovalException extends RuntimeException {

    public ApprovalException(String message) {
        super(message);
    }

    public ApprovalException(String message, Throwable cause) {
        super(message, cause);
    }
}