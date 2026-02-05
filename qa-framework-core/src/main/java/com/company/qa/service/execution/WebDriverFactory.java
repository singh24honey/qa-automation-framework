package com.company.qa.service.execution;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

@Component
@Slf4j
public class WebDriverFactory {

    @Value("${selenium.grid-url}")
    private String gridUrl;

    @Value("${selenium.implicit-wait:10}")
    private int implicitWait;

    @Value("${selenium.page-load-timeout:30}")
    private int pageLoadTimeout;

    public WebDriver createDriver(String browser, boolean headless) {
        log.info("Creating WebDriver for browser: {}, headless: {}", browser, headless);

        try {
            WebDriver driver;

            switch (browser.toUpperCase()) {
                case "CHROME":
                    driver = createChromeDriver(headless);
                    break;

                case "FIREFOX":
                    driver = createFirefoxDriver(headless);
                    break;

                default:
                    log.warn("Unknown browser: {}, defaulting to Chrome", browser);
                    driver = createChromeDriver(headless);
            }

            // Set timeouts
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitWait));
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pageLoadTimeout));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

            log.info("WebDriver created successfully: {}", driver);
            return driver;

        } catch (Exception e) {
            log.error("Failed to create WebDriver: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create WebDriver", e);
        }
    }

    private WebDriver createChromeDriver(boolean headless) throws MalformedURLException {
        ChromeOptions options = new ChromeOptions();

        if (headless) {
            options.addArguments("--headless=new");
        }

        options.addArguments(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-extensions",
                "--disable-infobars",
                "--window-size=1920,1080",
                // Add these critical options:
                "--disable-software-rasterizer",
                "--disable-dev-tools",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-background-networking",
                "--disable-background-timer-throttling",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-features=TranslateUI",
                "--disable-ipc-flooding-protection",
                "--disable-hang-monitor",
                "--disable-popup-blocking",
                "--disable-prompt-on-repost",
                "--metrics-recording-only",
                "--safebrowsing-disable-auto-update",
                "--password-store=basic",
                "--use-mock-keychain",
                "--remote-debugging-port=9222"  // Important for headless Chrome
        );




        log.debug("Chrome options: {}", options.asMap());

        return new RemoteWebDriver(new URL(gridUrl), options);
    }

    private WebDriver createFirefoxDriver(boolean headless) throws MalformedURLException {
        FirefoxOptions options = new FirefoxOptions();

        if (headless) {
            options.addArguments("--headless");
        }

        options.addArguments("--width=1920");
        options.addArguments("--height=1080");

        log.debug("Firefox options: {}", options.asMap());

        return new RemoteWebDriver(new URL(gridUrl), options);
    }

    public void quitDriver(WebDriver driver) {
        if (driver != null) {
            try {
                log.debug("Quitting WebDriver: {}", driver);
                driver.quit();
                log.info("WebDriver quit successfully");
            } catch (Exception e) {
                log.error("Error quitting WebDriver: {}", e.getMessage(), e);
            }
        }
    }
}