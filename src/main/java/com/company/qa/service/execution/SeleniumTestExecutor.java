package com.company.qa.service.execution;

import com.company.qa.model.dto.TestScript;
import com.company.qa.model.dto.TestStep;
import com.company.qa.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeleniumTestExecutor {

    private final WebDriverFactory webDriverFactory;
    private final FileStorageService fileStorageService;

    public ExecutionResult execute(String executionId, TestScript testScript, String browser, boolean headless) {
        log.info("Executing test: {} on browser: {}", testScript.getName(), browser);

        WebDriver driver = null;
        ExecutionResult result = new ExecutionResult();
        result.setExecutionId(executionId);
        result.setTestName(testScript.getName());
        result.setStartTime(System.currentTimeMillis());
        result.setSuccess(false);

        List<String> screenshotUrls = new ArrayList<>();
        List<String> executionLogs = new ArrayList<>();

        try {
            // Create WebDriver
            driver = webDriverFactory.createDriver(browser, headless);
            executionLogs.add("WebDriver created successfully: " + browser);

            // Execute each step
            for (int i = 0; i < testScript.getSteps().size(); i++) {
                TestStep step = testScript.getSteps().get(i);

                try {
                    log.debug("Executing step {}: {}", i + 1, step.getAction());
                    executeStep(driver, step, executionId, i + 1);
                    executionLogs.add(String.format("Step %d: %s - PASSED", i + 1, step.getAction()));

                } catch (Exception e) {
                    log.error("Step {} failed: {}", i + 1, e.getMessage());
                    executionLogs.add(String.format("Step %d: %s - FAILED: %s", i + 1, step.getAction(), e.getMessage()));

                    // Capture failure screenshot
                    String screenshotUrl = captureScreenshot(driver, executionId, "step-" + (i + 1) + "-failure");
                    if (screenshotUrl != null) {
                        screenshotUrls.add(screenshotUrl);
                    }

                    result.setSuccess(false);
                    result.setErrorMessage(e.getMessage());
                    result.setFailedStep(i + 1);
                    break;
                }
            }

            // If all steps passed

                result.setSuccess(true);
                executionLogs.add("All steps completed successfully");

                // Capture final screenshot
                String screenshotUrl = captureScreenshot(driver, executionId, "final-success");
                if (screenshotUrl != null) {
                    screenshotUrls.add(screenshotUrl);
                }


        } catch (Exception e) {
            log.error("Execution failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage("Execution error: " + e.getMessage());
            executionLogs.add("EXECUTION ERROR: " + e.getMessage());

            if (driver != null) {
                String screenshotUrl = captureScreenshot(driver, executionId, "execution-error");
                if (screenshotUrl != null) {
                    screenshotUrls.add(screenshotUrl);
                }
            }

        } finally {
            result.setEndTime(System.currentTimeMillis());
            result.setDurationMs((int) (result.getEndTime() - result.getStartTime()));
            result.setScreenshotUrls(screenshotUrls);

            // Save execution logs
            String logContent = String.join("\n", executionLogs);
            try {
                var logMetadata = fileStorageService.saveLog(executionId, logContent, "execution");
                result.setLogUrl(logMetadata.getPath());
            } catch (Exception e) {
                log.error("Failed to save execution log: {}", e.getMessage());
            }

            // Quit driver
            webDriverFactory.quitDriver(driver);

            log.info("Execution completed. Success: {}, Duration: {}ms",
                    result.isSuccess(), result.getDurationMs());
        }

        return result;
    }

    private void executeStep(WebDriver driver, TestStep step, String executionId, int stepNumber) {
        String action = step.getAction().toLowerCase();

        switch (action) {
            case "navigate":
                driver.get(step.getValue());
                log.debug("Navigated to: {}", step.getValue());
                break;

            case "click":
                WebElement clickElement = findElement(driver, step.getLocator(), step.getTimeout());
                clickElement.click();
                log.debug("Clicked element: {}", step.getLocator());
                break;

            case "sendkeys":
                WebElement inputElement = findElement(driver, step.getLocator(), step.getTimeout());
                inputElement.clear();
                inputElement.sendKeys(step.getValue());
                log.debug("Sent keys to element: {}", step.getLocator());
                break;

            case "asserttext":
                WebElement textElement = findElement(driver, step.getLocator(), step.getTimeout());
                String actualText = textElement.getText();
                if (!actualText.contains(step.getValue())) {
                    throw new AssertionError(
                            String.format("Expected text '%s' but found '%s'", step.getValue(), actualText));
                }
                log.debug("Text assertion passed: {}", step.getValue());
                break;

            case "asserttitle":
                String actualTitle = driver.getTitle();
                if (!actualTitle.contains(step.getValue())) {
                    throw new AssertionError(
                            String.format("Expected title '%s' but found '%s'", step.getValue(), actualTitle));
                }
                log.debug("Title assertion passed: {}", step.getValue());
                break;

            case "wait":
                int seconds = Integer.parseInt(step.getValue());
                try {
                    Thread.sleep(seconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.debug("Waited for {} seconds", seconds);
                break;

            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }
    }

    private WebElement findElement(WebDriver driver, String locator, Integer timeout) {
        int waitTime = timeout != null ? timeout : 10;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitTime));

        By by = parseLocator(locator);
        return wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    private By parseLocator(String locator) {
        if (locator.startsWith("id=")) {
            return By.id(locator.substring(3));
        } else if (locator.startsWith("name=")) {
            return By.name(locator.substring(5));
        } else if (locator.startsWith("css=")) {
            return By.cssSelector(locator.substring(4));
        } else if (locator.startsWith("xpath=")) {
            return By.xpath(locator.substring(6));
        } else if (locator.startsWith("class=")) {
            return By.className(locator.substring(6));
        } else {
            // Default to CSS selector
            return By.cssSelector(locator);
        }
    }

    private String captureScreenshot(WebDriver driver, String executionId, String stepName) {
        try {
            byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            var metadata = fileStorageService.saveScreenshot(executionId, screenshot, stepName);
            log.debug("Screenshot saved: {}", metadata.getFilename());
            return metadata.getPath();
        } catch (Exception e) {
            log.error("Failed to capture screenshot: {}", e.getMessage());
            return null;
        }
    }

    // Inner class for result
    @lombok.Data
    public static class ExecutionResult {
        private String executionId;
        private String testName;
        private boolean success;
        private String errorMessage;
        private Integer failedStep;
        private Long startTime;
        private Long endTime;
        private Integer durationMs;
        private List<String> screenshotUrls;
        private String logUrl;
    }
}