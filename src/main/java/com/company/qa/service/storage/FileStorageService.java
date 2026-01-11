package com.company.qa.service.storage;

import com.company.qa.config.StorageConfig;
import com.company.qa.exception.FileNotFoundException;
import com.company.qa.exception.StorageException;
import com.company.qa.model.dto.FileMetadata;
import com.company.qa.model.dto.StorageStats;
import com.company.qa.model.enums.FileType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final StorageConfig storageConfig;

    /**
     * Save a screenshot for a test execution
     */
    public FileMetadata saveScreenshot(String executionId, byte[] imageData, String stepName) {
        log.debug("Saving screenshot for execution: {}, step: {}", executionId, stepName);

        String filename = generateFilename(executionId, stepName, FileType.SCREENSHOT);
        Path targetPath = getExecutionPath(executionId, FileType.SCREENSHOT).resolve(filename);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, imageData);

            log.info("Saved screenshot: {}", targetPath);
            return buildMetadata(targetPath, FileType.SCREENSHOT, executionId);

        } catch (IOException e) {
            log.error("Failed to save screenshot: {}", e.getMessage(), e);
            throw new StorageException("Failed to save screenshot", e);
        }
    }

    /**
     * Save a log file for a test execution
     */
    public FileMetadata saveLog(String executionId, String content, String logName) {
        log.debug("Saving log for execution: {}, name: {}", executionId, logName);

        String filename = generateFilename(executionId, logName, FileType.LOG);
        Path targetPath = getExecutionPath(executionId, FileType.LOG).resolve(filename);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, content);

            log.info("Saved log: {}", targetPath);
            return buildMetadata(targetPath, FileType.LOG, executionId);

        } catch (IOException e) {
            log.error("Failed to save log: {}", e.getMessage(), e);
            throw new StorageException("Failed to save log", e);
        }
    }

    /**
     * Save a file from MultipartFile (for uploads)
     */
    public FileMetadata saveFile(String executionId, MultipartFile file, FileType fileType) {
        log.debug("Saving file for execution: {}, filename: {}", executionId, file.getOriginalFilename());

        validateFile(file);

        String filename = generateFilename(executionId, file.getOriginalFilename(), fileType);
        Path targetPath = getExecutionPath(executionId, fileType).resolve(filename);

        try {
            Files.createDirectories(targetPath.getParent());

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Saved file: {}", targetPath);
            return buildMetadata(targetPath, fileType, executionId);

        } catch (IOException e) {
            log.error("Failed to save file: {}", e.getMessage(), e);
            throw new StorageException("Failed to save file", e);
        }
    }

    /**
     * Retrieve a file as a Resource
     */
    public Resource loadFileAsResource(String executionId, String filename) {
        log.debug("Loading file: {} for execution: {}", filename, executionId);

        try {
            Path filePath = findFile(executionId, filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                log.debug("File loaded: {}", filePath);
                return resource;
            } else {
                throw new FileNotFoundException(filename);
            }

        } catch (MalformedURLException e) {
            log.error("Failed to load file: {}", e.getMessage(), e);
            throw new FileNotFoundException(filename);
        }
    }

    /**
     * List all files for an execution
     */
    public List<FileMetadata> listFiles(String executionId) {
        log.debug("Listing files for execution: {}", executionId);

        List<FileMetadata> files = new ArrayList<>();
        Path basePath = Paths.get(storageConfig.getBasePath());

        for (FileType fileType : FileType.values()) {
            Path executionPath = basePath.resolve(fileType.getDirectory()).resolve(executionId);

            if (Files.exists(executionPath)) {
                try (Stream<Path> paths = Files.list(executionPath)) {
                    paths.filter(Files::isRegularFile)
                            .forEach(path -> files.add(buildMetadata(path, fileType, executionId)));
                } catch (IOException e) {
                    log.warn("Failed to list files in: {}", executionPath, e);
                }
            }
        }

        return files;
    }

    /**
     * Delete all files for an execution
     */
    public void deleteExecutionFiles(String executionId) {
        log.info("Deleting all files for execution: {}", executionId);

        Path basePath = Paths.get(storageConfig.getBasePath());
        int deletedCount = 0;

        for (FileType fileType : FileType.values()) {
            Path executionPath = basePath.resolve(fileType.getDirectory()).resolve(executionId);

            if (Files.exists(executionPath)) {
                try {
                    FileUtils.deleteDirectory(executionPath.toFile());
                    deletedCount++;
                    log.debug("Deleted directory: {}", executionPath);
                } catch (IOException e) {
                    log.error("Failed to delete directory: {}", executionPath, e);
                }
            }
        }

        log.info("Deleted {} directories for execution: {}", deletedCount, executionId);
    }

    /**
     * Clean up old files based on retention policy
     */
    public int cleanupOldFiles() {
        log.info("Starting cleanup of old files (retention: {} days)", storageConfig.getRetentionDays());

        Instant cutoffDate = Instant.now().minus(storageConfig.getRetentionDays(), ChronoUnit.DAYS);
        Path basePath = Paths.get(storageConfig.getBasePath());
        int deletedCount = 0;

        for (FileType fileType : FileType.values()) {
            Path typeDir = basePath.resolve(fileType.getDirectory());

            if (Files.exists(typeDir)) {
                try (Stream<Path> executionDirs = Files.list(typeDir)) {
                    List<Path> oldDirs = executionDirs
                            .filter(Files::isDirectory)
                            .filter(path -> isOlderThan(path, cutoffDate))
                            .collect(Collectors.toList());

                    for (Path dir : oldDirs) {
                        try {
                            FileUtils.deleteDirectory(dir.toFile());
                            deletedCount++;
                            log.debug("Deleted old directory: {}", dir);
                        } catch (IOException e) {
                            log.error("Failed to delete directory: {}", dir, e);
                        }
                    }

                } catch (IOException e) {
                    log.error("Failed to list directories in: {}", typeDir, e);
                }
            }
        }

        log.info("Cleanup completed. Deleted {} old execution directories", deletedCount);
        return deletedCount;
    }

    /**
     * Get storage statistics
     */
    public StorageStats getStorageStats() {
        log.debug("Calculating storage statistics");

        Path basePath = Paths.get(storageConfig.getBasePath());
        Map<String, Long> filesByType = new HashMap<>();
        Map<String, Long> sizeByType = new HashMap<>();
        long totalSize = 0;
        long totalFiles = 0;
        final Instant[] oldestFileTime = {Instant.now()};

        for (FileType fileType : FileType.values()) {
            Path typeDir = basePath.resolve(fileType.getDirectory());

            if (Files.exists(typeDir)) {
                try (Stream<Path> paths = Files.walk(typeDir)) {
                    List<Path> files = paths.filter(Files::isRegularFile).collect(Collectors.toList());

                    long typeSize = files.stream()
                            .mapToLong(this::getFileSize)
                            .sum();

                    filesByType.put(fileType.name(), (long) files.size());
                    sizeByType.put(fileType.name(), typeSize);
                    totalFiles += files.size();
                    totalSize += typeSize;

                    files.stream()
                            .map(this::getFileCreationTime)
                            .filter(Objects::nonNull)
                            .min(Instant::compareTo)
                            .ifPresent(time -> {
                                if (time.isBefore(oldestFileTime[0])) {
                                    oldestFileTime[0] = time;
                                }
                            });

                } catch (IOException e) {
                    log.warn("Failed to calculate stats for: {}", typeDir, e);
                }
            }
        }

        int oldestDays = (int) ChronoUnit.DAYS.between(oldestFileTime[0], Instant.now());

        return StorageStats.builder()
                .totalFiles(totalFiles)
                .totalSizeBytes(totalSize)
                .totalSizeFormatted(formatFileSize(totalSize))
                .filesByType(filesByType)
                .sizeByType(sizeByType)
                .oldestFileDays(oldestDays)
                .build();
    }

    // Helper methods

    private Path getExecutionPath(String executionId, FileType fileType) {
        return Paths.get(storageConfig.getBasePath())
                .resolve(fileType.getDirectory())
                .resolve(executionId);
    }

    private String generateFilename(String executionId, String baseName, FileType fileType) {
        String timestamp = Instant.now().toString().replace(":", "-");
        String safeName = baseName.replaceAll("[^a-zA-Z0-9.-]", "_");
        return String.format("%s_%s.%s", safeName, timestamp, fileType.getExtension());
    }

    private Path findFile(String executionId, String filename) {
        Path basePath = Paths.get(storageConfig.getBasePath());

        for (FileType fileType : FileType.values()) {
            Path filePath = basePath.resolve(fileType.getDirectory())
                    .resolve(executionId)
                    .resolve(filename);

            if (Files.exists(filePath)) {
                return filePath;
            }
        }

        throw new FileNotFoundException(filename);
    }

    private FileMetadata buildMetadata(Path path, FileType fileType, String executionId) {
        return FileMetadata.builder()
                .filename(path.getFileName().toString())
                .path(path.toString())
                .type(fileType)
                .sizeBytes(getFileSize(path))
                .createdAt(getFileCreationTime(path))
                .executionId(executionId)
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new StorageException("Cannot store empty file");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            throw new StorageException("Invalid filename: " + filename);
        }

        String extension = getFileExtension(filename);
        if (!storageConfig.getAllowedExtensions().contains(extension.toLowerCase())) {
            throw new StorageException("File type not allowed: " + extension);
        }

        long maxSize = storageConfig.getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new StorageException("File size exceeds maximum: " + storageConfig.getMaxFileSizeMb() + "MB");
        }
    }

    private boolean isOlderThan(Path path, Instant cutoffDate) {
        try {
            Instant creationTime = Files.getLastModifiedTime(path).toInstant();
            return creationTime.isBefore(cutoffDate);
        } catch (IOException e) {
            log.warn("Failed to get file time for: {}", path, e);
            return false;
        }
    }

    private long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            log.warn("Failed to get file size: {}", path, e);
            return 0L;
        }
    }

    private Instant getFileCreationTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            log.warn("Failed to get file creation time: {}", path, e);
            return null;
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex + 1) : "";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}