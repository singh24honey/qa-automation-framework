package com.company.qa.exception;

public class FileNotFoundException extends StorageException {

    public FileNotFoundException(String filename) {
        super("File not found: " + filename);
    }
}