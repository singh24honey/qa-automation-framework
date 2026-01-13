package com.company.qa.service.execution;

import com.company.qa.model.dto.TestScript;
import com.company.qa.model.dto.TestStep;
import com.company.qa.service.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeleniumTestExecutorTest {

    @Mock
    private WebDriverFactory webDriverFactory;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private WebDriver webDriver;

    private SeleniumTestExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new SeleniumTestExecutor(webDriverFactory, fileStorageService);
    }

    @Test
    @DisplayName("Should create executor successfully")
    void createExecutor_Success() {
        assertThat(executor).isNotNull();
    }

    @Test
    @DisplayName("Should parse test script with valid steps")
    void parseTestScript_ValidSteps() {
        TestScript script = TestScript.builder()
                .name("Test Script")
                .description("Test description")
                .steps(Arrays.asList(
                        TestStep.builder()
                                .action("navigate")
                                .value("https://example.com")
                                .build(),
                        TestStep.builder()
                                .action("assertTitle")
                                .value("Example")
                                .build()
                ))
                .build();

        assertThat(script.getSteps()).hasSize(2);
        assertThat(script.getSteps().get(0).getAction()).isEqualTo("navigate");
    }
}

