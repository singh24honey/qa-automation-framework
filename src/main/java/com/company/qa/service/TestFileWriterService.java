package com.company.qa.service;

import com.company.qa.exception.StorageException;
import com.company.qa.model.entity.AIGeneratedTest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for writing generated test files to disk.
 *
 * Architecture:
 * - Draft folder: Temporary storage for unapproved tests
 * - Committed folder: Final destination after QA approval
 * - Maintains folder structure: /testType/jiraKey/timestamp/
 *
 * File structure example:
 * AiDraft/
 *   UI/
 *     PROJ-123/
 *       20250130_143022/
 *         LoginTest.feature
 *         LoginSteps.java
 *         LoginPage.java
 */
@Service
@Slf4j
public class TestFileWriterService {

    @Value("${ai.test-generation.draft-folder}")
    private String draftFolderPath;

    @Value("${ai.test-generation.committed-folder}")
    private String committedFolderPath;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Write generated test to draft folder.
     * Returns the full path where files were written.
     */
    public String writeToDraftFolder(AIGeneratedTest test, Map<String, Object> testCode) {
        log.info("Writing test {} to draft folder", test.getTestName());

        try {
            // Create folder structure: /testType/jiraKey/timestamp/
            Path testFolder = createDraftFolder(test);

            // Write files based on test framework
            switch (test.getTestFramework()) {
                case CUCUMBER:
                    writeCucumberFiles(testFolder, testCode);
                    break;
                case TESTNG:
                    writeTestNGFiles(testFolder, testCode);
                    break;
                default:
                    throw new StorageException("Failed to write test files");
            }

            log.info("Test files written successfully to: {}", testFolder);
            return testFolder.toString();

        } catch (IOException e) {
            throw new StorageException("Failed to write test files to draft folder", e);
        }
    }

    /**
     * Commit approved test from draft to committed folder.
     * Copies files and updates structure to match project conventions.
     */
    public String commitApprovedTest(AIGeneratedTest test) {
        log.info("Committing approved test {} to final location", test.getTestName());

        try {
            // Create committed folder structure
            Path committedFolder = createCommittedFolder(test);

            // Copy files from draft to committed location
            Path draftFolder = Paths.get(test.getDraftFolderPath());
            copyFolder(draftFolder, committedFolder);

            log.info("Test committed successfully to: {}", committedFolder);
            return committedFolder.toString();

        } catch (IOException e) {
            throw new StorageException("Failed to commit test files", e);
        }
    }

    /**
     * Delete test files from draft folder (for rejected tests).
     */
    public void deleteDraftFiles(AIGeneratedTest test) {
        log.info("Deleting draft files for rejected test {}", test.getTestName());

        try {
            Path draftFolder = Paths.get(test.getDraftFolderPath());
            if (Files.exists(draftFolder)) {
                deleteFolder(draftFolder);
                log.info("Draft files deleted: {}", draftFolder);
            }
        } catch (IOException e) {
            log.error("Failed to delete draft files: {}", e.getMessage());
            // Don't throw - this is cleanup, not critical
        }
    }

    // ============================================================
    // FOLDER CREATION
    // ============================================================

    private Path createDraftFolder(AIGeneratedTest test) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        Path folder = Paths.get(
                draftFolderPath,
                test.getTestType().name(),
                test.getJiraStoryKey(),
                timestamp
        );

        Files.createDirectories(folder);
        return folder;
    }

    private Path createCommittedFolder(AIGeneratedTest test) throws IOException {
        // Structure: src/test/resources/features/ or src/test/java/...
        Path folder;

        if (test.getTestFramework() == AIGeneratedTest.TestFramework.CUCUMBER) {
            folder = Paths.get(
                    committedFolderPath,
                    "resources",
                    "features",
                    test.getTestType().name().toLowerCase()
            );
        } else {
            folder = Paths.get(
                    committedFolderPath,
                    "java",
                    "com", "framework", "qa", "tests",
                    test.getTestType().name().toLowerCase()
            );
        }

        Files.createDirectories(folder);
        return folder;
    }

    // ============================================================
    // CUCUMBER FILE WRITING
    // ============================================================

    private void writeCucumberFiles(Path folder, Map<String, Object> testCode) throws IOException {
        // Write feature file
        if (testCode.containsKey("featureFile")) {
            String featureContent = testCode.get("featureFile").toString();
            Path featureFile = folder.resolve(extractFileName(featureContent, ".feature"));
            Files.writeString(featureFile, featureContent);
            log.debug("Written feature file: {}", featureFile);
        }

        // Write step definitions
        if (testCode.containsKey("stepDefinitions")) {
            @SuppressWarnings("unchecked")
            List<String> stepDefs = (List<String>) testCode.get("stepDefinitions");

            Path stepsFolder = folder.resolve("steps");
            Files.createDirectories(stepsFolder);

            for (String stepDef : stepDefs) {
                String fileName = extractJavaClassName(stepDef) + ".java";
                Path stepFile = stepsFolder.resolve(fileName);
                Files.writeString(stepFile, stepDef);
                log.debug("Written step definition: {}", stepFile);
            }
        }

        // Write page objects
        if (testCode.containsKey("pageObjects")) {
            @SuppressWarnings("unchecked")
            List<String> pageObjects = (List<String>) testCode.get("pageObjects");

            Path pagesFolder = folder.resolve("pages");
            Files.createDirectories(pagesFolder);

            for (String pageObject : pageObjects) {
                String fileName = extractJavaClassName(pageObject) + ".java";
                Path pageFile = pagesFolder.resolve(fileName);
                Files.writeString(pageFile, pageObject);
                log.debug("Written page object: {}", pageFile);
            }
        }
    }

    // ============================================================
    // TESTNG FILE WRITING
    // ============================================================

    private void writeTestNGFiles(Path folder, Map<String, Object> testCode) throws IOException {
        // Write test class
        if (testCode.containsKey("testClass")) {
            String testClass = testCode.get("testClass").toString();
            String fileName = extractJavaClassName(testClass) + ".java";
            Path testFile = folder.resolve(fileName);
            Files.writeString(testFile, testClass);
            log.debug("Written TestNG test: {}", testFile);
        }

        // Write page objects if present
        if (testCode.containsKey("pageObjects")) {
            @SuppressWarnings("unchecked")
            List<String> pageObjects = (List<String>) testCode.get("pageObjects");

            Path pagesFolder = folder.resolve("pages");
            Files.createDirectories(pagesFolder);

            for (String pageObject : pageObjects) {
                String fileName = extractJavaClassName(pageObject) + ".java";
                Path pageFile = pagesFolder.resolve(fileName);
                Files.writeString(pageFile, pageObject);
                log.debug("Written page object: {}", pageFile);
            }
        }
    }

    // ============================================================
    // FILE OPERATIONS
    // ============================================================

    private void copyFolder(Path source, Path destination) throws IOException {
        Files.walk(source)
                .forEach(sourcePath -> {
                    try {
                        Path targetPath = destination.resolve(source.relativize(sourcePath));
                        if (Files.isDirectory(sourcePath)) {
                            Files.createDirectories(targetPath);
                        } else {
                            Files.copy(sourcePath, targetPath);
                        }
                    } catch (IOException e) {
                        throw new StorageException("Failed to copy file: " + sourcePath, e);
                    }
                });
    }

    private void deleteFolder(Path folder) throws IOException {
        Files.walk(folder)
                .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before folders
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete: {}", path);
                    }
                });
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================

    /**
     * Extract file name from feature file content.
     * Looks for "Feature: <name>" and converts to file name.
     */
    private String extractFileName(String featureContent, String extension) {
        String[] lines = featureContent.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("Feature:")) {
                String featureName = line.substring(8).trim();
                return sanitizeFileName(featureName) + extension;
            }
        }
        return "GeneratedFeature" + extension;
    }

    /**
     * Extract Java class name from class content.
     * Looks for "public class <ClassName>".
     */
    private String extractJavaClassName(String classContent) {
        String[] lines = classContent.split("\n");
        for (String line : lines) {
            if (line.contains("public class ")) {
                int start = line.indexOf("public class ") + 13;
                int end = line.indexOf("{", start);
                if (end == -1) {
                    end = line.indexOf("implements", start);
                }
                if (end == -1) {
                    end = line.indexOf("extends", start);
                }
                if (end != -1) {
                    return line.substring(start, end).trim().split("\\s+")[0];
                }
            }
        }
        return "GeneratedClass";
    }

    /**
     * Sanitize file name (remove special characters).
     */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_")
                .replaceAll("_+", "_")
                .trim();
    }
}