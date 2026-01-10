package com.company.qa.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;

@Configuration
@ConfigurationProperties(prefix = "storage")
@Getter
@Slf4j
public class StorageConfig {

    private String type;
    private String basePath;
    private String screenshots;
    private String videos;
    private String logs;
    private String reports;

    public void setType(String type) {
        this.type = type;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public void setScreenshots(String screenshots) {
        this.screenshots = screenshots;
    }

    public void setVideos(String videos) {
        this.videos = videos;
    }

    public void setLogs(String logs) {
        this.logs = logs;
    }

    public void setReports(String reports) {
        this.reports = reports;
    }

    @PostConstruct
    public void init() {
        createDirectories();
    }

    private void createDirectories() {
        createDirectory(screenshots);
        createDirectory(videos);
        createDirectory(logs);
        createDirectory(reports);
        log.info("Storage directories initialized at: {}", basePath);
    }

    private void createDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                log.debug("Created directory: {}", path);
            }
        }
    }
}