package com.company.qa.model.enums;

public enum FileType {
    SCREENSHOT("screenshots", "png"),
    VIDEO("videos", "mp4"),
    LOG("logs", "log"),
    REPORT("reports", "html"),
    JSON("logs", "json");

    private final String directory;
    private final String extension;

    FileType(String directory, String extension) {
        this.directory = directory;
        this.extension = extension;
    }

    public String getDirectory() {
        return directory;
    }

    public String getExtension() {
        return extension;
    }
}