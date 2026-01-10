package com.company.qa.exception;

public class ResourceNotFoundException extends QaFrameworkException {

    public ResourceNotFoundException(String resource, String id) {
        super(String.format("%s not found with id: %s", resource, id));
    }
}