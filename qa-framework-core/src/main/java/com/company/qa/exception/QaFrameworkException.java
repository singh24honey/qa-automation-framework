package com.company.qa.exception;

public class QaFrameworkException extends RuntimeException {

    public QaFrameworkException(String message) {
        super(message);
    }

    public QaFrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}